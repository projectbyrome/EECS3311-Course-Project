package mealplanner.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

/**
 * F04 GUI (Pantry menu): the pantry as a table, a row to add an ingredient, and Edit / Remove buttons.
 * Listens for PantryChanged, so Finish Shopping and the end-of-day update also refresh it.
 */
public class PantryView extends BorderPane implements EventListener {

    private final AppFacade facade;
    private final TableView<PantryItem> table = new TableView<>();
    private final TextField nameField = new TextField();
    private final TextField amountField = new TextField();
    private final ComboBox<Unit> unitBox = new ComboBox<>();

    public PantryView(AppFacade facade) {
        this.facade = facade;

        TableColumn<PantryItem, String> name = new TableColumn<>("Ingredient");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIngredient().getName()));
        TableColumn<PantryItem, String> qty = new TableColumn<>("Quantity");
        qty.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuantity().toString()));
        TableColumn<PantryItem, String> added = new TableColumn<>("Added on");
        added.setCellValueFactory(new PropertyValueFactory<>("addedOn"));
        table.getColumns().add(name);
        table.getColumns().add(qty);
        table.getColumns().add(added);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        nameField.setPromptText("Ingredient, e.g. eggs");
        amountField.setPromptText("Amount");
        amountField.setPrefWidth(80);
        unitBox.getItems().setAll(Unit.values());
        unitBox.setValue(Unit.G);
        Button add = new Button("Add");
        add.setOnAction(e -> onAdd());
        Button edit = new Button("Edit quantity");
        edit.setOnAction(e -> {
            PantryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onEdit(sel);
        });
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            PantryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onRemove(sel);
        });

        HBox addRow = new HBox(6, nameField, amountField, unitBox, add);
        HBox editRow = new HBox(6, edit, remove);
        VBox bottom = new VBox(8, addRow, editRow);
        bottom.setPadding(new Insets(8));
        setCenter(table);
        setBottom(bottom);
        refresh();
    }

    /** SD02 from the GUI: addPantryItem(name, quantity). Unknown names and bad amounts show a message. */
    public void onAdd() {
        try {
            facade.addPantryItem(nameField.getText(), readQuantity(amountField.getText(), unitBox.getValue()));
            nameField.clear();
            amountField.clear();
            // the PantryChanged event refreshes the table
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    public void onEdit(PantryItem item) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change " + item.getIngredient().getName());
        TextField amount = new TextField(String.valueOf(item.getQuantity().getAmount()));
        ComboBox<Unit> unit = new ComboBox<>();
        unit.getItems().setAll(Unit.values());
        unit.setValue(item.getQuantity().getUnit());
        VBox form = new VBox(8, new Label("Amount"), amount, new Label("Unit"), unit);
        form.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                facade.updatePantryItem(item.getId(), readQuantity(amount.getText(), unit.getValue()));
            } catch (AppException ex) {
                showError(ex.getMessage());
            }
        });
    }

    public void onRemove(PantryItem item) {
        try {
            facade.removePantryItem(item.getId());
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** Observer: PantryChanged redraws the table. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::refresh);
    }

    private void refresh() {
        table.getItems().setAll(facade.getPantry());
    }

    private static Quantity readQuantity(String text, Unit unit) {
        if (unit == null) {
            throw new InvalidInputException("Choose a unit");
        }
        try {
            return new Quantity(Double.parseDouble(text.trim().replace(',', '.')), unit);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Amount must be a number, not '" + text + "'");
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}