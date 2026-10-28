package mealplanner.domain;

import java.util.List;
import java.util.Optional;

/**
 * The groups of recipe categories. Each list holds the suggested values; users can also type their own
 * (see RecipeCategory), so the lists are a starting point, not a limit.
 */
public enum CategoryGroup {

    COOKING_METHOD("Cooking Method", List.of(
            "Soup", "Broth", "Stewed", "Braised", "Boiled", "Steamed", "Fried", "Deep-Fried", "Pan-Fried",
            "Stir-Fried", "Roasted", "Grilled", "Barbecued", "Baked", "Smoked", "Charred", "Seared", "Poached",
            "Blanched", "Sautéed", "Slow-Cooked", "Pressure-Cooked", "Air-Fried", "Flame-Cooked", "Salt-Baked",
            "Fire-Roasted", "Fermented", "Cured", "Pickled", "Dried", "Freeze-Dried", "Raw", "Marinated")),

    DISH_TYPE("Dish Type", List.of(
            "Soup", "Broth", "Stew", "Curry", "Hotpot", "Salad", "Sandwich", "Burger", "Wrap", "Taco", "Burrito",
            "Pizza", "Pasta", "Noodles", "Dumplings", "Buns", "Rolls", "Bread", "Pastries", "Pies", "Tarts",
            "Casseroles", "Skewers", "Kebabs", "Platters", "Bowls", "Rice Dishes", "Porridge", "Congee", "Risotto",
            "Pancakes", "Waffles", "Omelettes", "Fritters", "Cutlets", "Meatloaf", "Stuffed Dishes",
            "Layered Dishes", "Cured Meats", "Charcuterie")),

    FLAVOR("Flavor", List.of(
            "Sweet", "Savory", "Salty", "Sour", "Bitter", "Spicy", "Umami", "Smoky", "Tangy", "Creamy", "Buttery",
            "Garlicky", "Peppery", "Herbal", "Citrusy", "Fruity", "Earthy", "Nutty", "Cheesy", "Rich", "Mild",
            "Pungent", "Sweet-and-Sour", "Sweet-and-Spicy", "Hot-and-Sour")),

    MAIN_INGREDIENT("Main Ingredient", List.of(
            "Meat", "Poultry", "Seafood", "Fish", "Shellfish", "Eggs", "Dairy", "Cheese", "Vegetables", "Fruits",
            "Mushrooms", "Rice", "Noodles", "Pasta", "Wheat", "Bread", "Grains", "Legumes", "Beans", "Tofu",
            "Soy Products", "Nuts", "Seeds", "Seaweed", "Herbs", "Flowers", "Roots and Tubers", "Offal",
            "Bone Marrow", "Insects", "Exotic or Fantasy Ingredients")),

    /** For the user's own categories that don't fit any group above. */
    OTHER("Other", List.of());

    private final String label;
    private final List<String> values;

    CategoryGroup(String label, List<String> values) {
        this.label = label;
        this.values = values;
    }

    public String getLabel() { return label; }
    public List<String> getValues() { return values; }

    /** The built-in spelling of a value in this group, ignoring case: "pan-fried" gives "Pan-Fried". */
    public Optional<String> find(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        return values.stream().filter(v -> v.equalsIgnoreCase(t)).findFirst();
    }
}
