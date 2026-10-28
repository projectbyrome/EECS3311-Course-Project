package mealplanner.domain;

import java.util.Objects;

/**
 * One category of a recipe, for example Dish Type: Soup. The name can be one of the group's suggested values
 * or the user's own, like Dish Type: Yogurt Bowl.
 */
public final class RecipeCategory {

    private final CategoryGroup group;
    private final String name;

    public RecipeCategory(CategoryGroup group, String name) {
        this.group = Objects.requireNonNull(group, "group");
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("A category needs a name");
        }
        if (name.contains(":") || name.contains("\n")) {
            throw new InvalidInputException("A category name cannot contain ':' or a line break");
        }
        // a built-in value keeps its usual spelling ("pan-fried" becomes "Pan-Fried"), anything else is the user's own
        this.name = group.find(name).orElse(name.trim());
    }

    /** True if the user made this category up, false if it is one of the group's suggested values. */
    public boolean isCustom() {
        return group.find(name).isEmpty();
    }

    public CategoryGroup getGroup() { return group; }
    public String getName() { return name; }

    /** How it is stored in the database: "DISH_TYPE:Soup". */
    public String toKey() {
        return group.name() + ":" + name;
    }

    /** The opposite of toKey(). */
    public static RecipeCategory parse(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) {
            throw new InvalidInputException("Category must look like GROUP:Name, got '" + key + "'");
        }
        return new RecipeCategory(CategoryGroup.valueOf(key.substring(0, colon).trim()),
                key.substring(colon + 1));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecipeCategory)) {
            return false;
        }
        RecipeCategory c = (RecipeCategory) o;
        return c.group == group && c.name.equalsIgnoreCase(name);
    }

    @Override
    public int hashCode() { return Objects.hash(group, name.toLowerCase()); }

    @Override
    public String toString() { return group.getLabel() + ": " + name; }
}
