package mealplanner.ui;

import javafx.application.Platform;
import java.util.List;
import javafx.scene.Cursor;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
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
import mealplanner.domain.StapleStatus;
import mealplanner.domain.Unit;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;
import mealplanner.service.PantryService;

/**
 * F04 GUI (Pantry menu): the pantry as a table, a row to add an ingredient, and Edit / Remove buttons.
 * Items are listed A to Z, and an item with 0 left shows "Out of stock". Staples (salt, oil, spices) have no
 * amount, only Have / Running low: "Add as staple" adds one, and Edit changes its status. The All / Ingredient /
 * Staple filter above the table shows one type at a time.
 * Listens for PantryChanged, so Finish Shopping and the end-of-day update also refresh it.
 * Rough draft: the layout is built in code; it can move to an .fxml file later.
 */
public class PantryView extends BorderPane implements EventListener {

    private final AppFacade facade;
    private final TableView<PantryItem> table = new TableView<>();
    private final TextField nameField = new TextField();
    private final TextField amountField = new TextField();
    private final ComboBox<Unit> unitBox = new ComboBox<>();
    /** "All", "Ingredient" or "Staple". */
    private String filter = "All";

    public PantryView(AppFacade facade) {
        this.facade = facade;

        TableColumn<PantryItem, String> name = new TableColumn<>("Ingredient");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIngredient().getName()));
        TableColumn<PantryItem, String> qty = new TableColumn<>("Quantity");
        // An item with 0 left says "Out of stock" instead of "0.0 G".
        qty.setCellValueFactory(c -> new SimpleStringProperty(quantityText(c.getValue())));
        TableColumn<PantryItem, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isStaple() ? "Staple" : "Ingredient"));
        TableColumn<PantryItem, String> added = new TableColumn<>("Added on");
        added.setCellValueFactory(new PropertyValueFactory<>("addedOn"));
        table.getColumns().add(name);
        table.getColumns().add(type);
        table.getColumns().add(qty);
        table.getColumns().add(added);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        nameField.setPromptText("Ingredient, e.g. eggs");
        amountField.setPromptText("Amount");
        amountField.setPrefWidth(80);
        unitBox.getItems().setAll(Unit.values());
        unitBox.setValue(Unit.G);
        Button add = new Button("Add");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> onAdd());
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            PantryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onEdit(sel);
        });
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            PantryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onRemove(sel);
        });

        Button addStaple = new Button("Add as staple");
        addStaple.setOnAction(e -> onAddStaple());
        HBox addRow = new HBox(6, nameField, amountField, unitBox, add, addStaple);
        HBox editRow = new HBox(6, edit, remove);
        VBox bottom = new VBox(8, addRow, editRow);
        bottom.setPadding(new Insets(12, 0, 0, 0));
        HBox filterRow = new HBox(segmentedFilter());
        filterRow.setPadding(new Insets(0, 0, 12, 0));
        VBox center = new VBox(filterRow, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(center);
        setBottom(bottom);
        refresh();
    }

    /** SD02 from the GUI: addPantryItem(name, quantity). Unknown names and bad amounts show a message. */
    public void onAdd() {
        try {
            if (!knownOrAdded(nameField.getText())) return;
            facade.addPantryItem(nameField.getText(), readQuantity(amountField.getText(), unitBox.getValue()));
            nameField.clear();
            amountField.clear();
            // the PantryChanged event refreshes the table
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** A staple has no amount: only Have / Running low (the amount and unit boxes are ignored). */
    public void onAddStaple() {
        try {
            if (!knownOrAdded(nameField.getText())) return;
            facade.addStaple(nameField.getText(), StapleStatus.HAVE);
            nameField.clear();
            amountField.clear();
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * A name that isn't in the ingredient list (e.g. ketchup) can be added to it after a yes/no question.
     * Returns false if the user said no. A blank name goes through so the usual error is shown.
     */
    private boolean knownOrAdded(String name) {
        if (name == null || name.isBlank() || facade.isKnownIngredient(name)) {
            return true;
        }
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
                "\"" + name.trim() + "\" isn't a known ingredient yet. Add it to the ingredient list?",
                ButtonType.YES, ButtonType.NO);
        ask.setHeaderText("New ingredient");
        if (ask.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) {
            return false;
        }
        facade.addIngredient(name);
        return true;
    }

    public void onEdit(PantryItem item) {
        if (item.isStaple()) {
            onEditStaple(item);
            return;
        }
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

    private void onEditStaple(PantryItem item) {
        ChoiceDialog<StapleStatus> dialog = new ChoiceDialog<>(item.getStapleStatus(), StapleStatus.values());
        dialog.setTitle("Change " + item.getIngredient().getName());
        dialog.setHeaderText(item.getIngredient().getName() + " is a staple: no amount, only a status.");
        dialog.setContentText("Status");
        dialog.showAndWait().ifPresent(status -> {
            try {
                facade.setStapleStatus(item.getId(), status);
            } catch (AppException ex) {
                showError(ex.getMessage());
            }
        });
    }

    /** "500 G", "Out of stock" for an ingredient at 0, or "Have" / "Running low" for a staple. */
    private static String quantityText(PantryItem item) {
        if (item.isStaple()) return item.getStapleStatus().label();
        return PantryService.isOutOfStock(item) ? "Out of stock" : item.getQuantity().toString();
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
        table.getItems().setAll(facade.getPantry().stream().filter(this::shown).toList());
    }

    /** The type filter: All, Ingredient (tracked amount) or Staple (have / running low). */
    private boolean shown(PantryItem item) {
        return switch (filter) {
            case "Ingredient" -> !item.isStaple();
            case "Staple" -> item.isStaple();
            default -> true;
        };
    }

    /**
     * All / Ingredient / Staple as one rounded control; the chosen part is filled with the accent colour.
     * Drawn with plain panes and Text (no style classes), so Theme leaves these colours alone.
     */
    private HBox segmentedFilter() {
        HBox control = new HBox(4);
        control.setPadding(new Insets(4));
        control.setBackground(new Background(new BackgroundFill(Theme.PANEL, new CornerRadii(12), Insets.EMPTY)));
        control.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID, new CornerRadii(12),
                BorderWidths.DEFAULT)));
        control.setMaxWidth(Region.USE_PREF_SIZE);
        for (String name : List.of("All", "Ingredient", "Staple")) {
            Text label = new Text(name);
            label.setFont(Font.font("Inter SemiBold", 13));
            StackPane segment = new StackPane(label);
            segment.setPadding(new Insets(7, 18, 7, 18));
            segment.setCursor(Cursor.HAND);
            segment.setOnMouseClicked(e -> {
                filter = name;
                paintSegments(control);
                refresh();
            });
            segment.setUserData(name);
            control.getChildren().add(segment);
        }
        paintSegments(control);
        return control;
    }

    private void paintSegments(HBox control) {
        for (javafx.scene.Node node : control.getChildren()) {
            StackPane segment = (StackPane) node;
            boolean chosen = filter.equals(segment.getUserData());
            segment.setBackground(new Background(new BackgroundFill(chosen ? Theme.ACCENT : Color.TRANSPARENT,
                    new CornerRadii(9), Insets.EMPTY)));
            ((Text) segment.getChildren().get(0)).setFill(chosen ? Color.WHITE : Theme.TEXT_MUTED);
        }
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