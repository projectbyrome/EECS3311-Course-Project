package mealplanner.service;

import mealplanner.domain.Recipe;

/** The recipe takes at most maxMinutes to cook. */
public class CookTimeFilter implements RecipeFilter {

    private final int maxMinutes;

    public CookTimeFilter(int maxMinutes) {
        this.maxMinutes = maxMinutes;
    }

    @Override
    public boolean matches(Recipe recipe) {
        return recipe.getCookMinutes() <= maxMinutes;
    }

    @Override
    public String describe() {
        return "ready in at most " + maxMinutes + " minutes";
    }
}