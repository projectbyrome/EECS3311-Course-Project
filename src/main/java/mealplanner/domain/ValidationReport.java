package mealplanner.domain;

import java.util.ArrayList;
import java.util.List;

/** A list of problems found by a validator; valid when the list is empty. */
public class ValidationReport {

    private final List<String> violations = new ArrayList<>();

    public void add(String message) {
        violations.add(message);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public List<String> getViolations() {
        return List.copyOf(violations);
    }

    @Override
    public String toString() {
        return isValid() ? "valid" : String.join("; ", violations);
    }
}