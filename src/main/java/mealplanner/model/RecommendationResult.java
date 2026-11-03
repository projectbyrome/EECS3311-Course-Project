package mealplanner.model;

import java.util.List;

/** The cards to show, and a message such as "No recipes found. Try loosening your filters." */
public class RecommendationResult {

    public static final String NOTHING_FOUND = "No recipes found. Try loosening your filters.";

    public final List<Recommendation> recommendations;
    public final String message;

    public RecommendationResult(List<Recommendation> recommendations, String message) {
        this.recommendations = List.copyOf(recommendations);
        this.message = message;
    }

    public static RecommendationResult nothingFound() {
        return new RecommendationResult(List.of(), NOTHING_FOUND);
    }

    public boolean isEmpty() {
        return recommendations.isEmpty();
    }
}