package mealplanner.service;

import mealplanner.domain.Recipe;

/** The recipe has the category asked for, in any group ("Soup", "Spicy", "Poultry"), ignoring case. */
public class CategoryFilter implements RecipeFilter {

    private final String category;

    public CategoryFilter(String category) {
        this.category = category.trim();
    }

    @Override
    public boolean matches(Recipe recipe) {
        return recipe.hasCategory(category);
    }

    @Override
    public String describe() {
        return "category is " + category;
    }
}