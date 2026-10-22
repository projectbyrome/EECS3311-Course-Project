package mealplanner.domain;


public class RecipeIngredient {

    private final Ingredient ingredient;
    private final Quantity quantity;

    public RecipeIngredient(Ingredient ingredient, Quantity quantity) {
        this.ingredient = ingredient;
        this.quantity = quantity;
    }

    public static RecipeIngredient unchecked(String name, Quantity quantity) {
        return new RecipeIngredient(new Ingredient(0, name == null ? "" : name.trim(), null, 0, 0), quantity);
    }

    public Ingredient getIngredient() {
        return ingredient;
    }
    public Quantity getQuantity() { return quantity;
    }

    @Override
    public String toString() {
        return (quantity == null ? "?" : quantity.toString()) + " " + ingredient.getName();
    }
}