package mealplanner.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import mealplanner.data.CalendarRepository;
import mealplanner.domain.DayPlan;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.Recipe;
import mealplanner.events.AppEvent;
import mealplanner.events.CalendarChanged;
import mealplanner.events.EventBus;
import mealplanner.events.EventListener;
import mealplanner.events.PantryChanged;

/**
 * F01 Weekly Meal Calendar: returns a week of days, adds meals, flags meals the pantry cannot cover,
 * and removes used ingredients when a day has passed. Listens for PantryChanged to re-check flags.
 */
public class MealCalendarService implements EventListener {

    private final CalendarRepository calendarRepository;
    private final RecipeLibraryService recipeLibraryService;
    private final PantryService pantryService;
    private final EventBus eventBus;

    public MealCalendarService(CalendarRepository calendarRepository, RecipeLibraryService recipeLibraryService,
                               PantryService pantryService, EventBus eventBus) {
        this.calendarRepository = calendarRepository;
        this.recipeLibraryService = recipeLibraryService;
        this.pantryService = pantryService;
        this.eventBus = eventBus;
    }

    public DayPlan getDay(LocalDate date) {
        return calendarRepository.findDay(date).orElseGet(() -> new DayPlan(date));
    }

    /** Seven days starting at weekStart; days with nothing saved come back empty. */
    public List<DayPlan> getWeek(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(7);
        Map<LocalDate, DayPlan> saved = calendarRepository.findDaysFrom(weekStart).stream()
                .filter(d -> d.getDate().isBefore(weekEnd))
                .collect(Collectors.toMap(DayPlan::getDate, Function.identity()));
        List<DayPlan> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            week.add(saved.getOrDefault(date, new DayPlan(date)));
        }
        return week;
    }

    /** SD01: validates, flags the meal if the pantry is short, saves the day and publishes CalendarChanged. */
    public DayPlan addMeal(LocalDate date, MealSlot slot, long recipeId, double servings) {
        if (servings <= 0) {
            throw new InvalidInputException("Servings must be greater than 0");
        }
        Recipe recipe = recipeLibraryService.getById(recipeId);
        DayPlan day = getDay(date);
        if (day.isClosed()) {
            throw new InvalidInputException("Cannot add a meal to a day that has passed: " + date);
        }
        MealEntry entry = new MealEntry(slot, recipe, servings);
        entry.setMissing(!pantryService.missingFor(recipe, servings).isEmpty());
        day.addEntry(entry);
        calendarRepository.save(day);
        eventBus.publish(new CalendarChanged(date));
        return day;
    }

    public void removeMeal(LocalDate date, long entryId) {
        DayPlan day = requireDay(date);
        day.removeEntry(entryId);
        calendarRepository.save(day);
        eventBus.publish(new CalendarChanged(date));
    }

    public void markSkipped(LocalDate date, long entryId) {
        DayPlan day = requireDay(date);
        day.findEntry(entryId).markSkipped();
        calendarRepository.save(day);
        eventBus.publish(new CalendarChanged(date));
    }

    /**
     * SD08: for every open day before today, takes the ingredients of the meals that were not skipped
     * out of the pantry, then closes the day. Returns pantry warnings for the UI to show.
     */
    public List<String> closePastDays(LocalDate today) {
        List<String> warnings = new ArrayList<>();
        for (DayPlan day : calendarRepository.findOpenDaysBefore(today)) {
            for (MealEntry entry : day.activeEntries()) {
                warnings.addAll(pantryService.consume(entry.getRecipe().scaledTo(entry.getServings())));
            }
            day.close();
            calendarRepository.save(day);
            eventBus.publish(new CalendarChanged(day.getDate()));
        }
        return warnings;
    }

    /** Re-checks the "missing ingredients" flag on every upcoming meal. */
    public void refreshFlags(LocalDate from) {
        for (DayPlan day : calendarRepository.findDaysFrom(from)) {
            if (day.isClosed()) continue;
            boolean changed = false;
            for (MealEntry entry : day.activeEntries()) {
                boolean missing = !pantryService.missingFor(entry.getRecipe(), entry.getServings()).isEmpty();
                if (missing != entry.isMissingIngredients()) {
                    entry.setMissing(missing);
                    changed = true;
                }
            }
            if (changed) {
                calendarRepository.save(day);
                eventBus.publish(new CalendarChanged(day.getDate()));
            }
        }
    }

    /** Observer: the pantry changed, so the flags may be out of date. */
    @Override
    public void onEvent(AppEvent event) {
        if (event instanceof PantryChanged) {
            refreshFlags(LocalDate.now());
        }
    }

    private DayPlan requireDay(LocalDate date) {
        return calendarRepository.findDay(date)
                .orElseThrow(() -> new InvalidInputException("Nothing is planned on " + date));
    }
}
