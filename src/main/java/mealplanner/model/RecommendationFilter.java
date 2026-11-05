package mealplanner.model;

import mealplanner.domain.NutritionGoal;

/**
 * What the user asks for in the Recommendations menu (F07). Every field is optional (null = any).
 * Dietary restrictions and allergies are not here: they always come from the profile.
 */
public class RecommendationFilter {

    public String category;
    public Integer maxCookMinutes;
    public NutritionGoal goal;
    public String request;

    public RecommendationFilter() {
    }

    public RecommendationFilter(String category, Integer maxCookMinutes, NutritionGoal goal) {
        this.category = category;
        this.maxCookMinutes = maxCookMinutes;
        this.goal = goal;
    }

    public String describe() {
        return (category == null || category.isBlank() ? "any category" : "category " + category)
                + ", " + (maxCookMinutes == null ? "any cooking time" : "at most " + maxCookMinutes + " min")
                + ", " + (goal == null || goal.isEmpty() ? "no nutrition goal" : "daily goal " + goal)
                + (hasRequest() ? ", request \"" + request.trim() + "\"" : "");
    }

    public boolean hasRequest() {
        return request != null && !request.isBlank();
    }
}