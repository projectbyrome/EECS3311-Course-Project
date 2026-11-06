package mealplanner.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;

/**
 * F10 (SD07): one day's nutrition compared with the daily goal, and the short summary text (written by Claude)
 */
public class DaySummary {

    public static final String NO_MEALS = "No meals planned for this day.";

    public final LocalDate date;
    /** The planned meals that count, e.g. "LUNCH: Tomato Pasta x2.0 (520 kcal)". */
    public final List<String> meals;
    /** Null when no meals are planned. */
    public final NutritionSummary totals;
    public final NutritionGoal goal;
    public final boolean goalMet;
    /** The goals that are not met, e.g. "protein 32 g is under the 50 g goal". */
    public final List<String> misses;
    public final String text;

    public DaySummary(LocalDate date, List<String> meals, NutritionSummary totals, NutritionGoal goal,
                      boolean goalMet, List<String> misses, String text) {
        this.date = date;
        this.meals = List.copyOf(meals);
        this.totals = totals;
        this.goal = goal;
        this.goalMet = goalMet;
        this.misses = List.copyOf(misses);
        this.text = text;
    }

    /** UC10 1a: nothing to add up. */
    public static DaySummary noMeals(LocalDate date, NutritionGoal goal) {
        return new DaySummary(date, List.of(), null, goal, false, List.of(), NO_MEALS);
    }

    public boolean hasMeals() {
        return totals != null;
    }

    /** UC10 2a: some nutrition data is missing, so the real totals are higher. */
    public boolean isIncomplete() {
        return totals != null && !totals.isComplete();
    }

    /** One line per goal, e.g. "Calories: 1850 / 2000 kcal max (93%) OK". */
    public List<String> goalLines() {
        return totals == null ? List.of() : goalLines(totals, goal, misses);
    }

    /** Also used for the week (week totals against the daily goal times 7). */
    public static List<String> goalLines(NutritionSummary totals, NutritionGoal goal, List<String> misses) {
        List<String> lines = new ArrayList<>();
        lines.add(line("Calories", totals.getCalories(), goal.getCalories(), "kcal", "max", misses, "calories"));
        lines.add(line("Protein", totals.getProteinG(), goal.getProteinG(), "g", "min", misses, "protein"));
        lines.add(line("Carbohydrates", totals.getCarbsG(), goal.getCarbsG(), "g", "max", misses, "carbohydrates"));
        lines.add(line("Fat", totals.getFatG(), goal.getFatG(), "g", "max", misses, "fat"));
        lines.add(String.format("Sugar: %.1f g (no goal)", totals.getSugarG()));
        lines.add(optionalLine("Fibre", totals.getFiberG(), goal.getFiberG(), "g", "min", misses, "fibre",
                "about 25-38 g a day is recommended"));
        lines.add(optionalLine("Sodium", totals.getSodiumMg(), goal.getSodiumMg(), "mg", "max", misses, "sodium",
                "under about 2300 mg a day is recommended"));
        return lines;
    }

    /** Fibre and sodium: "n/a" while unknown, and the usual guide when no goal is set. */
    private static String optionalLine(String label, double actual, double target, String unit, String kind,
                                       List<String> misses, String key, String guide) {
        if (Double.isNaN(actual)) {
            return String.format("%s: n/a%s", label, target > 0 ? String.format(" (goal %s %.0f %s)", kind, target, unit) : "");
        }
        if (target <= 0) {
            return String.format("%s: %.0f %s (no goal; %s)", label, actual, unit, guide);
        }
        return line(label, actual, target, unit, kind, misses, key);
    }

    private static String line(String label, double actual, double target, String unit, String kind,
                               List<String> misses, String key) {
        if (target <= 0) {
            return String.format("%s: %.0f %s (no goal)", label, actual, unit);
        }
        boolean missed = misses.stream().anyMatch(m -> m.startsWith(key + " "));
        return String.format("%s: %.0f / %.0f %s %s (%.0f%%) %s", label, actual, target, unit, kind,
                actual * 100 / target, missed ? "NOT MET" : "OK");
    }

    /** Plain text for the command line and the tests. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Nutrition summary for ").append(date);
        if (!hasMeals()) {
            return sb.append("\n").append(text).toString();
        }
        sb.append("\nMeals:");
        meals.forEach(m -> sb.append("\n  ").append(m));
        sb.append("\nTotals compared with your daily goal:");
        goalLines().forEach(l -> sb.append("\n  ").append(l));
        totals.getVitamins().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("%n  %s: %.2f", e.getKey(), e.getValue())));
        sb.append(goalMet ? "\nAll goals met." : "\nGoals not met.");
        if (isIncomplete()) {
            sb.append("\n(incomplete: some nutrition data is missing, so the real totals are higher)");
            totals.getNotes().forEach(n -> sb.append("\n  Note: ").append(n));
        }
        sb.append("\nSummary: ").append(text);
        return sb.toString();
    }
}