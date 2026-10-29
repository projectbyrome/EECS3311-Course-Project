package mealplanner.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;
import mealplanner.model.FinishResult;

/**
 * F02/F03 GUI (Shopping menu): checklists on the left, the selected checklist's items with tick boxes
 * on the right, plus Finish Shopping. "Generate from meals" (F03) comes in Step 9.
 * Rough draft: the layout is built in code
 */
public class ChecklistView extends BorderPane implements EventListener {

    private final AppFacade facade;
    private final ListView<Checklist> lists = new ListView<>();
    private final VBox itemsBox = new VBox(6);
    private final Label title = new Label();
    private final TextField itemName = new TextField();
    private final TextField itemAmount = new TextField();
    private final ComboBox<Unit> itemUnit = new ComboBox<>();

    public ChecklistView(AppFacade facade) {
        this.facade = facade;

        Button newList = new Button("New checklist");
        newList.setOnAction(e -> onNewChecklist());
        lists.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> showItems(sel));
        VBox left = new VBox(8, newList, lists);
        left.setPadding(new Insets(8));
        left.setPrefWidth(240);

        itemName.setPromptText("Item, e.g. eggs");
        itemAmount.setPromptText("Amount");
        itemAmount.setPrefWidth(70);
        itemUnit.getItems().setAll(Unit.values());
        itemUnit.setValue(Unit.PIECE);
        Button add = new Button("Add item");
        add.setOnAction(e -> onAddItem());
        Button finish = new Button("Finish Shopping");
        finish.setOnAction(e -> onFinishShopping());
        HBox addRow = new HBox(6, itemName, itemAmount, itemUnit, add);
        VBox right = new VBox(10, title, itemsBox, addRow, finish);
        right.setPadding(new Insets(8));

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

    /** Observer: ChecklistChanged and PantryChanged (pantry flags) redraw the screen. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::reload);
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
            HBox row = new HBox(8, box);
            if (item.isAlreadyInPantry()) {
                Label flag = new Label("already in pantry"
                        + (item.getPantryQuantity() != null ? ": " + item.getPantryQuantity() : ""));
                flag.setStyle("-fx-text-fill: #8a6d00;");
                row.getChildren().add(flag);
            }
            itemsBox.getChildren().add(row);
        }
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