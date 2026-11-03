package mealplanner.domain;

import java.util.List;
import java.util.Map;

/**
 * SD03 / UC08 4a: a recipe could not be saved. Carries every problem, and which ingredient rows
 * (0-based) they belong to, so the recipe form can flag those rows. Nothing was saved.
 */
public class InvalidRecipeException extends InvalidInputException {

    private final List<String> problems;
    private final Map<Integer, String> rowProblems;

    public InvalidRecipeException(List<String> problems, Map<Integer, String> rowProblems) {
        super("Recipe not saved:\n- " + String.join("\n- ", problems));
        this.problems = List.copyOf(problems);
        this.rowProblems = Map.copyOf(rowProblems);
    }

    public List<String> getProblems() {
        return problems;
    }

    /** Ingredient row index (0-based) to the problem with that row. */
    public Map<Integer, String> getRowProblems() {
        return rowProblems;
    }
}
