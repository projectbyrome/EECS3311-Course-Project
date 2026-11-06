package mealplanner.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.DayPlan;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.ValidationReport;
import mealplanner.events.AppEvent;
import mealplanner.events.CalendarChanged;
import mealplanner.events.EventListener;
import mealplanner.events.ProfileChanged;
import mealplanner.model.DaySummary;

/**
 * F10 GUI (SD07): the nutrition of a day or a week compared with the daily goals, Claude's short summary,
 * and a form to edit the goals. It opens in its own window when "Day nutrition" or "Week nutrition" is
 * clicked in the Meal Calendar.
 */
public class NutritionSummaryView extends HBox implements EventListener {

    private static final DateTimeFormatter TITLE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final String RED = "-fx-text-fill: #F2545B;";   // Theme.DANGER
    private static final String GREEN = "-fx-text-fill: #4CC38A;";

    private final AppFacade facade;
    private final Label title = new Label("Nutrition summary");
    private final Label meals = new Label("Click \"Day nutrition\" or \"Week nutrition\" above.");
    private final VBox goalLines = new VBox(4);
    private final Label status = new Label();
    private final Label summary = new Label();
    private final TextField calories = new TextField();
    private final TextField protein = new TextField();
    private final TextField carbs = new TextField();
    private final TextField fat = new TextField();
    private final TextField fiber = new TextField();
    private final TextField sodium = new TextField();
    private final Label goalMessage = new Label();
    /** What is shown now, so events can redraw it: a day, or a week start when showingWeek. */
    private LocalDate shown;
    private boolean showingWeek;
    /** Only the newest background task may draw its result. */
    private int request;
    /** Set by MainWindow: opens or closes the summary under the calendar. */
    private Runnable onOpen = () -> { };
    private Runnable onClose = () -> { };

    public NutritionSummaryView(AppFacade facade) {
        this.facade = facade;
        setSpacing(16);
        VBox result = resultCard();
        HBox.setHgrow(result, Priority.ALWAYS);
        getChildren().addAll(result, goalCard());
        fillGoalForm(facade.getProfile().getDefaultGoal());
    }

    /** MainWindow passes what showing and hiding the summary means in its layout. */
    public void setOpenCloseHandlers(Runnable onOpen, Runnable onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    /** SD07: daySummary(date) in a background task, because it may call USDA and Claude. */
    public void onShowDay(LocalDate date) {
        onOpen.run();
        loadDay(date);
    }

    /** Works out the day without opening the window (used when a meal changes while the window is open). */
    private void loadDay(LocalDate date) {
        shown = date;
        showingWeek = false;
        int mine = ++request;
        title.setText(date.format(TITLE));
        clearResult("Working out the nutrition and asking Claude for a summary...");
        Task<DaySummary> task = new Task<>() {
            @Override
            protected DaySummary call() {
                return facade.daySummary(date);
            }
        };
        task.setOnSucceeded(e -> {
            if (mine == request) showSummary(task.getValue());
        });
        task.setOnFailed(e -> {
            if (mine == request) showProblem(task.getException().getMessage());
        });
        start(task, "day-summary");
    }

    /** SD07 week view: weekSummary(weekStart), compared with seven times the daily goal. */
    public void onShowWeek(LocalDate weekStart) {
        onOpen.run();
        loadWeek(weekStart);
    }

    /** Adds up the week without opening the window. */
    private void loadWeek(LocalDate weekStart) {
        shown = weekStart;
        showingWeek = true;
        int mine = ++request;
        title.setText("Week of " + weekStart.format(TITLE));
        clearResult("Adding up the week...");
        List<DayPlan> week = facade.getWeek(weekStart);
        long planned = week.stream().filter(d -> !d.activeEntries().isEmpty()).count();
        NutritionGoal goal = facade.getProfile().getDefaultGoal();
        if (planned == 0) {
            clearResult("No meals planned for this week.");
            return;
        }
        Task<NutritionSummary> task = new Task<>() {
            @Override
            protected NutritionSummary call() {
                return facade.weekSummary(weekStart);
            }
        };
        task.setOnSucceeded(e -> {
            if (mine == request) showWeek(task.getValue(), goal.times(7), planned);
        });
        task.setOnFailed(e -> {
            if (mine == request) showProblem(task.getException().getMessage());
        });
        start(task, "week-summary");
    }

    /** UC10 5a: invalid values are shown under the form and nothing is saved; a saved goal redraws the summary. */
    public void onEditGoals(NutritionGoal goal) {
        try {
            ValidationReport report = facade.updateGoal(goal);
            if (report.isValid()) {
                goalMessage.setStyle(GREEN);
                goalMessage.setText("Goals saved.");
                // ProfileChanged (published by the service) redraws the summary through onEvent
            } else {
                showGoalError(String.join("\n", report.getViolations()));
            }
        } catch (AppException ex) {
            showGoalError(ex.getMessage());
        }
    }

    /** Observer: the profile (goals), a planned day or a recipe changed, so redraw what is shown. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(() -> {
            if (event instanceof ProfileChanged) fillGoalForm(facade.getProfile().getDefaultGoal());
            // Only redraw while the summary window is open, and never open it from here:
            // adding a meal should not pop the window up.
            if (shown == null || !isWindowOpen()) return;
            if (event instanceof CalendarChanged c && !affectsShown(c.date)) return;
            if (showingWeek) loadWeek(shown); else loadDay(shown);
        });
    }

    private boolean isWindowOpen() {
        return getScene() != null && getScene().getWindow() != null && getScene().getWindow().isShowing();
    }

    private boolean affectsShown(LocalDate date) {
        return showingWeek ? !date.isBefore(shown) && date.isBefore(shown.plusDays(7)) : date.equals(shown);
    }

    // ---------------------------------------------------------------- showing results

    private void showSummary(DaySummary s) {
        title.setText(s.date.format(TITLE));
        if (!s.hasMeals()) {
            clearResult(DaySummary.NO_MEALS);
            return;
        }
        meals.setText("Meals:\n" + String.join("\n", s.meals));
        showGoalLines(s.goalLines());
        String state = s.goalMet ? "All goals met." : "Goals not met.";
        if (s.isIncomplete()) {
            state += "  Incomplete: some nutrition data is missing, so the real totals are higher.";
        }
        status.setText(state);
        status.setStyle(s.goalMet && !s.isIncomplete() ? GREEN : RED);
        summary.setText(s.text);
    }

    private void showWeek(NutritionSummary totals, NutritionGoal weekGoal, long planned) {
        List<String> misses = weekGoal.misses(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        meals.setText(planned + " of 7 days have meals. Week totals compared with 7 x your daily goal:");
        showGoalLines(DaySummary.goalLines(totals, weekGoal, misses));
        status.setText(String.format("Average per planned day: %.0f kcal, %.1f g protein, %.1f g carbs, %.1f g fat%s",
                totals.getCalories() / planned, totals.getProteinG() / planned, totals.getCarbsG() / planned,
                totals.getFatG() / planned,
                totals.isComplete() ? "" : "\nIncomplete: some nutrition data is missing, so the real totals are higher."));
        status.setStyle(totals.isComplete() ? "" : RED);
        summary.setText("Open a single day for Claude's summary and suggestions.");
    }

    private void showGoalLines(List<String> lines) {
        goalLines.getChildren().clear();
        for (String line : lines) {
            Label l = new Label(line);
            if (line.endsWith("NOT MET")) l.setStyle(RED);
            goalLines.getChildren().add(l);
        }
    }

    private void clearResult(String message) {
        meals.setText(message);
        goalLines.getChildren().clear();
        status.setText("");
        status.setStyle("");
        summary.setText("");
    }

    private void showProblem(String message) {
        clearResult("");
        status.setStyle(RED);
        status.setText("Could not work out the summary: " + message);
    }

    // ---------------------------------------------------------------- the two cards

    private VBox resultCard() {
        title.getStyleClass().add("section-title");
        Button hide = new Button("Close");
        hide.setOnAction(e -> onClose.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(10, title, gap, hide);
        header.setAlignment(Pos.CENTER_LEFT);

        meals.setWrapText(true);
        status.setWrapText(true);
        summary.setWrapText(true);
        Label summaryTitle = new Label("Summary");
        summaryTitle.getStyleClass().add("card-title");
        VBox body = new VBox(8, meals, goalLines, status, summaryTitle, summary);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        // the text box is at least as tall as the scroll area, so no light area shows under short text
        body.minHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = new VBox(10, header, scroll);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox goalCard() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, small("Calories (kcal, max)"), calories);
        grid.addRow(1, small("Protein (g, min)"), protein);
        grid.addRow(2, small("Carbohydrates (g, max)"), carbs);
        grid.addRow(3, small("Fat (g, max)"), fat);
        grid.addRow(4, small("Fibre (g, min; 0 = none)"), fiber);
        grid.addRow(5, small("Sodium (mg, max; 0 = none)"), sodium);
        for (TextField f : List.of(calories, protein, carbs, fat, fiber, sodium)) f.setPrefColumnCount(6);
        Button save = new Button("Save goals");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> readGoalForm());
        goalMessage.setWrapText(true);
        goalMessage.setMaxWidth(260);
        Label heading = new Label("Daily goals");
        heading.getStyleClass().add("card-title");
        VBox card = new VBox(10, heading, grid, save, goalMessage);
        card.getStyleClass().add("card");
        card.setMinWidth(300);
        return card;
    }

    private void readGoalForm() {
        try {
            onEditGoals(new NutritionGoal(number(calories, "Calories"), number(protein, "Protein"),
                    number(carbs, "Carbohydrates"), number(fat, "Fat"), number(fiber, "Fibre"), number(sodium, "Sodium")));
        } catch (AppException ex) {
            showGoalError(ex.getMessage());
        }
    }

    /** A negative number cannot make a NutritionGoal, so it gets the same message as 0 here. */
    private double number(TextField field, String label) {
        double v;
        try {
            v = Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be a number");
        }
        if (v < 0) throw new InvalidInputException(label + " must be greater than 0");
        return v;
    }

    private void fillGoalForm(NutritionGoal goal) {
        calories.setText(String.format("%.0f", goal.getCalories()));
        protein.setText(String.format("%.0f", goal.getProteinG()));
        carbs.setText(String.format("%.0f", goal.getCarbsG()));
        fat.setText(String.format("%.0f", goal.getFatG()));
        fiber.setText(String.format("%.0f", goal.getFiberG()));
        sodium.setText(String.format("%.0f", goal.getSodiumMg()));
    }

    private void showGoalError(String message) {
        goalMessage.setStyle(RED);
        goalMessage.setText("Goals not saved:\n" + message);
    }

    private static Node small(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("small-label");
        return label;
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}