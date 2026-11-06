package mealplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * F07 extra: a recipe Claude wrote itself because the catalogue had no good fit, as read from its JSON answer.
 * WrittenRecipeConverter turns it into a Recipe, so the app can check it like any other recipe.
 */
public class WrittenRecipe {

    public String name;
    public int servings;
    public int cookMinutes;
    public List<String> categories = new ArrayList<>();
    public List<Line> ingredients = new ArrayList<>();
    public List<String> steps = new ArrayList<>();
    public Estimate nutritionPerServing;

    public static class Line {
        public String name;
        public double amount;
        public String unit;
        public List<String> allergens = new ArrayList<>();
        public double gramsPerPiece;
    }

    public static class Estimate {
        public double calories;
        public double proteinG;
        public double carbsG;
        public double fatG;
        /** Null if Claude left it out. */
        public Double sodiumMg;
        public Double fiberG;
    }
}