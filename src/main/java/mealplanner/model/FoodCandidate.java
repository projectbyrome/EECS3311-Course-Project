package mealplanner.model;

/** One possible USDA food for an ingredient name (F05): its FoodData Central id and description. */
public class FoodCandidate {

    public final String fdcId;
    public final String description;

    public FoodCandidate(String fdcId, String description) {
        this.fdcId = fdcId;
        this.description = description;
    }

    @Override
    public String toString() {
        return fdcId + " " + description;
    }
}