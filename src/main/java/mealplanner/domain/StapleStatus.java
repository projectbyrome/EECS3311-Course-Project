package mealplanner.domain;

/**
 * Status of a pantry staple (salt, oil, spices...): no amount is tracked, only whether you have it.
 * Both count as available for recipes; RUNNING_LOW is also listed in the Shopping page summary.
 */
public enum StapleStatus {
    HAVE("Have"), RUNNING_LOW("Running low");

    private final String label;

    StapleStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static StapleStatus parse(String text) {
        String t = text == null ? "" : text.trim().toLowerCase().replace('_', ' ');
        return switch (t) {
            case "have", "" -> HAVE;
            case "low", "running low" -> RUNNING_LOW;
            default -> throw new InvalidInputException("Staple status must be have or low, not '" + text + "'");
        };
    }
}