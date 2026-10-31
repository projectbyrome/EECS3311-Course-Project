package mealplanner.model;

/** A recipe the user picked for the shopping list, and how many servings to buy for (F03). */
public class RecipeSelection {

    public long recipeId;
    public double servings;

    public RecipeSelection() {
    }

    public RecipeSelection(long recipeId, double servings) {
        this.recipeId = recipeId;
        this.servings = servings;
    }
}