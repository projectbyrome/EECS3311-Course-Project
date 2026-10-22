package mealplanner.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NutritionSummary {

    private final double calories;
    private final double carbsG;
    private final double proteinG;
    private final double fatG;
    private final double sugarG;
    private final Map<String, Double> vitamins;
    private final boolean complete;
    private final List<String> notes;

    public NutritionSummary(double calories, double carbsG, double proteinG, double fatG, double sugarG,
                            Map<String, Double> vitamins, boolean complete, List<String> notes) {
        this.calories = calories;
        this.carbsG = carbsG;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.sugarG = sugarG;
        this.vitamins = Map.copyOf(vitamins);
        this.complete = complete;
        this.notes = List.copyOf(notes);
    }

    public static NutritionSummary zero() {
        return new NutritionSummary(0, 0, 0, 0, 0, Map.of(), true, List.of());
    }

    public static NutritionSummary incomplete(String reason) {
        return new NutritionSummary(0, 0, 0, 0, 0, Map.of(), false, List.of(reason));
    }

    public NutritionSummary plus(NutritionSummary other) {
        Map<String, Double> v = new LinkedHashMap<>(vitamins);
        other.vitamins.forEach((k, amount) -> v.merge(k, amount, Double::sum));
        List<String> n = new ArrayList<>(notes);
        for (String note : other.notes) {
            if (!n.contains(note)) n.add(note);
        }
        return new NutritionSummary(calories + other.calories, carbsG + other.carbsG, proteinG + other.proteinG,
                fatG + other.fatG, sugarG + other.sugarG, v, complete && other.complete, n);
    }

    public NutritionSummary times(double factor) {
        Map<String, Double> v = new LinkedHashMap<>();
        vitamins.forEach((k, amount) -> v.put(k, amount * factor));
        return new NutritionSummary(calories * factor, carbsG * factor, proteinG * factor, fatG * factor,
                sugarG * factor, v, complete, notes);
    }

    public NutritionSummary withNote(String note) {
        List<String> n = new ArrayList<>(notes);
        if (!n.contains(note)) n.add(note);
        return new NutritionSummary(calories, carbsG, proteinG, fatG, sugarG, vitamins, complete, n);
    }

    public double getCalories() {
        return calories;
    }
    public double getCarbsG() {
        return carbsG;
    }
    public double getProteinG() {
        return proteinG;
    }
    public double getFatG() {
        return fatG;
    }
    public double getSugarG() {
        return sugarG;
    }
    public Map<String, Double> getVitamins() {
        return vitamins;
    }
    public boolean isComplete() {
        return complete;
    }
    public List<String> getNotes() {
        return notes;
    }

    public String shortLine() {
        return String.format("%.0f kcal, %.1f g protein, %.1f g carbs, %.1f g fat, %.1f g sugar%s",
                calories, proteinG, carbsG, fatG, sugarG, complete ? "" : " (Incomplete: Missing Values)");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Calories: %.0f kcal%n", calories));
        sb.append(String.format("Carbohydrates: %.1f g%n", carbsG));
        sb.append(String.format("Protein: %.1f g%n", proteinG));
        sb.append(String.format("Fat: %.1f g%n", fatG));
        sb.append(String.format("Sugar: %.1f g", sugarG));
        vitamins.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("%n%s: %.2f", e.getKey(), e.getValue())));
        if (!complete) {
            sb.append(String.format("%n(incomplete: some values are missing)"));
        }
        for (String note : notes) {
            sb.append(String.format("%nNote: %s", note));
        }
        return sb.toString();
    }
}