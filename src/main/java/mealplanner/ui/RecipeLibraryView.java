package mealplanner.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.CategoryGroup;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.InvalidRecipeException;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

/**
 * F08 + F09 GUI (Recipe Library menu)
 */
public class RecipeLibraryView extends BorderPane implements EventListener {

    private static final String HEART_ON = "♥ Favourite";
    private static final String HEART_OFF = "♡ Favourite";
    private static final String DANGER = "#F2545B"; // Theme.DANGER, for warnings and problems

    /** Groups tried, in this order, when a category is typed without a group ("Pasta" is a Dish Type). */
    private static final List<CategoryGroup> LOOKUP_ORDER = List.of(CategoryGroup.DISH_TYPE,
            CategoryGroup.MAIN_INGREDIENT, CategoryGroup.FLAVOR, CategoryGroup.COOKING_METHOD);

    private final AppFacade facade;
    private final TextField searchField = new TextField();
    private final CheckBox favouritesOnly = new CheckBox("♥ Favourites only");
    private final ListView<Recipe> list = new ListView<>();

    private final TextField nameField = new TextField();
    private final TextField categoriesField = new TextField();
    private final TextField minutesField = new TextField();
    private final TextField servingsField = new TextField();
    private final VBox ingredientRows = new VBox(8);
    private final List<IngredientRow> rows = new ArrayList<>();
    private final TextArea stepsArea = new TextArea();
    private final Label formTitle = new Label("New recipe");
    private final Label warningsLabel = new Label();
    private final Label nutritionLabel = new Label();
    private final Label problemsLabel = new Label();
    private final Button saveButton = new Button("Save");
    private final Button removeButton = new Button("Remove");
    private final Button heartButton = new Button(HEART_OFF);
    private final Label status = new Label();

    /** The recipe open in the form; null while adding a new one. */
    private Recipe current;

    public RecipeLibraryView(AppFacade facade) {
        this.facade = facade;

        // ---- left: search, favourites filter, list, New recipe
        searchField.setPromptText("Search recipes");
        searchField.textProperty().addListener((o, a, b) -> refreshList());
        favouritesOnly.setOnAction(e -> onShowFavourites());
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Recipe r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null
                        : (r.isFavourite() ? "♥ " : "") + r.getName() + (r.getWarnings().isEmpty() ? "" : "  ⚠"));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> { if (r != null) open(r); });
        Button newButton = new Button("New recipe");
        newButton.setMaxWidth(Double.MAX_VALUE);
        newButton.setOnAction(e -> open(null));
        Label listTitle = new Label("Your recipes");
        listTitle.getStyleClass().add("card-title");
        VBox left = new VBox(10, listTitle, searchField, favouritesOnly, list, newButton);
        left.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setPrefWidth(290);
        left.setMinWidth(240);
        BorderPane.setMargin(left, new Insets(0, 16, 0, 0));

        // ---- right: the recipe form, in cards
        nameField.setPromptText("Recipe name");
        categoriesField.setPromptText("e.g. Pasta, Savory, Flavor:Spicy");
        minutesField.setPromptText("minutes");
        minutesField.setPrefWidth(110);
        servingsField.setPromptText("servings");
        servingsField.setPrefWidth(110);
        stepsArea.setPromptText("One cooking step per line");
        stepsArea.setPrefRowCount(6);
        stepsArea.setWrapText(true);
        stepsArea.setStyle("-fx-control-inner-background: #252932; -fx-text-fill: #F1F3F5;"
                + " -fx-prompt-text-fill: #7A808C; -fx-highlight-fill: #3478F6;"
                + " -fx-background-color: #343943, #252932; -fx-background-insets: 0, 1;"
                + " -fx-background-radius: 8, 7; -fx-font-size: 14px; -fx-padding: 4;");
        warningsLabel.setWrapText(true);
        nutritionLabel.setWrapText(true);
        problemsLabel.setWrapText(true);
        problemsLabel.setStyle("-fx-text-fill: " + DANGER + ";");
        formTitle.getStyleClass().add("section-title");
        status.getStyleClass().add("description");

        Button addRow = new Button("+ Ingredient");
        addRow.setOnAction(e -> addRow(null));
        saveButton.getStyleClass().add("primary");
        saveButton.setOnAction(e -> onSave());
        removeButton.setOnAction(e -> { if (current != null) onRemove(current); });
        heartButton.setOnAction(e -> { if (current != null) onToggleFavourite(current); });

        HBox nameRow = new HBox(10, field("Name", nameField), heartButton);
        nameRow.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(nameRow.getChildren().get(0), Priority.ALWAYS);
        HBox timeRow = new HBox(12, field("Cooking time (minutes)", minutesField), field("Servings", servingsField));
        Label categoryHint = new Label("Separate with commas. Use Group:Name to pick the group "
                + "(Dish Type, Main Ingredient, Flavor, Cooking Method, Other).");
        categoryHint.getStyleClass().add("description");
        categoryHint.setWrapText(true);
        VBox detailsCard = card("Recipe", nameRow, field("Categories", categoriesField), categoryHint, timeRow);

        Label ingredientHint = new Label("Amount and unit are required. Ingredient names come from the "
                + "ingredient list (e.g. pasta, tomato, chicken breast).");
        ingredientHint.getStyleClass().add("description");
        ingredientHint.setWrapText(true);
        VBox ingredientsCard = card("Ingredients", ingredientHint, ingredientRows, addRow);

        VBox stepsCard = card("Cooking instructions", stepsArea);

        VBox infoCard = card("Warnings and nutrition", warningsLabel, nutritionLabel);

        HBox buttons = new HBox(10, saveButton, removeButton);
        VBox form = new VBox(14, formTitle, detailsCard, ingredientsCard, stepsCard, infoCard, problemsLabel,
                buttons, status);
        form.setPadding(new Insets(0, 8, 8, 0));
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);

        setLeft(left);
        setCenter(scroll);
        refreshList();
        open(null);
    }

    /**
     * SD03: onSave() -> saveRecipe(r). Runs in the background because nutrition may call USDA.
     * On problems the ingredient rows are flagged and nothing is saved (UC08 4a).
     */
    public void onSave() {
        Recipe recipe;
        try {
            recipe = readForm();
        } catch (AppException ex) {
            showError(ex.getMessage());
            return;
        }
        clearFlags();
        Task<Recipe> task = new Task<>() {
            @Override
            protected Recipe call() {
                return facade.saveRecipe(recipe);
            }
        };
        saveButton.setDisable(true);
        status.setText("Saving and working out the nutrition...");
        task.setOnSucceeded(e -> {
            saveButton.setDisable(false);
            status.setText("Saved " + task.getValue().getName() + ".");
            open(task.getValue());
            list.getItems().stream().filter(r -> r.getId() == task.getValue().getId()).findFirst()
                    .ifPresent(r -> list.getSelectionModel().select(r));
        });
        task.setOnFailed(e -> {
            saveButton.setDisable(false);
            status.setText("Nothing was saved.");
            if (task.getException() instanceof InvalidRecipeException bad) {
                flag(bad);
            } else {
                showError(task.getException().getMessage());
            }
        });
        Thread thread = new Thread(task, "save-recipe");
        thread.setDaemon(true);
        thread.start();
    }

    /** UC08 1a: archives the recipe; meals already in the calendar keep it. */
    public void onRemove(Recipe recipe) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + recipe.getName() + " from the library? Meals already planned keep it.",
                ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            facade.removeRecipe(recipe.getId());
            status.setText("Removed " + recipe.getName() + ".");
            open(null);
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** UC09: the heart changes at once; if saving fails it goes back and an error is shown (2a). */
    public void onToggleFavourite(Recipe recipe) {
        boolean before = recipe.isFavourite();
        heartButton.setText(before ? HEART_OFF : HEART_ON);
        try {
            boolean now = facade.toggleFavourite(recipe.getId());
            recipe.setFavourite(now);
            heartButton.setText(now ? HEART_ON : HEART_OFF);
            status.setText(now ? recipe.getName() + " added to favourites." : recipe.getName() + " removed from favourites.");
        } catch (AppException ex) {
            heartButton.setText(before ? HEART_ON : HEART_OFF);
            showError("Could not save the favourite: " + ex.getMessage());
        }
    }

    /** Favourites filter: the list shows getFavourites() only. */
    public void onShowFavourites() {
        refreshList();
    }

    /** Observer: LibraryChanged (save, remove, favourite, import, new warnings) redraws the list. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::refreshList);
    }

    private void refreshList() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        List<Recipe> recipes = favouritesOnly.isSelected() ? facade.getFavourites() : facade.searchLibrary("");
        Long selected = current == null ? null : current.getId();
        list.getItems().setAll(recipes.stream().filter(r -> r.getName().toLowerCase().contains(query)).toList());
        if (selected != null) {
            list.getItems().stream().filter(r -> r.getId() == selected).findFirst().ifPresent(r -> {
                current = r;
                heartButton.setText(r.isFavourite() ? HEART_ON : HEART_OFF);
                showWarnings(r);
            });
        }
    }

    /** Fills the form with the recipe, or empties it for a new one. */
    private void open(Recipe recipe) {
        current = recipe;
        clearFlags();
        rows.clear();
        ingredientRows.getChildren().clear();
        formTitle.setText(recipe == null ? "New recipe" : recipe.getName());
        nameField.setText(recipe == null ? "" : recipe.getName());
        categoriesField.setText(recipe == null ? "" : String.join(", ",
                recipe.getCategories().stream().map(RecipeLibraryView::categoryText).toList()));
        minutesField.setText(recipe == null || recipe.getCookMinutes() == 0 ? "" : String.valueOf(recipe.getCookMinutes()));
        servingsField.setText(recipe == null ? "1" : String.valueOf(recipe.getServings()));
        stepsArea.setText(recipe == null ? "" : String.join("\n", recipe.getInstructions()));
        if (recipe == null) {
            addRow(null);
            list.getSelectionModel().clearSelection();
        } else {
            recipe.ingredients().forEach(this::addRow);
        }
        showWarnings(recipe);
        nutritionLabel.setText(recipe == null ? "Nutrition per serving is worked out when you press Save."
                : nutritionText(recipe));
        heartButton.setText(recipe != null && recipe.isFavourite() ? HEART_ON : HEART_OFF);
        heartButton.setDisable(recipe == null);
        removeButton.setDisable(recipe == null);
    }

    private Recipe readForm() {
        int servings = parseInt(servingsField.getText(), 1, "Servings");
        Recipe recipe = new Recipe(current == null ? 0 : current.getId(), nameField.getText().trim(), servings);
        recipe.setCategories(parseCategories(categoriesField.getText()));
        recipe.setCookMinutes(parseInt(minutesField.getText(), 0, "Cooking time"));
        for (IngredientRow row : rows) {
            if (row.isBlank()) continue;
            recipe.addIngredient(row.read());
        }
        Arrays.stream(stepsArea.getText().split("\n")).map(String::trim).filter(s -> !s.isEmpty())
                .forEach(recipe.getInstructions()::add);
        return recipe;
    }

    /** Marks the rows named in the exception (UC08 4a: "flags the ingredient rows"). */
    private void flag(InvalidRecipeException bad) {
        List<IngredientRow> filled = rows.stream().filter(r -> !r.isBlank()).toList();
        for (Map.Entry<Integer, String> e : bad.getRowProblems().entrySet()) {
            if (e.getKey() < filled.size()) filled.get(e.getKey()).flag(e.getValue());
        }
        problemsLabel.setText("Nothing was saved:\n- " + String.join("\n- ", bad.getProblems()));
    }

    private void clearFlags() {
        rows.forEach(IngredientRow::unflag);
        problemsLabel.setText("");
    }

    private void addRow(RecipeIngredient line) {
        IngredientRow row = new IngredientRow(line);
        rows.add(row);
        ingredientRows.getChildren().add(row.box);
    }

    /** UC08 step 5 / 5a. */
    private static String nutritionText(Recipe r) {
        return r.getNutritionPerServing() == null
                ? "Nutrition per serving: not calculated yet (press Save to calculate it)"
                : "Nutrition per serving: " + r.getNutritionPerServing().shortLine();
    }

    private void showWarnings(Recipe r) {
        boolean none = r == null || r.getWarnings().isEmpty();
        warningsLabel.setText(r == null ? "" : none ? "No allergy or diet warnings."
                : "⚠ " + String.join("; ", r.getWarnings()));
        warningsLabel.setStyle(none ? "" : "-fx-text-fill: " + DANGER + ";");
    }

    /**
     * "Pasta, Savory, Flavor:Spicy, Yogurt Bowl" -> categories. A name without a group goes in the first group
     * that suggests it, or in Other if no group does (the user's own category). Same rules as "recipe add".
     */
    private static List<RecipeCategory> parseCategories(String text) {
        List<RecipeCategory> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String part : text.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.indexOf(':');
            if (colon >= 0) {
                result.add(new RecipeCategory(group(item.substring(0, colon).trim()), item.substring(colon + 1).trim()));
            } else {
                CategoryGroup g = LOOKUP_ORDER.stream().filter(x -> x.find(item).isPresent()).findFirst()
                        .orElse(CategoryGroup.OTHER);
                result.add(new RecipeCategory(g, item));
            }
        }
        return result;
    }

    /** "Flavor", "dish type" or "DISH_TYPE" -> the group. */
    private static CategoryGroup group(String typed) {
        for (CategoryGroup g : CategoryGroup.values()) {
            if (g.getLabel().equalsIgnoreCase(typed) || g.name().equalsIgnoreCase(typed.replace(' ', '_'))) {
                return g;
            }
        }
        throw new InvalidInputException("Unknown category group '" + typed + "'. Use one of: Dish Type, "
                + "Main Ingredient, Flavor, Cooking Method, Other");
    }

    /** How a category is shown in the form: just the name, unless typing the name alone would pick another group. */
    private static String categoryText(RecipeCategory c) {
        CategoryGroup guessed = LOOKUP_ORDER.stream().filter(g -> g.find(c.getName()).isPresent()).findFirst()
                .orElse(CategoryGroup.OTHER);
        return guessed == c.getGroup() ? c.getName() : c.getGroup().getLabel() + ":" + c.getName();
    }

    private static int parseInt(String text, int fallback, String what) {
        if (text == null || text.isBlank()) return fallback;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(what + " must be a whole number");
        }
    }

    private static VBox card(String title, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");
        VBox card = new VBox(10, heading);
        card.getChildren().addAll(content);
        card.getStyleClass().add("card");
        return card;
    }

    private static VBox field(String name, Node input) {
        Label label = new Label(name);
        label.getStyleClass().add("small-label");
        return new VBox(6, label, input);
    }

    private void showError(String text) {
        new Alert(Alert.AlertType.ERROR, text).showAndWait();
    }

    /** One editable ingredient line: name, amount, unit and a delete button. */
    private final class IngredientRow {
        final TextField name = new TextField();
        final TextField amount = new TextField();
        final ComboBox<Unit> unit = new ComboBox<>();
        final HBox box;

        IngredientRow(RecipeIngredient line) {
            name.setPromptText("ingredient");
            amount.setPromptText("amount");
            amount.setPrefWidth(90);
            unit.getItems().setAll(Unit.values());
            unit.setPromptText("unit");
            unit.setPrefWidth(110);
            if (line != null) {
                name.setText(line.getIngredient().getName());
                if (line.getQuantity() != null) {
                    double a = line.getQuantity().getAmount();
                    amount.setText(a == Math.floor(a) ? String.valueOf((long) a) : String.valueOf(a));
                    unit.setValue(line.getQuantity().getUnit());
                }
            }
            Button delete = new Button("✕");
            delete.setTooltip(new Tooltip("Remove this row"));
            box = new HBox(8, name, amount, unit, delete);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(3));
            HBox.setHgrow(name, Priority.ALWAYS);
            delete.setOnAction(e -> {
                rows.remove(this);
                ingredientRows.getChildren().remove(box);
            });
        }

        boolean isBlank() {
            return name.getText().isBlank() && amount.getText().isBlank() && unit.getValue() == null;
        }

        /** A missing or unreadable amount, or no unit, gives a line without a quantity; save() flags it. */
        RecipeIngredient read() {
            Quantity q = null;
            try {
                double a = Double.parseDouble(amount.getText().trim());
                if (unit.getValue() != null && a >= 0) q = new Quantity(a, unit.getValue());
            } catch (NumberFormatException e) {
                // left without a quantity
            }
            return RecipeIngredient.unchecked(name.getText(), q);
        }

        /** A red frame around the row, and the problem as its tooltip. */
        void flag(String problem) {
            box.setBorder(new Border(new BorderStroke(Theme.DANGER, BorderStrokeStyle.SOLID,
                    new CornerRadii(8), new BorderWidths(2))));
            Tooltip.install(box, new Tooltip(problem));
        }

        void unflag() {
            box.setBorder(Border.EMPTY);
        }
    }
}
