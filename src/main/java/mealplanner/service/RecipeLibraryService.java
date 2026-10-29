package mealplanner.service;

import java.util.List;
import mealplanner.data.RecipeRepository;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Recipe;

/**
 * Recipe library. F01 only needs getById() and search() (to pick a recipe for a slot).
 * save/remove/favourites/warnings come with F08 and F09.
 */
public class RecipeLibraryService {

    private final RecipeRepository recipeRepository;
    // TODO (F08): ingredientRepository, recipeCatalogService, nutritionService, profileService, eventBus

    public RecipeLibraryService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    /** Returns a recipe that is in the library (not archived), or throws InvalidInputException. */
    public Recipe getById(long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new InvalidInputException("Recipe " + id + " is not in the library"));
        if (recipe.isArchived()) {
            throw new InvalidInputException("Recipe " + recipe.getName() + " was removed from the library");
        }
        return recipe;
    }

    public List<Recipe> search(String query) {
        return recipeRepository.search(query).stream().filter(r -> !r.isArchived()).toList();
    }
}
