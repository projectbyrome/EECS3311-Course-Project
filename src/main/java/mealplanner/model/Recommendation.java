package mealplanner.model;

import mealplanner.domain.Recipe;

/**
 * One recipe card: the recipe and why it was picked (no reason when Claude was not used).
 * written is true for a recipe Claude wrote itself
 */
public class Recommendation {

    public final Recipe recipe;
    public final String reason;
    public final boolean written;

    public Recommendation(Recipe recipe, String reason) {
        this(recipe, reason, false);
    }

    public Recommendation(Recipe recipe, String reason, boolean written) {
        this.recipe = recipe;
        this.reason = reason;
        this.written = written;
    }
}