package mealplanner.domain;

import java.util.Optional;

/** Strategy: one dietary rule from the profile. Returns a warning when the recipe breaks it. */
public interface DietaryRule {

    Optional<String> check(Recipe recipe);
}