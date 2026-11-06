package mealplanner.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.Unit;
import mealplanner.model.IngredientPortion;
import mealplanner.model.NutritionSelection;
import mealplanner.model.RecipePortion;

/**
 * F05 GUI (Macro Generator): pick recipes (with servings) and pantry ingredients (with amounts),
 * then Calculate. The calculation may call USDA and Claude, so it runs in a background Task.
 * Rough draft: the layout is built in code; it can move to an .fxml file later.
 */
public class MacroGeneratorView extends BorderPane {

    private final AppFacade facade;
    private final ListView<NutritionSelection> selections = new ListView<>();
    private final ComboBox<Recipe> recipeBox = new ComboBox<>();
    private final Spinner<Double> servings = new Spinner<>(0.25, 20, 1, 0.25);
    private final ComboBox<String> ingredientBox = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final ComboBox<Unit> unitBox = new ComboBox<>();
    private final Label result = new Label("Press Calculate to see calories, carbohydrates, protein, fat, sugar and vitamins.");
    private final Button calculate = new Button("Calculate");

    public MacroGeneratorView(AppFacade facade) {
        this.facade = facade;

        recipeBox.setPromptText("Recipe");
        recipeBox.setOnShowing(e -> reloadChoices()); // recipes saved after start-up show up too
        servings.setEditable(true);
        servings.setPrefWidth(90);
        Button addRecipe = new Button("Add");
        addRecipe.setOnAction(e -> onAddSelection(true));

        ingredientBox.setEditable(true); // pantry items are offered, any known ingredient can be typed
        ingredientBox.setPromptText("Ingredient");
        ingredientBox.setOnShowing(e -> reloadChoices());
        amountField.setPromptText("Amount");
        amountField.setPrefWidth(80);
        amountField.setMinWidth(80);
        unitBox.getItems().setAll(Unit.values());
        unitBox.setValue(Unit.G);
        Button addIngredient = new Button("Add");
        addIngredient.setOnAction(e -> onAddSelection(false));
        addIngredient.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // keep "Add" readable, not "..."

        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            NutritionSelection sel = selections.getSelectionModel().getSelectedItem();
            if (sel != null) selections.getItems().remove(sel);
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> selections.getItems().clear());
        calculate.getStyleClass().add("primary");
        calculate.setOnAction(e -> onCalculate());

        Label pickTitle = new Label("What did you eat?");
        pickTitle.getStyleClass().add("section-title");
        Label recipeLabel = new Label("Recipe and servings");
        recipeLabel.getStyleClass().add("small-label");
        Label ingredientLabel = new Label("Ingredient and amount");
        ingredientLabel.getStyleClass().add("small-label");
        Label selectedLabel = new Label("Selected");
        selectedLabel.getStyleClass().add("small-label");
        VBox.setVgrow(selections, Priority.ALWAYS);
        VBox left = new VBox(10, pickTitle,
                recipeLabel, new HBox(6, recipeBox, servings, addRecipe),
                ingredientLabel, new HBox(6, ingredientBox, amountField, unitBox, addIngredient),
                selectedLabel, selections, new HBox(6, calculate, remove, clear));
        left.getStyleClass().add("card");

        Label resultTitle = new Label("Result");
        resultTitle.getStyleClass().add("section-title");
        result.setWrapText(true);
        ScrollPane scroll = new ScrollPane(result);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox right = new VBox(10, resultTitle, scroll);
        right.getStyleClass().add("card");

        HBox.setHgrow(right, Priority.ALWAYS);
        setCenter(new HBox(16, left, right));
        reloadChoices();
    }

    /** Adds the chosen recipe or ingredient to the list of selections. */
    public void onAddSelection(boolean recipe) {
        try {
            if (recipe) {
                Recipe r = recipeBox.getValue();
                if (r == null) throw new InvalidInputException("Choose a recipe first.");
                double count = readServings(servings);
                if (count <= 0) throw new InvalidInputException("Servings must be more than 0.");
                selections.getItems().add(new RecipePortion(r.getId(), count) {
                    @Override
                    public String describe() {
                        return r.getName() + " x" + count;
                    }
                });
            } else {
                String name = ingredientBox.getEditor().getText();
                if (name == null || name.isBlank()) throw new InvalidInputException("Enter an ingredient.");
                double amount;
                try {
                    amount = Double.parseDouble(amountField.getText().trim().replace(',', '.'));
                } catch (NumberFormatException ex) {
                    throw new InvalidInputException("Amount must be a number.");
                }
                Quantity quantity = new Quantity(amount, unitBox.getValue());
                // a name that isn't in the ingredient list (e.g. ketchup) is checked with Claude first
                NewIngredientDialog.ensureKnown(facade, name, this, known -> {
                    selections.getItems().add(new IngredientPortion(known.trim(), quantity));
                    ingredientBox.getEditor().clear();
                    amountField.clear();
                }, this::showError);
            }
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** SD05: generateMacros(selections) in a background task; errors (1a) are shown as a message. */
    public void onCalculate() {
        List<NutritionSelection> chosen = new ArrayList<>(selections.getItems());
        if (chosen.isEmpty()) {
            showError("Add at least one recipe or ingredient first.");
            return;
        }
        Task<NutritionSummary> task = new Task<>() {
            @Override
            protected NutritionSummary call() {
                return facade.generateMacros(chosen);
            }
        };
        calculate.setDisable(true);
        result.setText("Calculating...");
        task.setOnSucceeded(e -> {
            calculate.setDisable(false);
            result.setText(task.getValue().toString());
        });
        task.setOnFailed(e -> {
            calculate.setDisable(false);
            result.setText("");
            showError(task.getException().getMessage());
        });
        Thread thread = new Thread(task, "macro-generator");
        thread.setDaemon(true);
        thread.start();
    }

    private void reloadChoices() {
        Recipe chosenRecipe = recipeBox.getValue();
        recipeBox.getItems().setAll(facade.searchLibrary(""));
        recipeBox.setValue(chosenRecipe);
        String typed = ingredientBox.getEditor().getText();
        ingredientBox.getItems().setAll(facade.getPantry().stream()
                .map(PantryItem::getIngredient).map(i -> i.getName()).distinct().toList());
        ingredientBox.getEditor().setText(typed);
    }

    /** A typed number only reaches the Spinner after Enter, so read the text box itself. */
    private static double readServings(Spinner<Double> spinner) {
        try {
            return Double.parseDouble(spinner.getEditor().getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return spinner.getValue();
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}