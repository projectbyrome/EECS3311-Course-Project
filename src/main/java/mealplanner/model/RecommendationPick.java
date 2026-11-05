package mealplanner.model;

/**
 * One recipe Claude picked, as read from its JSON answer: {"catalogId": 4, "reason": "..."} for a catalogue
 * recipe, or {"newRecipe": {...}, "reason": "..."} for a recipe Claude wrote itself.
 */
public class RecommendationPick {

    public long catalogId;
    public WrittenRecipe newRecipe;
    public String reason;

    public RecommendationPick() {
    }

    public RecommendationPick(long catalogId, String reason) {
        this.catalogId = catalogId;
        this.reason = reason;
    }
}