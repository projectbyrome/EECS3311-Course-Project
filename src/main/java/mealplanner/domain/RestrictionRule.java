package mealplanner.domain;

import java.util.List;
import java.util.Optional;

/** The recipe must fit a dietary restriction such as VEGETARIAN; each ingredient is checked with allows(). */
public class RestrictionRule implements DietaryRule {

    private final DietaryRestriction restriction;

    public RestrictionRule(DietaryRestriction restriction) {
        this.restriction = restriction;
    }

    @Override
    public Optional<String> check(Recipe recipe) {
        List<String> breaking = recipe.ingredients().stream()
                .map(RecipeIngredient::getIngredient)
                .filter(i -> !restriction.allows(i))
                .map(Ingredient::getName)
                .distinct()
                .toList();
        if (breaking.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("not " + restriction.label() + " (" + String.join(", ", breaking) + ")");
    }

    public DietaryRestriction getRestriction() {
        return restriction;
    }
}