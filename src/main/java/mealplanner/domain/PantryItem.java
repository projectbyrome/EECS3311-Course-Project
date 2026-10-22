package mealplanner.domain;

import java.time.LocalDate;

public class PantryItem {

    private long id;
    private final Ingredient ingredient;
    private Quantity quantity;
    private final LocalDate addedOn;

    public PantryItem(long id, Ingredient ingredient, Quantity quantity, LocalDate addedOn) {
        this.id = id;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.addedOn = addedOn;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public Ingredient getIngredient() {
        return ingredient;
    }
    public Quantity getQuantity() {
        return quantity;
    }
    public void setQuantity(Quantity quantity) {
        this.quantity = quantity;
    }
    public LocalDate getAddedOn() {
        return addedOn;
    }

    @Override
    public String toString() {
        return ingredient.getName() + ": " + quantity;
    }

}
