package mealplanner.service;

import java.util.List;
import mealplanner.domain.DietaryRule;
import mealplanner.domain.Recipe;

/** The recipe breaks none of the profile's dietary rules (allergies and restrictions are strict). */
public class DietaryFilter implements RecipeFilter {

    private final List<DietaryRule> rules;

    public DietaryFilter(List<DietaryRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean matches(Recipe recipe) {
        return rules.stream().allMatch(rule -> rule.check(recipe).isEmpty());
    }

    @Override
    public String describe() {
        return "fits the profile's " + rules.size() + " dietary rule(s)";
    }
}