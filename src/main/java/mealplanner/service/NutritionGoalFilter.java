package mealplanner.service;

import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;

/**
 * One serving of the recipe fits a share of the daily goal (a meal is about a third of the day),
 * within a tolerance. Uses the recipe's stored nutrition per serving; if it has none, NutritionService
 * works it out. A recipe whose nutrition is incomplete does not match, because the goal cannot be confirmed.
 */
public class NutritionGoalFilter implements RecipeFilter {

    private final NutritionGoal goal;
    private final double mealShare = 0.33;
    private final double tolerancePct = 15;
    private final NutritionService nutritionService;

    public NutritionGoalFilter(NutritionGoal goal, NutritionService nutritionService) {
        this.goal = goal;
        this.nutritionService = nutritionService;
    }

    @Override
    public boolean matches(Recipe recipe) {
        NutritionSummary perServing = recipe.getNutritionPerServing();
        if (perServing == null) {
            if (nutritionService == null) return false;
            perServing = nutritionService.recipeNutrition(recipe, 1);
            recipe.setNutrition(perServing);
        }
        return perServing.isComplete() && goal.times(mealShare).isMet(perServing, tolerancePct);
    }

    @Override
    public String describe() {
        return "one serving fits a third of the daily goal (" + goal.times(mealShare) + ", "
                + (int) tolerancePct + "% tolerance)";
    }
}