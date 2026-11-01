package mealplanner.model;

import mealplanner.domain.Quantity;

/** An ingredient (usually from the pantry) and an amount of it (F05). */
public class IngredientPortion extends NutritionSelection {

    public String ingredientName;
    public Quantity amount;

    public IngredientPortion(String ingredientName, Quantity amount) {
        this.ingredientName = ingredientName;
        this.amount = amount;
    }

    @Override
    public String describe() {
        return ingredientName + " " + amount;
    }
}