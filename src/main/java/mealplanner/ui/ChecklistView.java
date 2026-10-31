package mealplanner.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.Unit;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;
import mealplanner.model.DraftItem;
import mealplanner.model.FinishResult;
import mealplanner.model.RecipeSelection;
import mealplanner.model.ShoppingDraft;

/**
 * F02/F03 GUI (Shopping menu): checklists on the left, the selected checklist's items with tick boxes
 * on the right, plus Finish Shopping and Generate from meals.
 * Rough draft: the layout is built in code; it can move to an .fxml file later.
 */
public class ChecklistView extends BorderPane implements EventListener {

    private static final String NEW_CHECKLIST = "(new checklist)";

    private final AppFacade facade;
    private final ListView<Checklist> lists = new ListView<>();
    private final VBox itemsBox = new VBox(6);
    private final Label title = new Label();
    private final TextField itemName = new TextField();
    private final TextField itemAmount = new TextField();
    private final ComboBox<Unit> itemUnit = new ComboBox<>();
    private final Button generateButton = new Button("Generate from meals");

    public ChecklistView(AppFacade facade) {
        this.facade = facade;

        Button newList = new Button("New checklist");
        newList.setMaxWidth(Double.MAX_VALUE);
        Button deleteList = new Button("Delete checklist");
        deleteList.setMaxWidth(Double.MAX_VALUE);
        deleteList.setOnAction(e -> onDeleteChecklist());
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("primary");
        newList.setOnAction(e -> onNewChecklist());
        generateButton.setOnAction(e -> onGenerateFromMeals());
        lists.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> showItems(sel));
        VBox.setVgrow(lists, Priority.ALWAYS);
        VBox left = new VBox(8, generateButton, newList, lists, deleteList);
        left.setPadding(new Insets(0, 16, 0, 0));
        left.setPrefWidth(250);

        itemName.setPromptText("Item, e.g. eggs");
        itemAmount.setPromptText("Amount");
        itemAmount.setPrefWidth(70);
        itemUnit.getItems().setAll(Unit.values());
        itemUnit.setValue(Unit.PIECE);
        Button add = new Button("Add item");
        add.setOnAction(e -> onAddItem());
        Button finish = new Button("Finish Shopping");
        finish.getStyleClass().add("primary");
        title.getStyleClass().add("section-title");
        finish.setOnAction(e -> onFinishShopping());
        HBox addRow = new HBox(6, itemName, itemAmount, itemUnit, add);
        VBox right = new VBox(12, title, itemsBox, addRow, finish);
        right.getStyleClass().add("card");

        setLeft(left);
        setCenter(right);
        reload();
    }

    public void onNewChecklist() {
        TextInputDialog dialog = new TextInputDialog("Weekly shop");
        dialog.setHeaderText("Name of the new checklist");
        dialog.showAndWait().ifPresent(name -> run(() -> facade.createChecklist(name)));
    }

    public void onAddItem() {
        Checklist selected = lists.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Choose or create a checklist first.");
            return;
        }
        run(() -> {
            double amount;
            try {
                amount = Double.parseDouble(itemAmount.getText().trim());
            } catch (NumberFormatException e) {
                throw new InvalidInputException("Amount must be a number");
            }
            facade.addChecklistItem(selected.getId(), itemName.getText(), new Quantity(amount, itemUnit.getValue()));
            itemName.clear();
            itemAmount.clear();
        });
    }

    /** Asks first, then deletes the selected checklist and its items. */
    public void onDeleteChecklist() {
        Checklist selected = lists.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Choose a checklist first.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\" and its " + selected.getItems().size() + " item(s)?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete checklist");
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
            run(() -> facade.deleteChecklist(selected.getId()));
        }
    }

    public void onRemoveItem(ChecklistItem item) {
        run(() -> facade.removeChecklistItem(item.getId()));
    }

    public void onToggle(ChecklistItem item) {
        run(() -> facade.setItemChecked(item.getId(), !item.isChecked()));
    }

    public void onFinishShopping() {
        Checklist selected = lists.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FinishResult result = facade.finishShopping(selected.getId());
            new Alert(Alert.AlertType.INFORMATION, result.toString()).showAndWait();
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** F03: pick recipes and servings, then draft the list in the background (it may call Claude). */
    public void onGenerateFromMeals() {
        List<RecipeSelection> selections = askForRecipes();
        if (selections == null) return; // cancelled
        if (selections.isEmpty()) {
            showError("Set the servings above 0 for at least one recipe.");
            return;
        }
        generateButton.setDisable(true);
        generateButton.setText("Generating...");
        Task<ShoppingDraft> task = new Task<>() {
            @Override
            protected ShoppingDraft call() {
                return facade.draftShoppingList(selections);
            }
        };
        task.setOnSucceeded(e -> {
            resetGenerateButton();
            onApproveDraft(task.getValue());
        });
        task.setOnFailed(e -> {
            resetGenerateButton();
            showError(task.getException().getMessage());
        });
        Thread worker = new Thread(task, "shopping-list");
        worker.setDaemon(true);
        worker.start();
    }

    /** Shows the draft grouped by store section; if the user approves, asks which checklist to add it to. */
    public void onApproveDraft(ShoppingDraft draft) {
        StringBuilder text = new StringBuilder();
        draft.warnings.forEach(w -> text.append(w).append("\n\n"));
        if (draft.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, text.toString().trim()).showAndWait();
            return;
        }
        for (Map.Entry<String, List<DraftItem>> group : draft.bySection().entrySet()) {
            text.append(group.getKey()).append('\n');
            group.getValue().forEach(i -> text.append("   ").append(i).append('\n'));
        }
        Alert review = new Alert(Alert.AlertType.CONFIRMATION, text.toString().trim(), ButtonType.OK, ButtonType.CANCEL);
        review.setHeaderText("Add this shopping list to a checklist?");
        if (review.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return; // UC03 5a: rejected, nothing is added
        }
        List<java.lang.Object> choices = new ArrayList<>();
        choices.add(NEW_CHECKLIST);
        choices.addAll(lists.getItems());
        ChoiceDialog<java.lang.Object> pick = new ChoiceDialog<>(NEW_CHECKLIST, choices);
        pick.setHeaderText("Add the items to which checklist?");
        pick.showAndWait().ifPresent(choice -> run(() -> {
            Long id = choice instanceof Checklist c ? c.getId() : null;
            facade.approveShoppingDraft(draft, id);
        }));
    }

    /** Observer: ChecklistChanged and PantryChanged (pantry flags) redraw the screen. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::reload);
    }

    private List<RecipeSelection> askForRecipes() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Generate from meals");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Recipe"), new Label("Servings (0 = skip)"));
        List<Recipe> recipes = facade.searchLibrary("");
        List<Spinner<Double>> spinners = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            Spinner<Double> s = new Spinner<>(0, 50, 0, 1);
            s.setEditable(true);
            spinners.add(s);
            grid.addRow(i + 1, new Label(recipes.get(i).getName()), s);
        }
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return null; // cancelled
        }
        List<RecipeSelection> selections = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            double servings = readServings(spinners.get(i));
            if (servings > 0) selections.add(new RecipeSelection(recipes.get(i).getId(), servings));
        }
        return selections;
    }

    /** Reads what was typed in the box, even if Enter was not pressed (a typed value is not saved until then). */
    private static double readServings(Spinner<Double> spinner) {
        try {
            return Double.parseDouble(spinner.getEditor().getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return spinner.getValue();
        }
    }

    private void reload() {
        Checklist selected = lists.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1 : selected.getId();
        lists.getItems().setAll(facade.getChecklists());
        lists.getItems().stream().filter(c -> c.getId() == selectedId).findFirst()
                .ifPresentOrElse(c -> lists.getSelectionModel().select(c),
                        () -> lists.getSelectionModel().selectFirst());
        showItems(lists.getSelectionModel().getSelectedItem());
    }

    private void showItems(Checklist checklist) {
        itemsBox.getChildren().clear();
        if (checklist == null) {
            title.setText("No checklist selected");
            return;
        }
        title.setText(checklist.getName());
        for (ChecklistItem item : checklist.getItems()) {
            CheckBox box = new CheckBox(item.getName() + "  " + item.getQuantity());
            box.setSelected(item.isChecked());
            box.setOnAction(e -> onToggle(item));
            MenuItem remove = new MenuItem("Remove item");
            remove.setOnAction(e -> onRemoveItem(item));
            box.setContextMenu(new ContextMenu(remove)); // right-click an item to remove it
            HBox row = new HBox(8, box);
            if (item.isAlreadyInPantry()) {
                Label flag = new Label("already in pantry"
                        + (item.getPantryQuantity() != null ? ": " + item.getPantryQuantity() : ""));
                flag.getStyleClass().add("pantry-flag");
                row.getChildren().add(flag);
            }
            itemsBox.getChildren().add(row);
        }
    }

    private void resetGenerateButton() {
        generateButton.setDisable(false);
        generateButton.setText("Generate from meals");
    }

    private void run(Runnable action) {
        try {
            action.run(); // ChecklistChanged then redraws the screen
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}
