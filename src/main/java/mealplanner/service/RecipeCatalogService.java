package mealplanner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import mealplanner.data.CatalogRepository;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.UserProfile;
import mealplanner.model.RecommendationFilter;

/** Searches the built-in recipe catalogue with RecipeFilter strategies (F07). */
public class RecipeCatalogService {

    private final CatalogRepository catalogRepository;
    private final NutritionService nutritionService;

    public RecipeCatalogService(CatalogRepository catalogRepository, NutritionService nutritionService) {
        this.catalogRepository = catalogRepository;
        this.nutritionService = nutritionService;
    }

    /** Catalogue recipes that pass every filter, in catalogue order. */
    public List<Recipe> search(List<RecipeFilter> filters) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : catalogRepository.findAll()) {
            if (filters.stream().allMatch(f -> f.matches(recipe))) {
                result.add(recipe);
            }
        }
        return result;
    }

    public Recipe getById(long id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new InvalidInputException("Recipe " + id + " is not in the catalogue"));
    }

    /**
     * Turns the user's filter into strategies. The dietary filter from the profile is always added,
     * so no search can return a recipe that breaks an allergy or restriction.
     */
    public List<RecipeFilter> buildFilters(RecommendationFilter filter, UserProfile profile) {
        List<RecipeFilter> filters = new ArrayList<>();
        if (filter != null) {
            if (filter.category != null && !filter.category.isBlank() && !filter.category.equalsIgnoreCase("any")) {
                filters.add(new CategoryFilter(filter.category));
            }
            if (filter.maxCookMinutes != null) {
                if (filter.maxCookMinutes <= 0) {
                    throw new InvalidInputException("Cooking time must be greater than 0 minutes");
                }
                filters.add(new CookTimeFilter(filter.maxCookMinutes));
            }
            if (filter.goal != null && !filter.goal.isEmpty()) {
                filters.add(new NutritionGoalFilter(filter.goal, nutritionService));
            }
        }
        if (profile != null && !profile.rules().isEmpty()) {
            filters.add(new DietaryFilter(profile.rules()));
        }
        return filters;
    }

    /** Every category name used in the catalogue, A to Z, for the filter form. */
    public List<String> categories() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Recipe r : catalogRepository.findAll()) {
            for (RecipeCategory c : r.getCategories()) {
                set.add(c.getName());
            }
        }
        return List.copyOf(set);
    }
}