package mealplanner.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

/**
 * F01 GUI: a month of meals. Each day shows one dot per meal slot (coloured when something is planned).
 * Clicking a day lists its meals underneath; clicking a meal fills the Meal details box on the right,
 * where it can be changed, skipped or removed. The same box adds a new meal to the chosen day.
 *
 * The calendar draws its own colours (Text, Circle and plain boxes), so Theme leaves them alone.
 */
public class MealCalendarView extends HBox implements EventListener {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FIELD =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);

    // Colours of the calendar (the rest come from Theme)
    private static final Color PANEL = Color.web("#202329");
    private static final Color CELL_BORDER = Color.web("#2E333C");
    private static final Color CELL_HOVER = Color.web("#4A5160");
    private static final Color SELECTED_DAY = Color.web("#1F3460");
    private static final Color ROW_SELECTED = Color.web("#2C313B");
    private static final Color EMPTY_DOT = Color.web("#3A404B");

    private final AppFacade facade;

    // month card
    private final Label monthLabel = new Label();
    private final GridPane monthGrid = new GridPane();
    // day card
    private final Label dayTitle = new Label();
    private final Label dayCount = new Label();
    private final VBox mealList = new VBox();
    // details card
    private final Label detailsTitle = new Label();
    private final Button newMealButton = new Button("Add");
    private final TextField dateField = new TextField();
    private final ComboBox<MealSlot> slotBox = new ComboBox<>();
    private final ComboBox<Recipe> recipeBox = new ComboBox<>();
    private final Label servingsLabel = new Label();
    private final Button lessButton = new Button("−");
    private final Button moreButton = new Button("+");
    private final TextField notesField = new TextField();
    private final Button viewRecipeButton = new Button("View recipe");
    private final Button saveButton = new Button("Save");
    private final Button skipButton = new Button("Skip");
    private final Button removeButton = new Button("Remove meal");
    private final Label pastNote = new Label("This day has passed, so its meals can't be changed.");

    private YearMonth month = YearMonth.now();
    private LocalDate selectedDay = LocalDate.now();
    /** The meal shown in the details box, or null when the box is adding a new meal. */
    private MealEntry selectedMeal;
    private double servings = 1;
    private List<DayPlan> days = List.of();

    public MealCalendarView(AppFacade facade) {
        this.facade = facade;
        setSpacing(16);
        VBox left = new VBox(14, monthCard(), dayCard());
        HBox.setHgrow(left, Priority.ALWAYS);
        getChildren().addAll(left, detailsCard());
        refresh();
        showNewMeal();
    }

    // ---------------------------------------------------------------- building the three cards

    private VBox monthCard() {
        monthLabel.getStyleClass().add("section-title");
        Label subtitle = new Label("Your meals for the month");
        subtitle.getStyleClass().add("description");
        VBox titles = new VBox(4, monthLabel, subtitle);

        Button prev = new Button("‹");
        Button next = new Button("›");
        Button today = new Button("Today");
        today.getStyleClass().add("primary");
        prev.setOnAction(e -> onShowMonth(month.minusMonths(1)));
        next.setOnAction(e -> onShowMonth(month.plusMonths(1)));
        today.setOnAction(e -> onSelectDay(LocalDate.now()));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(6, titles, gap, prev, next, today);
        header.setAlignment(Pos.TOP_LEFT);

        monthGrid.setHgap(6);
        monthGrid.setVgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7); // the seven days share the width
            monthGrid.getColumnConstraints().add(column);
        }

        HBox legend = new HBox(14);
        legend.setAlignment(Pos.CENTER_LEFT);
        for (MealSlot slot : MealSlot.values()) {
            HBox item = new HBox(6, new Circle(4, dotColour(slot)),
                    text(slotName(slot), 12, "Inter", Theme.TEXT_MUTED));
            item.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(item);
        }
        Region legendGap = new Region();
        HBox.setHgrow(legendGap, Priority.ALWAYS);
        legend.getChildren().addAll(legendGap, text("Grey dot = nothing planned", 12, "Inter", Theme.TEXT_MUTED));

        VBox card = section(10);
        card.getChildren().addAll(header, monthGrid, legend);
        return card;
    }

    private VBox dayCard() {
        dayTitle.getStyleClass().add("section-title");
        dayCount.getStyleClass().add("description");
        VBox titles = new VBox(3, dayTitle, dayCount);
        mealList.setBackground(fill(Theme.CARD, 10));
        mealList.setBorder(line(Theme.BORDER, 10));
        ScrollPane scroll = new ScrollPane(mealList);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = section(10);
        card.getChildren().addAll(titles, scroll);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox detailsCard() {
        detailsTitle.getStyleClass().add("section-title");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        newMealButton.setOnAction(e -> showNewMeal()); // empties the box to add another meal
        HBox header = new HBox(detailsTitle, gap, newMealButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinHeight(34); // same height with or without the Add button

        dateField.setEditable(false);
        dateField.setFocusTraversable(false);

        slotBox.getItems().setAll(MealSlot.values());
        slotBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MealSlot slot) {
                return slot == null ? "" : slotName(slot);
            }

            @Override
            public MealSlot fromString(String text) {
                return null; // the box is not editable, so this is never used
            }
        });
        slotBox.setMaxWidth(Double.MAX_VALUE);

        recipeBox.setPromptText("Choose a recipe from your library");
        recipeBox.setMaxWidth(Double.MAX_VALUE);
        recipeBox.setOnShowing(e -> reloadRecipes()); // recipes added since are in the list

        servingsLabel.setMinWidth(90);
        lessButton.setOnAction(e -> changeServings(-0.5));
        moreButton.setOnAction(e -> changeServings(0.5));
        Label adjust = new Label("Adjust");
        adjust.getStyleClass().add("small-label");
        HBox servingsRow = new HBox(10, servingsLabel, adjust, lessButton, moreButton);
        servingsRow.setAlignment(Pos.CENTER_LEFT);

        notesField.setPromptText("Anything to remember");

        viewRecipeButton.setMaxWidth(Double.MAX_VALUE);
        viewRecipeButton.setOnAction(e -> onViewRecipe());
        saveButton.getStyleClass().add("primary");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(saveButton, Priority.ALWAYS);
        saveButton.setOnAction(e -> onSave());
        skipButton.setOnAction(e -> onMarkSkipped(selectedMeal));
        removeButton.setOnAction(e -> onRemoveMeal(selectedMeal));
        HBox actions = new HBox(8, saveButton, skipButton, removeButton);
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS); // keeps the buttons at the bottom of the box

        pastNote.getStyleClass().add("description");
        pastNote.setWrapText(true);

        VBox card = section(14);
        card.setPadding(new Insets(18));
        card.setMinWidth(320);
        card.setPrefWidth(320);
        card.setMaxWidth(320);
        card.getChildren().addAll(header,
                field("Date", dateField),
                field("Meal", slotBox),
                field("Recipe", recipeBox),
                field("Serving size", servingsRow),
                field("Notes", notesField),
                viewRecipeButton, pastNote, push, actions);
        return card;
    }

    // ---------------------------------------------------------------- showing data

    public void onShowMonth(YearMonth month) {
        this.month = month;
        refresh();
    }

    /** Reloads the shown month and redraws it, keeping the chosen day and meal. */
    private void refresh() {
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        int count = (int) (last.toEpochDay() - first.toEpochDay()) + 1; // 35 or 42 days (5 or 6 weeks)
        days = facade.getDays(first, count);
        if (selectedMeal != null) {
            selectedMeal = findMeal(selectedMeal.getId()); // the saved copy, or null if it was removed
        }
        drawMonth();
        drawDay();
    }

    private void drawMonth() {
        monthLabel.setText(month.format(MONTH));
        monthGrid.getChildren().clear();
        DayOfWeek weekday = DayOfWeek.SUNDAY;
        for (int col = 0; col < 7; col++) {
            Text name = text(weekday.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 12, "Inter Medium",
                    Theme.TEXT_MUTED);
            monthGrid.add(new StackPane(name), col, 0);
            weekday = weekday.plus(1);
        }
        for (int i = 0; i < days.size(); i++) {
            monthGrid.add(dayCell(days.get(i)), i % 7, i / 7 + 1);
        }
    }

    private VBox dayCell(DayPlan day) {
        LocalDate date = day.getDate();
        boolean inMonth = YearMonth.from(date).equals(month);
        boolean selected = date.equals(selectedDay);
        boolean today = date.equals(LocalDate.now());

        Text number = text(String.valueOf(date.getDayOfMonth()), 13,
                selected || today ? "Inter SemiBold" : "Inter Medium",
                today && !selected ? Theme.ACCENT : Theme.TEXT);
        HBox dots = new HBox(4);
        for (MealSlot slot : MealSlot.values()) {
            boolean planned = day.entriesIn(slot).stream().anyMatch(e -> !e.isSkipped());
            dots.getChildren().add(new Circle(4, planned ? dotColour(slot) : EMPTY_DOT));
        }
        dots.setVisible(inMonth);

        VBox cell = new VBox(6, number, dots);
        cell.setPadding(new Insets(6, 8, 7, 8));
        cell.setMinHeight(44);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setCursor(Cursor.HAND);
        Color border = selected ? Theme.ACCENT : CELL_BORDER;
        cell.setBackground(fill(selected ? SELECTED_DAY : Theme.CARD, 8));
        cell.setBorder(line(border, 8));
        cell.setOpacity(inMonth ? 1 : 0.4);
        cell.setOnMouseEntered(e -> cell.setBorder(line(selected ? Theme.ACCENT : CELL_HOVER, 8)));
        cell.setOnMouseExited(e -> cell.setBorder(line(border, 8)));
        cell.setOnMouseClicked(e -> onSelectDay(date));
        return cell;
    }

    private void drawDay() {
        DayPlan day = dayPlan(selectedDay);
        List<MealEntry> meals = day.getEntries().stream()
                .sorted(Comparator.comparing(MealEntry::getSlot)) // breakfast, lunch, dinner, snack
                .toList();
        dayTitle.setText(selectedDay.format(DAY_TITLE));
        long planned = meals.stream().filter(m -> !m.isSkipped()).count();
        dayCount.setText(planned == 0 ? "No meals planned"
                : planned == 1 ? "1 meal planned" : planned + " meals planned");

        mealList.getChildren().clear();
        if (meals.isEmpty()) {
            Label empty = new Label(isPast(day) ? "Nothing was planned on this day."
                    : "Nothing planned yet. Add a meal on the right.");
            empty.getStyleClass().add("description");
            empty.setPadding(new Insets(20));
            mealList.getChildren().add(empty);
        }
        for (int i = 0; i < meals.size(); i++) {
            mealList.getChildren().add(mealRow(meals.get(i), i == 0));
        }
    }

    private HBox mealRow(MealEntry meal, boolean first) {
        boolean picked = selectedMeal != null && selectedMeal.getId() == meal.getId();

        Region bar = new Region();
        bar.setMinWidth(4);
        bar.setMaxWidth(4);
        bar.setBackground(fill(dotColour(meal.getSlot()), 2));

        String sub = servingsText(meal.getServings());
        if (!meal.getNotes().isEmpty()) {
            sub += "  ·  " + meal.getNotes();
        }
        VBox texts = new VBox(3,
                text(meal.getRecipe().getName(), 15, "Inter SemiBold", Theme.TEXT),
                text(sub, 12, "Inter", Theme.TEXT_MUTED));
        if (meal.isSkipped()) {
            texts.getChildren().add(text("Skipped", 12, "Inter Medium", Theme.TEXT_MUTED));
            texts.setOpacity(0.6);
        } else if (meal.isMissingIngredients()) {
            texts.getChildren().add(text("⚑ Missing ingredients in the pantry", 12, "Inter Medium", Theme.DANGER));
        }
        HBox.setHgrow(texts, Priority.ALWAYS);

        HBox row = new HBox(12, bar, texts, pill(meal.getSlot()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 12));
        row.setCursor(Cursor.HAND);
        Background normal = fill(picked ? ROW_SELECTED : Color.TRANSPARENT, 0);
        row.setBackground(normal);
        if (!first) {
            row.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                    new BorderWidths(1, 0, 0, 0)))); // a line between meals
        }
        row.setOnMouseEntered(e -> row.setBackground(fill(ROW_SELECTED, 0)));
        row.setOnMouseExited(e -> row.setBackground(normal));
        row.setOnMouseClicked(e -> showMeal(meal));
        return row;
    }

    /** Fills the details box with a planned meal. */
    private void showMeal(MealEntry meal) {
        selectedMeal = meal;
        boolean past = isPast(dayPlan(selectedDay));
        boolean canChange = !past && !meal.isSkipped();
        detailsTitle.setText(meal.isSkipped() ? "Skipped meal" : "Meal details");
        saveButton.setText("Save");
        dateField.setText(selectedDay.format(DATE_FIELD));
        slotBox.setValue(meal.getSlot());
        reloadRecipes();
        recipeBox.setValue(recipeBox.getItems().stream()
                .filter(r -> r.getId() == meal.getRecipe().getId())
                .findFirst()
                .orElse(meal.getRecipe())); // an archived recipe is not in the list, but show it anyway
        servings = meal.getServings();
        servingsLabel.setText(servingsText(servings));
        notesField.setText(meal.getNotes());
        setEditable(canChange);
        show(viewRecipeButton, true);
        show(newMealButton, !past);
        show(saveButton, canChange);
        show(skipButton, canChange);
        show(removeButton, !past);
        show(pastNote, past);
        drawDay();
    }

    /** Empties the details box so a new meal can be added to the chosen day. */
    private void showNewMeal() {
        selectedMeal = null;
        DayPlan day = dayPlan(selectedDay);
        boolean past = isPast(day);
        detailsTitle.setText("Add a meal");
        saveButton.setText("Add");
        dateField.setText(selectedDay.format(DATE_FIELD));
        slotBox.setValue(firstFreeSlot(day));
        reloadRecipes();
        recipeBox.setValue(null);
        servings = 1;
        servingsLabel.setText(servingsText(servings));
        notesField.clear();
        setEditable(!past);
        show(viewRecipeButton, !past);
        show(newMealButton, false);
        show(saveButton, !past);
        show(skipButton, false);
        show(removeButton, false);
        show(pastNote, past);
        drawDay();
    }

    // ---------------------------------------------------------------- user actions

    public void onSelectDay(LocalDate date) {
        selectedDay = date;
        if (!YearMonth.from(date).equals(month)) {
            month = YearMonth.from(date); // a faded day of the next or previous month, or Today
            refresh();
        } else {
            drawMonth();
        }
        showNewMeal();
    }

    /** Add saves a new meal; Save changes the meal shown in the details box. */
    private void onSave() {
        Recipe recipe = recipeBox.getValue();
        if (recipe == null) {
            showError("Choose a recipe first.");
            return;
        }
        try {
            if (selectedMeal == null) {
                DayPlan day = facade.addMealToDay(selectedDay, slotBox.getValue(), recipe.getId(), servings,
                        notesField.getText());
                List<MealEntry> entries = day.getEntries();
                selectedMeal = entries.get(entries.size() - 1); // show the meal just added
            } else {
                facade.updateMeal(selectedDay, selectedMeal.getId(), slotBox.getValue(), recipe.getId(),
                        servings, notesField.getText());
            }
            // the CalendarChanged event redraws the calendar
        } catch (AppException ex) {
            showError(ex.getMessage()); // e.g. a day that has passed: nothing is saved
        }
    }

    public void onRemoveMeal(MealEntry entry) {
        if (entry == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + entry.getRecipe().getName() + " from " + selectedDay.format(DAY_TITLE) + "?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            facade.removeMeal(selectedDay, entry.getId());
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    public void onMarkSkipped(MealEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            facade.skipMeal(selectedDay, entry.getId());
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    /** Shows the recipe's ingredients for the chosen serving size, and its steps. */
    private void onViewRecipe() {
        Recipe recipe = recipeBox.getValue();
        if (recipe == null) {
            showError("Choose a recipe first.");
            return;
        }
        StringBuilder text = new StringBuilder("Ingredients for " + servingsText(servings) + ":\n");
        for (RecipeIngredient line : recipe.scaledTo(servings)) {
            text.append("  • ").append(line).append('\n');
        }
        if (!recipe.getInstructions().isEmpty()) {
            text.append("\nSteps:\n");
            int n = 1;
            for (String step : recipe.getInstructions()) {
                text.append("  ").append(n++).append(". ").append(step).append('\n');
            }
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text.toString(), ButtonType.CLOSE);
        alert.setTitle(recipe.getName());
        alert.setHeaderText(recipe.getName());
        alert.showAndWait();
    }

    private void changeServings(double step) {
        servings = Math.max(0.5, servings + step);
        servingsLabel.setText(servingsText(servings));
    }

    /** Observer: CalendarChanged (and PantryChanged, through refreshed flags) redraws the month. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(() -> {
            refresh();
            if (selectedMeal != null) {
                showMeal(selectedMeal);
            } else {
                showNewMeal();
            }
        });
    }

    // ---------------------------------------------------------------- small helpers

    private void reloadRecipes() {
        Recipe chosen = recipeBox.getValue();
        recipeBox.getItems().setAll(facade.searchLibrary(""));
        recipeBox.setValue(chosen);
    }

    private void setEditable(boolean editable) {
        slotBox.setDisable(!editable);
        recipeBox.setDisable(!editable);
        lessButton.setDisable(!editable);
        moreButton.setDisable(!editable);
        notesField.setEditable(editable);
    }

    /** Shows or hides a node; a hidden node takes no space. */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private DayPlan dayPlan(LocalDate date) {
        return days.stream().filter(d -> d.getDate().equals(date)).findFirst().orElseGet(() -> new DayPlan(date));
    }

    private MealEntry findMeal(long id) {
        return dayPlan(selectedDay).getEntries().stream().filter(e -> e.getId() == id).findFirst().orElse(null);
    }

    private static boolean isPast(DayPlan day) {
        return day.isClosed() || day.getDate().isBefore(LocalDate.now());
    }

    private static MealSlot firstFreeSlot(DayPlan day) {
        for (MealSlot slot : MealSlot.values()) {
            if (day.entriesIn(slot).isEmpty()) {
                return slot;
            }
        }
        return MealSlot.SNACK;
    }

    private static String slotName(MealSlot slot) {
        String name = slot.name().toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1); // BREAKFAST -> Breakfast
    }

    private static String servingsText(double servings) {
        String number = servings == Math.floor(servings) ? String.valueOf((int) servings) : String.valueOf(servings);
        return number + (servings == 1 ? " serving" : " servings");
    }

    private static Color dotColour(MealSlot slot) {
        return switch (slot) {
            case BREAKFAST -> Color.web("#F5A524");
            case LUNCH -> Color.web("#34C38F");
            case DINNER -> Color.web("#3478F6");
            case SNACK -> Color.web("#A78BFA");
        };
    }

    /** The coloured label on the right of a meal ("Dinner"). */
    private static StackPane pill(MealSlot slot) {
        Color back = switch (slot) {
            case BREAKFAST -> Color.web("#3D2E12");
            case LUNCH -> Color.web("#143528");
            case DINNER -> Color.web("#1D3A73");
            case SNACK -> Color.web("#2F2550");
        };
        Color front = switch (slot) {
            case BREAKFAST -> Color.web("#F7C163");
            case LUNCH -> Color.web("#6FD8AE");
            case DINNER -> Color.web("#9DBDFB");
            case SNACK -> Color.web("#C4B2FC");
        };
        return pillOf(slotName(slot), back, front);
    }

    private static StackPane pillOf(String label, Color back, Color front) {
        StackPane pill = new StackPane(text(label, 11, "Inter SemiBold", front));
        pill.setPadding(new Insets(3, 9, 3, 9));
        pill.setBackground(fill(back, 999));
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pill;
    }

    /** A dark rounded box (the month, the day, and the meal details). */
    private static VBox section(double spacing) {
        VBox box = new VBox(spacing);
        box.setPadding(new Insets(16));
        box.setBackground(fill(PANEL, 12));
        box.setBorder(line(Theme.BORDER, 12));
        return box;
    }

    private static VBox field(String name, Node input) {
        Label label = new Label(name);
        label.getStyleClass().add("small-label");
        return new VBox(6, label, input);
    }

    /** A Text (not a Label), so Theme keeps the colour given here. */
    private static Text text(String value, double size, String font, Color colour) {
        Text t = new Text(value);
        t.setFont(Font.font(font, size));
        t.setFill(colour);
        return t;
    }

    private static Background fill(Color colour, double radius) {
        return new Background(new BackgroundFill(colour, new CornerRadii(radius), Insets.EMPTY));
    }

    private static Border line(Color colour, double radius) {
        return new Border(new BorderStroke(colour, BorderStrokeStyle.SOLID, new CornerRadii(radius),
                BorderWidths.DEFAULT));
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}