package mealplanner.model;

import mealplanner.domain.Ingredient;
import mealplanner.domain.Quantity;

/** One ingredient that must be bought, and how much (recipes scaled and merged, minus the pantry). */
public class ShoppingNeed {

    public final Ingredient ingredient;
    public final Quantity quantity;

    public ShoppingNeed(Ingredient ingredient, Quantity quantity) {
        this.ingredient = ingredient;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return ingredient.getName() + " " + quantity;
    }
}