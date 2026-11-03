package mealplanner.model;

import mealplanner.domain.Recipe;

/** One recipe card: a catalogue recipe and why it was picked (no reason when Claude was not used). */
public class Recommendation {

    public final Recipe recipe;
    public final String reason;

    public Recommendation(Recipe recipe, String reason) {
        this.recipe = recipe;
        this.reason = reason;
    }
}