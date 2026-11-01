package mealplanner.model;

/** A recipe from the library and how many servings of it (F05). */
public class RecipePortion extends NutritionSelection {

    public long recipeId;
    public double servings;

    public RecipePortion(long recipeId, double servings) {
        this.recipeId = recipeId;
        this.servings = servings;
    }

    @Override
    public String describe() {
        return "recipe " + recipeId + " x" + servings;
    }
}