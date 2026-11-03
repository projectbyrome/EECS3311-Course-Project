package mealplanner.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mealplanner.agent.workflows.MacroGenerator;
import mealplanner.data.DataAccessException;
import mealplanner.data.IngredientRepository;
import mealplanner.data.RecipeRepository;
import mealplanner.domain.Ingredient;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.InvalidRecipeException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
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
 * F07 adds favourites() and importFromCatalog(); F08 adds save() (SD03) and remove(); F09 toggleFavourite().
 */
public class RecipeLibraryService implements EventListener {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeCatalogService recipeCatalogService;
    private final NutritionService nutritionService;
    private final ProfileService profileService;
    private final EventBus eventBus;
    /** Set after construction (MacroGenerator itself needs this service); null means no USDA matching. */
    private MacroGenerator foodMatcher;

    /** Library without the catalogue, profile or events (used by the F01-F03 tests). */
    public RecipeLibraryService(RecipeRepository recipeRepository) {
        this(recipeRepository, null, null, null, null, null);
    }

    /** F06/F07 constructor, kept for the earlier tests; save() needs the full one. */
    public RecipeLibraryService(RecipeRepository recipeRepository, RecipeCatalogService recipeCatalogService,
                                ProfileService profileService, EventBus eventBus) {
        this(recipeRepository, null, recipeCatalogService, null, profileService, eventBus);
    }

    public RecipeLibraryService(RecipeRepository recipeRepository, IngredientRepository ingredientRepository,
                                RecipeCatalogService recipeCatalogService, NutritionService nutritionService,
                                ProfileService profileService, EventBus eventBus) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeCatalogService = recipeCatalogService;
        this.nutritionService = nutritionService;
        this.profileService = profileService;
        this.eventBus = eventBus;
    }

    /** F08: lets save() match unmatched ingredients to USDA foods the way the Macro Generator does (SD05). */
    public void setFoodMatcher(MacroGenerator foodMatcher) {
        this.foodMatcher = foodMatcher;
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

    /** F09 Favourites filter; also used by F07 so favourites rank higher. */
    public List<Recipe> favourites() {
        return recipeRepository.findAll().stream().filter(r -> r.isFavourite() && !r.isArchived()).toList();
    }

    /**
     * SD03 / UC08: adds a new recipe (id 0) or saves an edited one.
     * 1. every ingredient line must be a known ingredient (findByName) with an amount above 0 and a unit;
     *    otherwise InvalidRecipeException lists the problems by row and nothing is saved (4a);
     * 2. nutrition per serving from NutritionService (5a: marked incomplete when data is missing);
     * 3. allergy and diet warnings from UserProfile.violations();
     * 4. RecipeRepository.save(), then LibraryChanged.
     * An edited recipe keeps its favourite flag and where it came from.
     */
    public Recipe save(Recipe recipe) {
        Recipe existing = recipe.getId() == 0 ? null : getById(recipe.getId());
        List<RecipeIngredient> checked = validate(recipe);
        recipe.setIngredients(checked);
        if (existing != null) {
            recipe.setFavourite(existing.isFavourite());
            recipe.setSource(existing.getSource(), existing.getCatalogId());
        }

        NutritionSummary before = existing == null ? null : existing.getNutritionPerServing();
        if (before != null && before.isComplete() && sameIngredients(existing, recipe)) {
            recipe.setNutrition(before); // nothing to recalculate
        } else {
            recipe.setNutrition(nutritionPerServing(recipe));
        }
        recipe.setWarnings(profileService.getProfile().violations(recipe));
        recipeRepository.save(recipe);
        eventBus.publish(new LibraryChanged());
        return recipe;
    }

    /** SD03 "recipeNutrition(r, 1), lookup as in SD05"; 5a: missing data marks it incomplete. */
    private NutritionSummary nutritionPerServing(Recipe recipe) {
        List<String> notes = foodMatcher == null ? List.of()
                : foodMatcher.matchFoods(recipe.ingredients().stream().map(RecipeIngredient::getIngredient).toList());
        NutritionSummary n = nutritionService.recipeNutrition(recipe, 1);
        for (String note : notes) {
            n = n.withNote(note);
        }
        return n;
    }

    /** UC08 1a: the recipe leaves the library but stays in the database, so planned meals keep working. */
    public void remove(long id) {
        Recipe recipe = getById(id);
        recipe.archive();
        recipeRepository.save(recipe);
        eventBus.publish(new LibraryChanged());
    }

    /**
     * UC09: switches the favourite flag and saves it. If saving fails, the flag goes back (2a) and the
     * error is passed on so the screen can show it. Returns the new value.
     */
    public boolean toggleFavourite(long id) {
        Recipe recipe = getById(id);
        boolean before = recipe.isFavourite();
        recipe.setFavourite(!before);
        try {
            recipeRepository.save(recipe);
        } catch (DataAccessException e) {
            recipe.setFavourite(before);
            throw e;
        }
        eventBus.publish(new LibraryChanged());
        return !before;
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

    /** SD03 loop over the ingredient lines; returns the lines with the real ingredients, or throws. */
    private List<RecipeIngredient> validate(Recipe recipe) {
        List<String> problems = new ArrayList<>();
        Map<Integer, String> rowProblems = new LinkedHashMap<>();
        String name = recipe.getName() == null ? "" : recipe.getName().trim();
        if (name.isEmpty()) {
            problems.add("The recipe needs a name");
        } else {
            for (Recipe other : search(name)) {
                if (other.getId() != recipe.getId() && other.getName().equalsIgnoreCase(name)) {
                    problems.add("There is already a recipe called " + other.getName());
                }
            }
        }
        if (recipe.getCookMinutes() < 0) {
            problems.add("Cooking time cannot be negative");
        }
        List<RecipeIngredient> lines = recipe.ingredients();
        if (lines.isEmpty()) {
            problems.add("Add at least one ingredient");
        }
        List<RecipeIngredient> checked = new ArrayList<>();
        for (int row = 0; row < lines.size(); row++) {
            RecipeIngredient line = lines.get(row);
            String typed = line.getIngredient().getName() == null ? "" : line.getIngredient().getName().trim();
            String label = "Row " + (row + 1) + (typed.isEmpty() ? "" : " (" + typed + ")");
            String problem = null;
            Optional<Ingredient> known = typed.isEmpty() ? Optional.empty() : ingredientRepository.findByName(typed);
            Quantity q = line.getQuantity();
            if (typed.isEmpty()) {
                problem = label + ": the ingredient name is missing";
            } else if (known.isEmpty()) {
                List<String> close = ProfileService.suggestions(typed.toLowerCase(Locale.ROOT), knownIngredientNames());
                problem = label + ": unknown ingredient" + (close.isEmpty() ? "" : ". Did you mean: "
                        + String.join(", ", close) + "?");
            } else if (q == null) {
                problem = label + ": needs an amount and a unit";
            } else if (q.getAmount() <= 0) {
                problem = label + ": the amount must be more than 0";
            }
            if (problem != null) {
                problems.add(problem);
                rowProblems.put(row, problem);
            } else {
                checked.add(new RecipeIngredient(known.get(), q));
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidRecipeException(problems, rowProblems);
        }
        return checked;
    }

    private Set<String> knownIngredientNames() {
        Set<String> names = new TreeSet<>();
        ingredientRepository.search("").forEach(i -> names.add(i.getName().toLowerCase(Locale.ROOT)));
        return names;
    }

    private static boolean sameIngredients(Recipe a, Recipe b) {
        if (a.getServings() != b.getServings() || a.ingredients().size() != b.ingredients().size()) {
            return false;
        }
        for (int i = 0; i < a.ingredients().size(); i++) {
            RecipeIngredient x = a.ingredients().get(i);
            RecipeIngredient y = b.ingredients().get(i);
            if (x.getIngredient().getId() != y.getIngredient().getId() || !x.getQuantity().equals(y.getQuantity())) {
                return false;
            }
        }
        return true;
    }
}
