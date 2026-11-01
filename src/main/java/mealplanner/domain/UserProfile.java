package mealplanner.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The user's dietary settings (F06) and daily nutrition goal (F10). Restrictions and allergies are strict
 * rules (rules(), violations()); favourite categories (e.g. Soup, Spicy, Poultry) are only
 * preferences for the AI.
 */
public class UserProfile {

    private Set<DietaryRestriction> restrictions = new TreeSet<>();
    private Set<String> allergies = new TreeSet<>();
    private Set<String> favouriteCategories = new LinkedHashSet<>();
    private NutritionGoal defaultGoal = NutritionGoal.DEFAULT;

    public UserProfile() {
    }

    public UserProfile(Set<DietaryRestriction> restrictions, Set<String> allergies, Set<String> favouriteCategories,
                       NutritionGoal defaultGoal) {
        this.restrictions = new TreeSet<>(restrictions);
        this.allergies = new TreeSet<>(allergies);
        this.favouriteCategories = new LinkedHashSet<>(favouriteCategories);
        this.defaultGoal = defaultGoal == null ? NutritionGoal.DEFAULT : defaultGoal;
    }

    /** One rule per allergy and per restriction (Strategy objects). */
    public List<DietaryRule> rules() {
        List<DietaryRule> rules = new ArrayList<>();
        for (String allergy : allergies) {
            rules.add(new AllergyRule(allergy));
        }
        for (DietaryRestriction r : restrictions) {
            rules.add(new RestrictionRule(r));
        }
        return rules;
    }

    /** Warnings for the recipe, e.g. "contains peanut"; empty when the recipe is safe for this profile. */
    public List<String> violations(Recipe recipe) {
        List<String> warnings = new ArrayList<>();
        for (DietaryRule rule : rules()) {
            rule.check(recipe).ifPresent(warnings::add);
        }
        return warnings;
    }

    public Set<DietaryRestriction> getRestrictions() { return Set.copyOf(restrictions); }
    public Set<String> getAllergies() { return Set.copyOf(allergies); }
    public Set<String> getFavouriteCategories() { return new LinkedHashSet<>(favouriteCategories); }
    public NutritionGoal getDefaultGoal() { return defaultGoal; }

    public void setRestrictions(Set<DietaryRestriction> restrictions) { this.restrictions = new TreeSet<>(restrictions); }
    public void setAllergies(Set<String> allergies) { this.allergies = new TreeSet<>(allergies); }
    public void setFavouriteCategories(Set<String> favouriteCategories) { this.favouriteCategories = new LinkedHashSet<>(favouriteCategories); }
    public void setDefaultGoal(NutritionGoal goal) { this.defaultGoal = goal; }

    /** A copy, so a form can be edited without changing the saved profile until Save. */
    public UserProfile copy() {
        return new UserProfile(restrictions, allergies, favouriteCategories, defaultGoal);
    }

    @Override
    public String toString() {
        return "Dietary restrictions: " + (restrictions.isEmpty() ? "none"
                : String.join(", ", restrictions.stream().map(DietaryRestriction::label).toList()))
                + "\nAllergies: " + (allergies.isEmpty() ? "none" : String.join(", ", allergies))
                + "\nFavourite categories: " + (favouriteCategories.isEmpty() ? "none" : String.join(", ", favouriteCategories))
                + "\nDaily goal: " + defaultGoal;
    }
}