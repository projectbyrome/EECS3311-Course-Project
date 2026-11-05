package mealplanner.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import mealplanner.app.AppFacade;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.PantryItem;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

/**
 * The Home page ("Today"): today's meals, today's daily goals, and a tile for each feature.
 * Clicking a tile opens that page in the side menu. Listens for calendar, pantry, profile and checklist
 * changes so the numbers stay current. Colours come from Theme (style classes) and the slot colours of the calendar.
 */
public class HomeView extends ScrollPane implements EventListener {

    private static final DateTimeFormatter TODAY_DATE = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final Color EMPTY_DOT = Color.web("#3A404B");

    private final AppFacade facade;
    private final VBox todayCard = new VBox(10);
    private final GridPane tiles = new GridPane();
    private Consumer<String> openPage = name -> { };
    private Consumer<LocalDate> daySummaryHandler = date -> { };

    public HomeView(AppFacade facade) {
        this.facade = facade;

        todayCard.getStyleClass().add("card");
        todayCard.setMinWidth(300);
        todayCard.setPrefWidth(320);

        tiles.setHgap(12);
        tiles.setVgap(12);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100 / 3.0);
            tiles.getColumnConstraints().add(column);
        }
        HBox.setHgrow(tiles, Priority.ALWAYS);
        HBox top = new HBox(14, todayCard, tiles);

        VBox content = new VBox(14, top);
        content.setPadding(new Insets(0, 4, 4, 0));
        setContent(content);
        setFitToWidth(true);
        refresh();
    }

    /** MainWindow passes a way to open a page by its menu name, e.g. "Pantry". */
    public void setPageOpener(Consumer<String> opener) {
        this.openPage = opener;
    }

    /** The "Today's nutrition" button opens the Nutrition Summary for today (same window as the calendar's). */
    public void setSummaryHandler(Consumer<LocalDate> forDay) {
        this.daySummaryHandler = forDay;
    }

    /** Observer: a meal, pantry item, goal or checklist changed somewhere else. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::refresh);
    }

    public void refresh() {
        LocalDate today = LocalDate.now();
        DayPlan todayPlan = facade.getDays(today, 1).stream().findFirst().orElse(new DayPlan(today));
        drawToday(todayPlan);
        drawTiles();
    }

    private void drawToday(DayPlan day) {
        Label title = new Label("Today");
        title.getStyleClass().add("section-title");
        long planned = day.activeEntries().size();
        Label count = new Label(planned == 0 ? "No meals planned"
                : planned == 1 ? "1 meal planned" : planned + " meals planned");
        count.getStyleClass().add("small-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label date = new Label(LocalDate.now().format(TODAY_DATE));
        date.getStyleClass().add("description");
        HBox titleAndDate = new HBox(8, title, date);
        titleAndDate.setAlignment(Pos.BASELINE_LEFT);
        HBox header = new HBox(titleAndDate, spacer, count);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox meals = new VBox(0);
        for (MealSlot slot : MealSlot.values()) {
            List<MealEntry> inSlot = day.entriesIn(slot).stream().filter(e -> !e.isSkipped())
                    .sorted(Comparator.comparing(MealEntry::getId)).toList();
            meals.getChildren().add(slotRow(slot, inSlot));
        }

        Button addMeal = new Button("+  Add a meal");
        addMeal.getStyleClass().add("primary");
        addMeal.setMaxWidth(Double.MAX_VALUE);
        addMeal.setOnAction(e -> openPage.accept("Meal Calendar"));

        NutritionGoal goal = facade.getProfile().getDefaultGoal();
        Label goalTitle = new Label("Today's goals");
        goalTitle.getStyleClass().add("card-title");
        Label goals = new Label(String.format("%.0f kcal%n%.0f g protein  ·  %.0f g carbs  ·  %.0f g fat",
                goal.getCalories(), goal.getProteinG(), goal.getCarbsG(), goal.getFatG()));
        goals.getStyleClass().add("description");
        goals.setWrapText(true);
        Button summary = new Button("Today's nutrition");
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setOnAction(e -> daySummaryHandler.accept(LocalDate.now()));
        VBox goalBox = new VBox(6, goalTitle, goals, summary);
        goalBox.setPadding(new Insets(6, 0, 0, 0));

        todayCard.getChildren().setAll(header, meals, addMeal, goalBox);
    }

    private VBox slotRow(MealSlot slot, List<MealEntry> entries) {
        Label slotName = new Label(slotName(slot));
        slotName.getStyleClass().add("small-label");
        VBox lines = new VBox(2, slotName);
        if (entries.isEmpty()) {
            Label nothing = new Label("Nothing planned");
            nothing.getStyleClass().add("description");
            lines.getChildren().add(nothing);
        }
        for (MealEntry entry : entries) {
            Label recipe = new Label(entry.getRecipe().getName());
            recipe.getStyleClass().add("card-title");
            lines.getChildren().add(recipe);
            if (entry.isMissingIngredients()) {
                Label missing = new Label("Missing ingredients in the pantry");
                missing.getStyleClass().addAll("meal", "missing");
                lines.getChildren().add(missing);
            }
        }
        Circle dot = new Circle(4, entries.isEmpty() ? EMPTY_DOT : dotColour(slot));
        VBox dotBox = new VBox(dot);
        dotBox.setPadding(new Insets(5, 0, 0, 0));
        HBox row = new HBox(10, dotBox, lines);
        VBox wrapper = new VBox(row);
        wrapper.setPadding(new Insets(9, 0, 9, 0));
        wrapper.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1, 0, 0, 0))));
        return wrapper;
    }

    private void drawTiles() {
        List<PantryItem> pantry = facade.getPantry();
        long outOfStock = pantry.stream().filter(p -> p.getQuantity().getAmount() < 0.005).count();
        int lists = facade.getChecklists().size();
        int recipes = facade.searchLibrary("").size();

        tiles.getChildren().clear();
        addTile(0, 0, "Meal Calendar", "Plan your month");
        addTile(1, 0, "Pantry", outOfStock == 0 ? "Nothing out of stock"
                : outOfStock == 1 ? "1 item out of stock" : outOfStock + " items out of stock");
        addTile(2, 0, "Shopping", lists == 0 ? "No shopping lists yet"
                : lists == 1 ? "1 shopping list" : lists + " shopping lists");
        addTile(0, 1, "Recipe Library", recipes == 1 ? "1 recipe saved" : recipes + " recipes saved");
        addTile(1, 1, "Macro Generator", "Work out your macros");
        addTile(2, 1, "Recommendations", "Find what to make next");
    }

    /** A feature tile: a big button that opens the page with the same name in the side menu. */
    private void addTile(int column, int row, String page, String subtitle) {
        Region accent = new Region();
        accent.setMinSize(28, 4);
        accent.setMaxSize(28, 4);
        accent.setBackground(new Background(new BackgroundFill(Theme.ACCENT, new CornerRadii(2), Insets.EMPTY)));
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        Label name = new Label(page);
        name.getStyleClass().add("section-title");
        Label description = new Label(subtitle);
        description.getStyleClass().add("description");
        VBox inside = new VBox(6, accent, grow, name, description);
        inside.setPadding(new Insets(8, 6, 6, 6));
        inside.setMinHeight(90);
        inside.setAlignment(Pos.TOP_LEFT);

        Button tile = new Button();
        tile.setGraphic(inside);
        tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tile.setAlignment(Pos.TOP_LEFT);
        tile.setCursor(Cursor.HAND);
        tile.setOnAction(e -> openPage.accept(page));
        GridPane.setHgrow(tile, Priority.ALWAYS);
        GridPane.setVgrow(tile, Priority.ALWAYS);
        tiles.add(tile, column, row);
    }

    /** Same slot colours as the Meal Calendar. */
    private static Color dotColour(MealSlot slot) {
        return switch (slot) {
            case BREAKFAST -> Color.web("#F5A524");
            case LUNCH -> Color.web("#34C38F");
            case DINNER -> Color.web("#3478F6");
            case SNACK -> Color.web("#A78BFA");
        };
    }

    private static String slotName(MealSlot slot) {
        String n = slot.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}