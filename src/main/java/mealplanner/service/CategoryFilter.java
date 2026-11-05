package mealplanner.service;

import mealplanner.domain.CategoryGroup;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;

/**
 * The recipe has the category asked for ("Soup", "Curry", "Poultry"), ignoring case.
 * Flavor categories are mainly use as a description
 */
public class CategoryFilter implements RecipeFilter {

    private final String category;

    public CategoryFilter(String category) {
        this.category = category.trim();
    }

    @Override
    public boolean matches(Recipe recipe) {
        for (RecipeCategory c : recipe.getCategories()) {
            if (c.getGroup() != CategoryGroup.FLAVOR && c.getName().equalsIgnoreCase(category)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "category is " + category;
    }
}