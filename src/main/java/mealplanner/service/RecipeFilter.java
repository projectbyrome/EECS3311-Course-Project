package mealplanner.service;

import mealplanner.domain.Recipe;

/** Strategy: one condition a catalogue recipe must meet to be recommended (F07). */
public interface RecipeFilter {

    boolean matches(Recipe recipe);

    /** Short text for Claude and the user, e.g. "category is Soup". */
    String describe();
}