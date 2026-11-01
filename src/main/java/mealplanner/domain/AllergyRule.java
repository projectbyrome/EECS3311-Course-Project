package mealplanner.domain;

import java.util.List;
import java.util.Optional;

/**
 * The recipe must not contain the allergen. An allergy can be an allergen tag ("peanut", "milk") or an
 * ingredient name ("banana"); an ingredient matches if its name or one of its tags is the allergen.
 */
public class AllergyRule implements DietaryRule {

    private final String allergen;

    public AllergyRule(String allergen) {
        this.allergen = allergen.trim().toLowerCase();
    }

    @Override
    public Optional<String> check(Recipe recipe) {
        List<String> hits = recipe.ingredients().stream()
                .map(RecipeIngredient::getIngredient)
                .filter(i -> i.getName().equalsIgnoreCase(allergen)
                        || i.getAllergenTags().stream().anyMatch(t -> t.equalsIgnoreCase(allergen)))
                .map(Ingredient::getName)
                .distinct()
                .toList();
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        boolean onlyItself = hits.size() == 1 && hits.get(0).equalsIgnoreCase(allergen);
        return Optional.of("contains " + allergen + (onlyItself ? "" : " (" + String.join(", ", hits) + ")"));
    }

    public String getAllergen() {
        return allergen;
    }
}