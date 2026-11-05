package mealplanner.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mealplanner.agent.workflows.DaySummaryWriter;
import mealplanner.agent.workflows.MacroGenerator;
import mealplanner.agent.workflows.RecipeRecommender;
import mealplanner.agent.workflows.ShoppingListGenerator;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.DayPlan;
import mealplanner.domain.Ingredient;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.StapleStatus;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;
import mealplanner.model.DaySummary;
import mealplanner.model.FinishResult;
import mealplanner.model.NutritionSelection;
import mealplanner.model.PantryStatusSummary;
import mealplanner.model.RecipeSelection;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationResult;
import mealplanner.model.ShoppingDraft;
import mealplanner.service.ChecklistService;
import mealplanner.service.MealCalendarService;
import mealplanner.service.NutritionService;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeLibraryService;

/**
 * Facade: the one entry point used by both the GUI views and the CLI commands.
 * One group of methods per feature, F01 to F10.
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
    private final NutritionService nutritionService;
    private final DaySummaryWriter daySummaryWriter;

    /** F01-F05 only (used by the earlier tests); profile and recommendation calls then fail. */
    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService,
                     PantryService pantryService, ChecklistService checklistService,
                     ShoppingListGenerator shoppingListGenerator, MacroGenerator macroGenerator) {
        this(mealCalendarService, recipeLibraryService, pantryService, checklistService, shoppingListGenerator,
                macroGenerator, null, null, null);
    }

    /** F01-F09 (used by the earlier tests); the F10 summary calls then fail. */
    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService,
                     PantryService pantryService, ChecklistService checklistService,
                     ShoppingListGenerator shoppingListGenerator, MacroGenerator macroGenerator,
                     ProfileService profileService, RecipeRecommender recipeRecommender,
                     RecipeCatalogService recipeCatalogService) {
        this(mealCalendarService, recipeLibraryService, pantryService, checklistService, shoppingListGenerator,
                macroGenerator, profileService, recipeRecommender, recipeCatalogService, null, null);
    }

    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService,
                     PantryService pantryService, ChecklistService checklistService,
                     ShoppingListGenerator shoppingListGenerator, MacroGenerator macroGenerator,
                     ProfileService profileService, RecipeRecommender recipeRecommender,
                     RecipeCatalogService recipeCatalogService, NutritionService nutritionService,
                     DaySummaryWriter daySummaryWriter) {
        this.mealCalendarService = mealCalendarService;
        this.recipeLibraryService = recipeLibraryService;
        this.pantryService = pantryService;
        this.checklistService = checklistService;
        this.shoppingListGenerator = shoppingListGenerator;
        this.macroGenerator = macroGenerator;
        this.profileService = profileService;
        this.recipeRecommender = recipeRecommender;
        this.recipeCatalogService = recipeCatalogService;
        this.nutritionService = nutritionService;
        this.daySummaryWriter = daySummaryWriter;
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

    /** Adds a staple (no amount, only have / running low), or changes the status of one already there. */
    public PantryItem addStaple(String name, StapleStatus status) {
        return pantryService.addStaple(name, status);
    }

    public void setStapleStatus(long itemId, StapleStatus status) {
        pantryService.setStapleStatus(itemId, status);
    }

    /** Shopping page summary: missing ingredients (planned meals from today on) and staples running low. */
    public PantryStatusSummary pantryStatusSummary() {
        return pantryService.statusSummary(mealCalendarService.getDays(LocalDate.now(), PLANNING_DAYS));
    }

    /** How far ahead the summary looks for planned meals (the calendar's month view shows up to 6 weeks). */
    static final int PLANNING_DAYS = 42;

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

    // ---- F10 nutrition summary ----

    /**
     * SD07: the day's meals -> totals ("incomplete" if data is missing) -> compared with the daily goal ->
     * a short summary from Claude, or from a template if Claude is unavailable. May call USDA and Claude,
     * so the GUI runs it in a background task.
     */
    public DaySummary daySummary(LocalDate date) {
        DayPlan day = mealCalendarService.getDay(date);
        NutritionGoal goal = profileService.getProfile().getDefaultGoal();
        if (day.activeEntries().isEmpty()) {
            return DaySummary.noMeals(date, goal); // 1a
        }
        List<String> notes = matchFoods(List.of(day));
        List<String> meals = new ArrayList<>();
        for (MealEntry entry : day.activeEntries()) {
            NutritionSummary n = nutritionService.mealNutrition(entry);
            meals.add(String.format("%s: %s x%s (%.0f kcal%s)", entry.getSlot(), entry.getRecipe().getName(),
                    entry.getServings(), n.getCalories(),
                    n.isComplete() ? "" : ", incomplete"));
        }
        NutritionSummary totals = nutritionService.dayTotals(day);
        for (String note : notes) {
            totals = totals.withNote(note);
        }
        boolean goalMet = goal.isMet(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        List<String> misses = goal.misses(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        String text = daySummaryWriter.write(totals, goal);
        return new DaySummary(date, meals, totals, goal, goalMet, misses, text);
    }

    /** The seven days from weekStart added up (no Claude call). May call USDA. */
    public NutritionSummary weekSummary(LocalDate weekStart) {
        List<DayPlan> week = mealCalendarService.getWeek(weekStart);
        List<String> notes = matchFoods(week);
        NutritionSummary totals = nutritionService.weekTotals(week);
        for (String note : notes) {
            totals = totals.withNote(note);
        }
        return totals;
    }

    /** UC10 5a: values of 0 or less are rejected and nothing is saved; valid goals publish ProfileChanged. */
    public ValidationReport updateGoal(NutritionGoal goal) {
        return profileService.updateGoal(goal);
    }

    /**
     * "Calculate Nutrition" as in SD05: ingredients of planned recipes that have no saved nutrition and no
     * USDA food yet are matched first (Claude picks between candidates), so the totals can include them.
     */
    private List<String> matchFoods(List<DayPlan> days) {
        if (macroGenerator == null) {
            return List.of();
        }
        List<Ingredient> unmatched = new ArrayList<>();
        for (DayPlan day : days) {
            for (MealEntry entry : day.activeEntries()) {
                NutritionSummary saved = entry.getRecipe().getNutritionPerServing();
                if (saved != null && saved.isComplete()) continue;
                for (RecipeIngredient ri : entry.getRecipe().ingredients()) {
                    if (nutritionService.findFoodId(ri.getIngredient()).isEmpty()) {
                        unmatched.add(ri.getIngredient());
                    }
                }
            }
        }
        return unmatched.isEmpty() ? List.of() : macroGenerator.matchFoods(unmatched);
    }

    /** F07 extra: adds a recipe Claude wrote (not from the catalogue) to the library and returns the saved copy. */
    public Recipe approveWrittenRecipe(Recipe recipe) {
        return recipeRecommender.addWritten(recipe);
    }
}