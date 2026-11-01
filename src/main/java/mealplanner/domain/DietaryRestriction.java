package mealplanner.domain;

import java.util.List;
import java.util.Locale;

/**
 * Dietary restrictions the user can pick in the profile (F06). allows() decides from the ingredient's
 * name and allergen tags, because the ingredient list has no separate "meat" or "animal product" field.
 */
public enum DietaryRestriction {
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    PESCATARIAN("pescatarian"),
    GLUTEN_FREE("gluten-free"),
    DAIRY_FREE("dairy-free"),
    HALAL("halal");

    /** Words that mark meat (not fish) in an ingredient name. */
    private static final List<String> MEAT = List.of("chicken", "beef", "pork", "bacon", "ham", "lamb",
            "turkey", "sausage", "veal", "duck", "gelatin");
    /** Words that mark fish or seafood in an ingredient name. */
    private static final List<String> SEAFOOD = List.of("salmon", "tuna", "fish", "shrimp", "prawn", "cod",
            "anchovy", "crab", "lobster");
    private static final List<String> PORK_OR_ALCOHOL = List.of("pork", "bacon", "ham", "lard", "gelatin",
            "wine", "beer", "rum");
    private static final List<String> OTHER_ANIMAL = List.of("honey");

    private final String label;

    DietaryRestriction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean allows(Ingredient ingredient) {
        String name = ingredient.getName().toLowerCase(Locale.ROOT);
        boolean meat = containsAny(name, MEAT);
        boolean seafood = containsAny(name, SEAFOOD) || hasTag(ingredient, "fish") || hasTag(ingredient, "shellfish");
        return switch (this) {
            case VEGETARIAN -> !meat && !seafood;
            case VEGAN -> !meat && !seafood && !hasTag(ingredient, "egg") && !hasTag(ingredient, "milk")
                    && !containsAny(name, OTHER_ANIMAL);
            case PESCATARIAN -> !meat;
            case GLUTEN_FREE -> !hasTag(ingredient, "gluten");
            case DAIRY_FREE -> !hasTag(ingredient, "milk");
            case HALAL -> !containsAny(name, PORK_OR_ALCOHOL);
        };
    }

    /** Reads "gluten-free", "GLUTEN_FREE" or "gluten free". */
    public static DietaryRestriction parse(String text) {
        String t = text == null ? "" : text.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(t);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown dietary restriction '" + text + "'. Choose from: "
                    + String.join(", ", java.util.Arrays.stream(values()).map(DietaryRestriction::label).toList()));
        }
    }

    private static boolean containsAny(String name, List<String> words) {
        return words.stream().anyMatch(name::contains);
    }

    private static boolean hasTag(Ingredient ingredient, String tag) {
        return ingredient.getAllergenTags().stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }
}