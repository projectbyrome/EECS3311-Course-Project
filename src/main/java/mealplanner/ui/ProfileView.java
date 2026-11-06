package mealplanner.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;
import mealplanner.events.AppEvent;
import mealplanner.events.EventListener;

/**
 * F06 GUI (Profile menu): tick dietary restrictions, type allergies and favourite categories, then Save.
 * The Daily goals card edits the F10 goals here too, as well as access summary.
 */
public class ProfileView extends VBox implements EventListener {

    private final AppFacade facade;
    private final Map<DietaryRestriction, CheckBox> restrictionBoxes = new EnumMap<>(DietaryRestriction.class);
    private final TextField allergiesField = new TextField();
    private final TextField categoriesField = new TextField();
    private final Label status = new Label();
    // F10 daily goals
    private final TextField calories = new TextField();
    private final TextField protein = new TextField();
    private final TextField carbs = new TextField();
    private final TextField fat = new TextField();
    private final TextField fiber = new TextField();
    private final TextField sodium = new TextField();
    private final Label goalStatus = new Label();
    private final Button daySummaryButton = new Button("Today's summary");
    private final Button weekSummaryButton = new Button("This week's summary");
    private Consumer<LocalDate> daySummaryHandler;
    private Consumer<LocalDate> weekSummaryHandler;

    public ProfileView(AppFacade facade) {
        this.facade = facade;
        setSpacing(16);

        FlowPane restrictions = new FlowPane(16, 10);
        for (DietaryRestriction r : DietaryRestriction.values()) {
            CheckBox box = new CheckBox(r.label());
            restrictionBoxes.put(r, box);
            restrictions.getChildren().add(box);
        }
        allergiesField.setPromptText("e.g. peanut, milk, shellfish (comma separated)");
        categoriesField.setPromptText("e.g. Soup, Spicy, Poultry, Stir-Fried (comma separated)");

        Button save = new Button("Save profile");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> onSave());
        status.getStyleClass().add("description");

        getChildren().addAll(
                card("Dietary restrictions", "Strict: recipes that break these are left out.", restrictions),
                card("Allergies", "Strict: an allergen such as peanut, or an ingredient name.", allergiesField),
                card("Favourite categories", "Preferences: the assistant suggests these first. "
                        + "Any category works, from any group (Cooking Method, Dish Type, Flavor, Main Ingredient) "
                        + "or one of your own.", categoriesField),
                save, status,
                goalsCard());
        load();
    }

    /** F10: the Nutrition Summary window is opened from here too (set by AppContext, like the calendar's buttons). */
    public void setSummaryHandlers(Consumer<LocalDate> forDay, Consumer<LocalDate> forWeek) {
        this.daySummaryHandler = forDay;
        this.weekSummaryHandler = forWeek;
        daySummaryButton.setVisible(forDay != null);
        weekSummaryButton.setVisible(forWeek != null);
    }

    /** UC10 5a: updateGoal(goal); values of 0 or less are shown here and nothing is saved. */
    public void onSaveGoals() {
        try {
            NutritionGoal goal = new NutritionGoal(number(calories, "Calories"), number(protein, "Protein"),
                    number(carbs, "Carbohydrates"), number(fat, "Fat"), number(fiber, "Fibre"), number(sodium, "Sodium"));
            ValidationReport report = facade.updateGoal(goal);
            if (report.isValid()) {
                goalStatus.setStyle("-fx-text-fill: #4CC38A;");
                goalStatus.setText("Daily goals saved.");
            } else {
                showGoalError(String.join("\n", report.getViolations()));
            }
        } catch (AppException ex) {
            showGoalError(ex.getMessage());
        }
    }

    /** Observer: goals saved in the Nutrition Summary window (ProfileChanged) show here too. */
    @Override
    public void onEvent(AppEvent event) {
        Platform.runLater(this::loadGoals);
    }

    private VBox goalsCard() {
        for (TextField f : List.of(calories, protein, carbs, fat, fiber, sodium)) f.setPrefColumnCount(6);
        HBox fields = new HBox(12, field("Calories (kcal, max)", calories), field("Protein (g, min)", protein),
                field("Carbohydrates (g, max)", carbs), field("Fat (g, max)", fat));
        HBox moreFields = new HBox(12, field("Fibre (g, min)", fiber), field("Sodium (mg, max)", sodium));
        Label zeroHint = new Label("Fibre and sodium: 0 means no goal.");
        zeroHint.getStyleClass().add("description");
        Button saveGoals = new Button("Save goals");
        saveGoals.getStyleClass().add("primary");
        saveGoals.setOnAction(e -> onSaveGoals());
        daySummaryButton.setOnAction(e -> { if (daySummaryHandler != null) daySummaryHandler.accept(LocalDate.now()); });
        weekSummaryButton.setOnAction(e -> { if (weekSummaryHandler != null) weekSummaryHandler.accept(
                LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))); });
        daySummaryButton.setVisible(false); // shown once AppContext sets the handlers
        weekSummaryButton.setVisible(false);
        HBox buttons = new HBox(10, saveGoals, daySummaryButton, weekSummaryButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        goalStatus.setWrapText(true);
        // the message line only takes room when it has something to say
        goalStatus.managedProperty().bind(goalStatus.textProperty().isNotEmpty());
        status.managedProperty().bind(status.textProperty().isNotEmpty());

        return card("Daily goals", "What you aim to eat each day. Used by your meal summaries "
                        + "and the Recommendations filter. Calories, protein, carbs and fat must be more than 0.",
                new VBox(12, fields, moreFields, zeroHint, buttons, goalStatus));
    }

    private static VBox field(String name, TextField input) {
        Label label = new Label(name);
        label.getStyleClass().add("small-label");
        return new VBox(6, label, input);
    }

    private static double number(TextField field, String label) {
        double v;
        try {
            v = Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be a number");
        }
        if (v < 0) throw new InvalidInputException(label + " must be greater than 0");
        return v;
    }

    private void showGoalError(String message) {
        goalStatus.setStyle("-fx-text-fill: #F2545B;");
        goalStatus.setText("Goals not saved:\n" + message);
    }

    private void loadGoals() {
        NutritionGoal goal = facade.getProfile().getDefaultGoal();
        calories.setText(String.format("%.0f", goal.getCalories()));
        protein.setText(String.format("%.0f", goal.getProteinG()));
        carbs.setText(String.format("%.0f", goal.getCarbsG()));
        fat.setText(String.format("%.0f", goal.getFatG()));
        fiber.setText(String.format("%.0f", goal.getFiberG()));
        sodium.setText(String.format("%.0f", goal.getSodiumMg()));
    }

    /** UC06: updateProfile(profile); on problems nothing is saved and they are shown with suggestions. */
    public void onSave() {
        UserProfile profile = facade.getProfile().copy();
        Set<DietaryRestriction> chosen = new LinkedHashSet<>();
        restrictionBoxes.forEach((r, box) -> { if (box.isSelected()) chosen.add(r); });
        profile.setRestrictions(chosen);
        profile.setAllergies(split(allergiesField.getText()));
        profile.setFavouriteCategories(split(categoriesField.getText()));
        try {
            ValidationReport report = facade.updateProfile(profile);
            if (report.isValid()) {
                status.setText("Profile saved.");
                load();
            } else {
                status.setText("");
                new Alert(Alert.AlertType.ERROR, "Nothing was saved:\n" + String.join("\n", report.getViolations()))
                        .showAndWait();
            }
        } catch (AppException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    /** A titled card with a short explanation above its content. */
    private static VBox card(String title, String hint, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");
        Label explanation = new Label(hint);
        explanation.getStyleClass().add("description");
        explanation.setWrapText(true);
        VBox card = new VBox(8, heading, explanation, content);
        card.getStyleClass().add("card");
        return card;
    }

    private void load() {
        UserProfile p = facade.getProfile();
        restrictionBoxes.forEach((r, box) -> box.setSelected(p.getRestrictions().contains(r)));
        allergiesField.setText(String.join(", ", p.getAllergies()));
        categoriesField.setText(String.join(", ", p.getFavouriteCategories()));
        loadGoals();
    }

    private static Set<String> split(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return set;
        Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        return set;
    }
}