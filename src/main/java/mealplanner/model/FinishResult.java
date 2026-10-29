package mealplanner.model;

import java.util.List;

/** What Finish Shopping did: how many items went into the pantry, how many were merged, and what was not added. */
public class FinishResult {

    public final int movedCount;
    public final int mergedCount;
    public final List<String> notAdded;

    public FinishResult(int movedCount, int mergedCount, List<String> notAdded) {
        this.movedCount = movedCount;
        this.mergedCount = mergedCount;
        this.notAdded = List.copyOf(notAdded);
    }

    @Override
    public String toString() {
        String text = movedCount + " item(s) moved to the pantry (" + mergedCount + " added to items already there).";
        if (!notAdded.isEmpty()) {
            text += " Not added (unknown ingredient): " + String.join(", ", notAdded) + ".";
        }
        return text;
    }
}