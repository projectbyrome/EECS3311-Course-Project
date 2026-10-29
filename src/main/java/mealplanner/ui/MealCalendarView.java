package mealplanner.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.Recipe;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

public class MealCalendarView extends BorderPane implements EventListener {

    private static final DateTimeFormatter HEADER = DateTimeFormatter.ofPattern("EEE d MMM");

    private final AppFacade facade;
    private final GridPane grid = new GridPane();
    private final Label weekLabel = new Label();
    private LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private List<DayPlan> week = List.of();

    public MealCalendarView(AppFacade facade) {
        this.facade = facade;
        Button prev = new Button("< Previous week");
        Button next = new Button("Next week >");
        prev.setOnAction(e -> onShowWeek(weekStart.minusWeeks(1)));
        next.setOnAction(e -> onShowWeek(weekStart.plusWeeks(1)));
        HBox nav = new HBox(10, prev, weekLabel, next);
        nav.setPadding(new Insets(8));
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(8));
        setTop(nav);
        setCenter(grid);
        onShowWeek(weekStart);
    }

    public void onShowWeek(LocalDate weekStart) {
        this.weekStart = weekStart;
        render(facade.getWeek(weekStart));
    }

    /** Asks for a recipe and servings, then adds the meal to the chosen day and slot. */
    public void onAddMeal(LocalDate date, MealSlot slot) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add " + slot.name().toLowerCase() + " on " + date.format(HEADER));
        ComboBox<Recipe> recipeBox = new ComboBox<>();
        recipeBox.getItems().setAll(facade.searchLibrary(""));
        Spinner<Double> servings = new Spinner<>(0.5, 20, 1, 0.5);
        servings.setEditable(true);
        VBox form = new VBox(8, new Label("Recipe"), recipeBox, new Label("Servings"), servings);
        form.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            Recipe recipe = recipeBox.getValue();
            if (recipe == null) {
                showError("Choose a recipe first.");
                return;
            }
            try {
                facade.addMealToDay(date, slot, recipe.getId(), servings.getValue());
                // the CalendarChanged event refreshes the grid
            } catch (AppException ex) {
                showError(ex.getMessage());
            }
        });
    }

    public void onRemoveMeal(MealEntry entry) {
        try {
            facade.removeMeal(dateOf(entry), entry.getId());
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    public void onMarkSkipped(MealEntry entry) {
        try {
            facade.skipMeal(dateOf(entry), entry.getId());
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** Observer: CalendarChanged redraws the week. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(() -> onShowWeek(weekStart));
    }

    private void render(List<DayPlan> week) {
        this.week = week;
        weekLabel.setText("Week of " + weekStart.format(HEADER));
        grid.getChildren().clear();
        MealSlot[] slots = MealSlot.values();
        for (int row = 0; row < slots.length; row++) {
            grid.add(new Label(slots[row].name()), 0, row + 1);
        }
        for (int col = 0; col < week.size(); col++) {
            DayPlan day = week.get(col);
            grid.add(new Label(day.getDate().format(HEADER) + (day.isClosed() ? " (closed)" : "")), col + 1, 0);
            for (int row = 0; row < slots.length; row++) {
                grid.add(slotCell(day, slots[row]), col + 1, row + 1);
            }
        }
    }

    private VBox slotCell(DayPlan day, MealSlot slot) {
        boolean past = day.isClosed() || day.getDate().isBefore(LocalDate.now());
        VBox cell = new VBox(4);
        cell.setPadding(new Insets(4));
        cell.setStyle("-fx-border-color: #cccccc; -fx-min-width: 120; -fx-min-height: 60;");
        for (MealEntry entry : day.entriesIn(slot)) {
            Label label = new Label(entry.toString());
            if (entry.isMissingIngredients() && !entry.isSkipped()) {
                label.setStyle("-fx-text-fill: #b00020;"); // flag: pantry is short
                label.setText("⚑ " + label.getText());
            }
            if (!day.isClosed()) {
                MenuItem skip = new MenuItem("Mark skipped");
                MenuItem remove = new MenuItem("Remove");
                skip.setOnAction(e -> onMarkSkipped(entry));
                remove.setOnAction(e -> onRemoveMeal(entry));
                label.setContextMenu(new ContextMenu(skip, remove));
            }
            cell.getChildren().add(label);
        }
        if (!day.isClosed()) {
            Button add = new Button("+");
            add.setOnAction(e -> onAddMeal(day.getDate(), slot));
            cell.getChildren().add(add);
        }
        return cell;
    }

    private LocalDate dateOf(MealEntry entry) {
        return week.stream()
                .filter(d -> d.getEntries().stream().anyMatch(e -> e.getId() == entry.getId()))
                .map(DayPlan::getDate)
                .findFirst()
                .orElseThrow();
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}