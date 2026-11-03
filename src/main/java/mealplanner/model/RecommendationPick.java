package mealplanner.model;

/** One recipe Claude picked, as read from its JSON answer: {"catalogId": 4, "reason": "..."}. */
public class RecommendationPick {

    public long catalogId;
    public String reason;

    public RecommendationPick() {
    }

    public RecommendationPick(long catalogId, String reason) {
        this.catalogId = catalogId;
        this.reason = reason;
    }
}