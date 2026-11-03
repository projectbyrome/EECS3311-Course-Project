package mealplanner.service;

import java.util.List;
import java.util.Optional;
import mealplanner.data.RecipeRepository;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.RecipeSource;
import mealplanner.domain.UserProfile;
import mealplanner.events.AppEvent;
import mealplanner.events.EventBus;
import mealplanner.events.EventListener;
import mealplanner.events.LibraryChanged;
import mealplanner.events.ProfileChanged;

/**
 * Recipe library. F01 needs getById() and search(); F06 adds refreshWarnings() (Observer of ProfileChanged);
 * F07 adds favourites() and importFromCatalog(). save/remove/toggleFavourite come with F08 and F09.
 */
public class RecipeLibraryService implements EventListener {

    private final RecipeRepository recipeRepository;
    private final RecipeCatalogService recipeCatalogService;
    private final ProfileService profileService;
    private final EventBus eventBus;
    // TODO (F08): ingredientRepository, nutritionService

    /** Library without the catalogue, profile or events (used by the F01-F03 tests). */
    public RecipeLibraryService(RecipeRepository recipeRepository) {
        this(recipeRepository, null, null, null);
    }

    public RecipeLibraryService(RecipeRepository recipeRepository, RecipeCatalogService recipeCatalogService,
                                ProfileService profileService, EventBus eventBus) {
        this.recipeRepository = recipeRepository;
        this.recipeCatalogService = recipeCatalogService;
        this.profileService = profileService;
        this.eventBus = eventBus;
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

    /** F09 filter, used by F07 so favourites rank higher. */
    public List<Recipe> favourites() {
        return recipeRepository.findAll().stream().filter(r -> r.isFavourite() && !r.isArchived()).toList();
    }

    /**
     * SD06 last step: copies an approved catalogue recipe into the library, with the profile's warnings.
     * If the library already holds this catalogue recipe, that copy is returned instead of a duplicate.
     */
    public Recipe importFromCatalog(long catalogId) {
        Optional<Recipe> existing = recipeRepository.findAll().stream()
                .filter(r -> !r.isArchived() && r.getCatalogId() != null && r.getCatalogId() == catalogId)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Recipe source = recipeCatalogService.getById(catalogId);
        Recipe copy = new Recipe(0, source.getName(), source.getServings());
        copy.setCategories(source.getCategories());
        copy.setCookMinutes(source.getCookMinutes());
        copy.getInstructions().addAll(source.getInstructions());
        for (RecipeIngredient ri : source.ingredients()) {
            copy.addIngredient(new RecipeIngredient(ri.getIngredient(), ri.getQuantity()));
        }
        copy.setNutrition(source.getNutritionPerServing());
        copy.setSource(RecipeSource.CATALOG, catalogId);
        copy.setWarnings(profileService.getProfile().violations(copy));
        recipeRepository.save(copy);
        eventBus.publish(new LibraryChanged());
        return copy;
    }

    /** UC06 step 5: re-checks every recipe in the library against the profile and saves changed warnings. */
    public void refreshWarnings() {
        UserProfile profile = profileService.getProfile();
        boolean changed = false;
        for (Recipe recipe : recipeRepository.findAll()) {
            if (recipe.isArchived()) continue;
            List<String> warnings = profile.violations(recipe);
            if (!warnings.equals(recipe.getWarnings())) {
                recipe.setWarnings(warnings);
                recipeRepository.save(recipe);
                changed = true;
            }
        }
        if (changed) {
            eventBus.publish(new LibraryChanged());
        }
    }

    /** Observer: a saved profile changes the allergy and diet warnings. */
    @Override
    public void onEvent(AppEvent event) {
        if (event instanceof ProfileChanged) {
            refreshWarnings();
        }
    }
}