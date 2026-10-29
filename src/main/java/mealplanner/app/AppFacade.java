package mealplanner.app;

import java.time.LocalDate;
import java.util.List;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealSlot;
import mealplanner.domain.Recipe;
import mealplanner.service.MealCalendarService;
import mealplanner.service.RecipeLibraryService;

/**
 * Facade: the one entry point used by both the GUI views and the CLI commands.
 * Only the F01 methods are written
 */
public class AppFacade {

    private final MealCalendarService mealCalendarService;
    private final RecipeLibraryService recipeLibraryService;
    // TODO (F02-F10): pantryService, checklistService, nutritionService, profileService,
    //                 recipeRecommender, shoppingListGenerator, macroGenerator, daySummaryWriter

    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService) {
        this.mealCalendarService = mealCalendarService;
        this.recipeLibraryService = recipeLibraryService;
    }

    // ---- F01 meal calendar ----

    public List<DayPlan> getWeek(LocalDate weekStart) {
        return mealCalendarService.getWeek(weekStart);
    }

    public DayPlan addMealToDay(LocalDate date, MealSlot slot, long recipeId, double servings) {
        return mealCalendarService.addMeal(date, slot, recipeId, servings);
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

    // ---- F08 recipe library (only what the calendar needs to pick a recipe) ----

    public List<Recipe> searchLibrary(String query) {
        return recipeLibraryService.search(query);
    }
}
