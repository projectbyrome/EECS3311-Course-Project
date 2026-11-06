package mealplanner.domain;

/**
 * Value object: daily goals for calories, protein, carbohydrates, fat, fibre and sodium. Immutable.
 * A value of 0 means "no goal" for that nutrient. Protein and fibre are minimums; calories, carbohydrates,
 * fat and sodium are maximums. A sodium or fibre total that is unknown (NaN) is not checked. Used by the profile (F06), the nutrition-goal filter (F07) and the daily summary (F10).
 */
public final class NutritionGoal {

    /** Default daily goal (the reference daily values on Canadian and US food labels). */
    public static final NutritionGoal DEFAULT = new NutritionGoal(2000, 50, 275, 78, 28, 2300);

    public static final double DAILY_TOLERANCE_PCT = 10;

    private final double calories;
    private final double proteinG;
    private final double carbsG;
    private final double fatG;
    private final double fiberG;
    private final double sodiumMg;

    public NutritionGoal(double calories, double proteinG, double carbsG, double fatG) {
        this(calories, proteinG, carbsG, fatG, 0, 0);
    }

    public NutritionGoal(double calories, double proteinG, double carbsG, double fatG, double fiberG, double sodiumMg) {
        for (double v : new double[] {calories, proteinG, carbsG, fatG, fiberG, sodiumMg}) {
            if (!(v >= 0) || Double.isInfinite(v)) {
                throw new InvalidInputException("Nutrition goals cannot be negative");
            }
        }
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.fiberG = fiberG;
        this.sodiumMg = sodiumMg;
    }

    /** Preset goals for the recommendation filter (F07): "high-protein" or "low-calorie". */
    public static NutritionGoal preset(String name) {
        String n = name == null ? "" : name.trim().toLowerCase().replace(' ', '-').replace('_', '-');
        return switch (n) {
            case "high-protein" -> new NutritionGoal(0, 120, 0, 0);
            case "low-calorie" -> new NutritionGoal(1500, 0, 0, 0);
            case "low-carb" -> new NutritionGoal(0, 0, 130, 0);
            default -> throw new InvalidInputException(
                    "Unknown nutrition goal '" + name + "'. Choose high-protein, low-calorie, low-carb or profile");
        };
    }

    /** The goal for part of a day, e.g. one meal = times(0.33). */
    public NutritionGoal times(double factor) {
        return new NutritionGoal(calories * factor, proteinG * factor, carbsG * factor, fatG * factor,
                fiberG * factor, sodiumMg * factor);
    }

    /**
     * True if every goal that is set is met, allowing tolerancePct percent either way: protein must reach
     * goal * (1 - tol), calories, carbohydrates and fat must stay under goal * (1 + tol).
     */
    public boolean isMet(NutritionSummary actual, double tolerancePct) {
        return misses(actual, tolerancePct).isEmpty();
    }

    /**
     * The goals that are not met (same rule as isMet), each as a sentence starting with the nutrient,
     * e.g. "protein 32 g is under the 50 g goal". Empty when every goal is met (F10).
     */
    public java.util.List<String> misses(NutritionSummary actual, double tolerancePct) {
        double tol = tolerancePct / 100.0;
        java.util.List<String> misses = new java.util.ArrayList<>();
        if (calories > 0 && actual.getCalories() > calories * (1 + tol)) {
            misses.add(String.format("calories %.0f kcal are over the %.0f kcal goal", actual.getCalories(), calories));
        }
        if (proteinG > 0 && actual.getProteinG() < proteinG * (1 - tol)) {
            misses.add(String.format("protein %.0f g is under the %.0f g goal", actual.getProteinG(), proteinG));
        }
        if (carbsG > 0 && actual.getCarbsG() > carbsG * (1 + tol)) {
            misses.add(String.format("carbohydrates %.0f g are over the %.0f g goal", actual.getCarbsG(), carbsG));
        }
        if (fatG > 0 && actual.getFatG() > fatG * (1 + tol)) {
            misses.add(String.format("fat %.0f g is over the %.0f g goal", actual.getFatG(), fatG));
        }
        if (fiberG > 0 && !Double.isNaN(actual.getFiberG()) && actual.getFiberG() < fiberG * (1 - tol)) {
            misses.add(String.format("fibre %.0f g is under the %.0f g goal", actual.getFiberG(), fiberG));
        }
        if (sodiumMg > 0 && !Double.isNaN(actual.getSodiumMg()) && actual.getSodiumMg() > sodiumMg * (1 + tol)) {
            misses.add(String.format("sodium %.0f mg is over the %.0f mg goal", actual.getSodiumMg(), sodiumMg));
        }
        return misses;
    }

    public boolean isEmpty() {
        return calories == 0 && proteinG == 0 && carbsG == 0 && fatG == 0 && fiberG == 0 && sodiumMg == 0;
    }

    public double getCalories() { return calories; }
    public double getProteinG() { return proteinG; }
    public double getCarbsG() { return carbsG; }
    public double getFatG() { return fatG; }
    public double getFiberG() { return fiberG; }
    public double getSodiumMg() { return sodiumMg; }

    @Override
    public boolean equals(java.lang.Object o) {
        return o instanceof NutritionGoal g && g.calories == calories && g.proteinG == proteinG
                && g.carbsG == carbsG && g.fatG == fatG && g.fiberG == fiberG && g.sodiumMg == sodiumMg;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(calories, proteinG, carbsG, fatG, fiberG, sodiumMg);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (calories > 0) sb.append(String.format("max %.0f kcal, ", calories));
        if (proteinG > 0) sb.append(String.format("min %.0f g protein, ", proteinG));
        if (carbsG > 0) sb.append(String.format("max %.0f g carbs, ", carbsG));
        if (fatG > 0) sb.append(String.format("max %.0f g fat, ", fatG));
        if (fiberG > 0) sb.append(String.format("min %.0f g fibre, ", fiberG));
        if (sodiumMg > 0) sb.append(String.format("max %.0f mg sodium, ", sodiumMg));
        return sb.length() == 0 ? "no goal" : sb.substring(0, sb.length() - 2);
    }
}