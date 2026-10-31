package mealplanner.app;

import java.time.LocalDate;
import java.util.List;
import mealplanner.agent.workflows.ShoppingListGenerator;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealSlot;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.model.FinishResult;
import mealplanner.model.RecipeSelection;
import mealplanner.model.ShoppingDraft;
import mealplanner.service.ChecklistService;
import mealplanner.service.MealCalendarService;
import mealplanner.service.PantryService;
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

    public AppFacade(MealCalendarService mealCalendarService, RecipeLibraryService recipeLibraryService,
                     PantryService pantryService, ChecklistService checklistService,
                     ShoppingListGenerator shoppingListGenerator) {
        this.mealCalendarService = mealCalendarService;
        this.recipeLibraryService = recipeLibraryService;
        this.pantryService = pantryService;
        this.checklistService = checklistService;
        this.shoppingListGenerator = shoppingListGenerator;
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

    // ---- F08 recipe library (only what the calendar needs to pick a recipe) ----

    public List<Recipe> searchLibrary(String query) {
        return recipeLibraryService.search(query);
    }
}