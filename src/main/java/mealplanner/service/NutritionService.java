package mealplanner.service;

import java.util.List;
import java.util.Optional;
import mealplanner.data.IngredientRepository;
import mealplanner.domain.DayPlan;
import mealplanner.domain.IncompatibleUnitException;
import mealplanner.domain.Ingredient;
import mealplanner.domain.MealEntry;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.external.NutritionSource;
import mealplanner.external.NutritionSourceException;
import mealplanner.model.FoodCandidate;

/**
 * Calculates nutrients for ingredients, recipes, days and weeks (F05, used later by F08 and F10).
 * Values come from a NutritionSource (the cached USDA adapter) and are scaled with UnitConverter.
 * Anything that cannot be worked out counts as zero and marks the result incomplete (UC05 4a).
 */
public class NutritionService {

    private final NutritionSource source;
    private final IngredientRepository ingredientRepository;
    private final UnitConverter converter;

    public NutritionService(NutritionSource source, IngredientRepository ingredientRepository,
                            UnitConverter converter) {
        this.source = source;
        this.ingredientRepository = ingredientRepository;
        this.converter = converter;
    }

    /** Sum of the recipe's ingredients, scaled to the given number of servings. */
    public NutritionSummary recipeNutrition(Recipe recipe, double servings) {
        NutritionSummary total = NutritionSummary.zero();
        for (RecipeIngredient ri : recipe.scaledTo(servings)) {
            total = total.plus(ingredientNutrition(ri.getIngredient(), ri.getQuantity()));
        }
        return total;
    }

    /** USDA values are per 100 g, so the quantity is turned into grams first. */
    public NutritionSummary ingredientNutrition(Ingredient ingredient, Quantity quantity) {
        Optional<String> fdcId = findFoodId(ingredient);
        if (fdcId.isEmpty()) {
            return NutritionSummary.incomplete("No USDA food is matched to " + ingredient.getName());
        }
        double grams;
        try {
            grams = converter.toGrams(quantity, ingredient);
        } catch (IncompatibleUnitException e) {
            return NutritionSummary.incomplete("No gram weight for " + quantity.getUnit() + " of "
                    + ingredient.getName());
        }
        Optional<NutritionSummary> per100g;
        try {
            per100g = source.nutrientsFor(fdcId.get());
        } catch (NutritionSourceException e) {
            return NutritionSummary.incomplete("Nutrition database could not be reached for " + ingredient.getName());
        }
        if (per100g.isEmpty()) {
            return NutritionSummary.incomplete(ingredient.getName() + " was not found in the nutrition database");
        }
        return per100g.get().times(grams / 100.0);
    }

    /**
     * Meals of the day that were not skipped (F10). A recipe whose complete nutrition per serving was saved
     * with it (F08) uses that; any other recipe is worked out from its ingredients.
     */
    public NutritionSummary dayTotals(DayPlan day) {
        NutritionSummary total = NutritionSummary.zero();
        for (MealEntry entry : day.activeEntries()) {
            total = total.plus(mealNutrition(entry));
        }
        return total;
    }

    /** One planned meal: the recipe's nutrition for the planned servings. */
    public NutritionSummary mealNutrition(MealEntry entry) {
        NutritionSummary perServing = entry.getRecipe().getNutritionPerServing();
        if (perServing != null && perServing.isComplete()) {
            return perServing.times(entry.getServings());
        }
        return recipeNutrition(entry.getRecipe(), entry.getServings());
    }

    public NutritionSummary weekTotals(List<DayPlan> week) {
        NutritionSummary total = NutritionSummary.zero();
        for (DayPlan day : week) {
            total = total.plus(dayTotals(day));
        }
        return total;
    }

    public Optional<String> findFoodId(Ingredient ingredient) {
        String id = ingredient.getFdcId();
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    /** @throws NutritionSourceException if the nutrition database cannot be reached */
    public List<FoodCandidate> searchCandidates(String name) {
        return source.search(name);
    }

    /** Remembers the USDA food for this ingredient, so it is not matched again next time. */
    public void saveFoodId(Ingredient ingredient, String fdcId) {
        ingredient.setFdcId(fdcId);
        ingredientRepository.save(ingredient);
    }

    /** Looks up a known ingredient by name, for the Macro Generator's ingredient selections. */
    public Optional<Ingredient> findIngredient(String name) {
        return name == null ? Optional.empty() : ingredientRepository.findByName(name.trim());
    }
}