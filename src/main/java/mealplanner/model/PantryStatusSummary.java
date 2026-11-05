package mealplanner.model;

import java.util.List;

/**
 * The Shopping page summary (information only): ingredients that are missing (out of stock, not in the
 * pantry, or not enough for a planned meal) and staples marked running low. Names are sorted A to Z.
 */
public class PantryStatusSummary {

    public final List<String> missing;
    public final List<String> runningLow;

    public PantryStatusSummary(List<String> missing, List<String> runningLow) {
        this.missing = List.copyOf(missing);
        this.runningLow = List.copyOf(runningLow);
    }
}