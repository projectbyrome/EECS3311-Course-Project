package mealplanner.app;

import java.time.LocalDate;
import java.util.List;
import mealplanner.agent.workflows.MacroGenerator;
import mealplanner.agent.workflows.RecipeRecommender;
import mealplanner.agent.workflows.ShoppingListGenerator;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealSlot;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;
import mealplanner.model.FinishResult;
import mealplanner.model.NutritionSelection;
import mealplanner.model.RecipeSelection;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationResult;
import mealplanner.model.ShoppingDraft;
import mealplanner.service.ChecklistService;
import mealplanner.service.MealCalendarService;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeLibraryService;

/**
 * Facade: the one entry point used by both the GUI views and the CLI commands.
 * One group of methods per feature.
 */
public class AppFacade {

    private final MealCalendarService mealCalendarService;
    private final RecipeLibraryService recipeLibraryService;
    private final PantryService pantryService;
    private final ChecklistService checklistService;
    private final ShoppingListGenerator shoppingListGenerator;
    private final MacroGenerator macroGenerator;
    private final ProfileService profileService;
    private final RecipeRecommender recipeRecommender;
    private final RecipeCatalogService recipeCatalogService;

    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService,
                     PantryService pantryService, ChecklistService checklistService,
                     ShoppingListGenerator shoppingListGenerator, MacroGenerator macroGenerator,
                     ProfileService profileService, RecipeRecommender recipeRecommender,
                     RecipeCatalogService recipeCatalogService) {
        this.mealCalendarService = mealCalendarService;
        this.recipeLibraryService = recipeLibraryService;
        this.pantryService = pantryService;
        this.checklistService = checklistService;
        this.shoppingListGenerator = shoppingListGenerator;
        this.macroGenerator = macroGenerator;
        this.profileService = profileService;
        this.recipeRecommender = recipeRecommender;
        this.recipeCatalogService = recipeCatalogService;
    }

    // ---- F01 meal calendar ----

    public List<DayPlan> getWeek(LocalDate weekStart) {
        return mealCalendarService.getWeek(weekStart);
    }

    /** The days shown by the month view, starting at {@code from}. */
    public List<DayPlan> getDays(LocalDate from, int count) {
        return mealCalendarService.getDays(from, count);
    }

    public DayPlan addMealToDay(LocalDate date, MealSlot slot, long recipeId, double servings) {
        return mealCalendarService.addMeal(date, slot, recipeId, servings);
    }

    public DayPlan addMealToDay(LocalDate date, MealSlot slot, long recipeId, double servings, String notes) {
        return mealCalendarService.addMeal(date, slot, recipeId, servings, notes);
    }

    public DayPlan updateMeal(LocalDate date, long entryId, MealSlot slot, long recipeId, double servings,
                              String notes) {
        return mealCalendarService.updateMeal(date, entryId, slot, recipeId, servings, notes);
    }

    public void removeMeal(LocalDate date, long entryId) {
        mealCalendarService.removeMeal(date, entryId);
    }

    public void skipMeal(LocalDate date, long entryId) {
        mealCalendarService.markSkipped(date, entryId);
    }

    /** Called when the app starts and by the midnight timer. Returns pantry warnings to show. */
    public List<String> closePastDays(LocalDate today) {
        return mealCalendarService.closePastDays(today);
    }

    // ---- F02 / F03 checklists ----

    public List<Checklist> getChecklists() {
        return checklistService.getAll();
    }

    public Checklist createChecklist(String name) {
        return checklistService.create(name);
    }

    public ChecklistItem addChecklistItem(long checklistId, String name, Quantity quantity) {
        return checklistService.addItem(checklistId, name, quantity);
    }

    public void setItemChecked(long itemId, boolean checked) {
        checklistService.setChecked(itemId, checked);
    }

    public void removeChecklistItem(long itemId) {
        checklistService.removeItem(itemId);
    }

    public void deleteChecklist(long checklistId) {
        checklistService.delete(checklistId);
    }

    public FinishResult finishShopping(long checklistId) {
        return checklistService.finishShopping(checklistId);
    }

    /** F03: may call Claude, so the GUI runs it in a background task. */
    public ShoppingDraft draftShoppingList(List<RecipeSelection> selections) {
        return shoppingListGenerator.draft(selections);
    }

    /** Adds an approved draft to a checklist; a null checklistId creates a new checklist for it. */
    public Checklist approveShoppingDraft(ShoppingDraft draft, Long checklistId) {
        long target = checklistId != null
                ? checklistId
                : checklistService.create("Shopping list " + LocalDate.now()).getId();
        return checklistService.addItems(target, draft.items);
    }

    // ---- F04 pantry ----

    public List<PantryItem> getPantry() {
        return pantryService.getItems();
    }

    public PantryItem addPantryItem(String name, Quantity quantity) {
        return pantryService.add(name, quantity);
    }

    public void updatePantryItem(long itemId, Quantity quantity) {
        pantryService.update(itemId, quantity);
    }

    public void removePantryItem(long itemId) {
        pantryService.remove(itemId);
    }

    // ---- F05 macros ----

    /** May call USDA and Claude, so the GUI runs it in a background task. */
    public NutritionSummary generateMacros(List<NutritionSelection> selections) {
        return macroGenerator.generate(selections);
    }

    // ---- F06 profile ----

    public UserProfile getProfile() {
        return profileService.getProfile();
    }

    /** Saves nothing and returns the problems if a value is invalid (UC06 3a). */
    public ValidationReport updateProfile(UserProfile profile) {
        return profileService.update(profile);
    }

    // ---- F07 recommendations ----

    /** May call Claude several times, so the GUI runs it in a background task. */
    public RecommendationResult recommendRecipes(RecommendationFilter filter) {
        return recipeRecommender.recommend(filter);
    }

    /** Adds the approved catalogue recipe to the recipe library and returns the library copy. */
    public Recipe approveRecommendation(long catalogId) {
        return recipeLibraryService.importFromCatalog(catalogId);
    }

    /** Every category name used in the catalogue, A to Z, for the filter form. */
    public List<String> catalogCategories() {
        return recipeCatalogService.categories();
    }

    // ---- F08 / F09 recipe library ----

    public List<Recipe> searchLibrary(String query) {
        return recipeLibraryService.search(query);
    }

    /** One recipe of the library, for the recipe form and "recipe show". */
    public Recipe getRecipe(long recipeId) {
        return recipeLibraryService.getById(recipeId);
    }

    /**
     * SD03: checks, adds nutrition and warnings, and saves a new (id 0) or edited recipe.
     * Throws InvalidRecipeException with the problem rows if nothing was saved. May call USDA.
     */
    public Recipe saveRecipe(Recipe recipe) {
        return recipeLibraryService.save(recipe);
    }

    /** Archives the recipe: it leaves the library, but meals already in the calendar keep it. */
    public void removeRecipe(long recipeId) {
        recipeLibraryService.remove(recipeId);
    }

    public List<Recipe> getFavourites() {
        return recipeLibraryService.favourites();
    }

    /** F09: returns the new favourite status. */
    public boolean toggleFavourite(long recipeId) {
        return recipeLibraryService.toggleFavourite(recipeId);
    }
}
