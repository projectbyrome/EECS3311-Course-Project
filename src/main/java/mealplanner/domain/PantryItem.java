package mealplanner.domain;

import java.time.LocalDate;

/**
 * One thing the user has at home. An ingredient (eggs, chicken) has a quantity that is tracked.
 * A staple (salt, oil, spices) has no amount, only a StapleStatus; its quantity is a 0 placeholder.
 */
public class PantryItem {

    private long id;
    private final Ingredient ingredient;
    private Quantity quantity;
    private final LocalDate addedOn;
    private StapleStatus stapleStatus;

    public PantryItem(long id, Ingredient ingredient, Quantity quantity, LocalDate addedOn) {
        this.id = id;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.addedOn = addedOn;
    }

    /** A staple: no amount, only a status. */
    public static PantryItem staple(Ingredient ingredient, StapleStatus status, LocalDate addedOn) {
        PantryItem item = new PantryItem(0, ingredient, new Quantity(0, Unit.PIECE), addedOn);
        item.stapleStatus = status;
        return item;
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
    public boolean isStaple() {
        return stapleStatus != null;
    }
    public StapleStatus getStapleStatus() {
        return stapleStatus;
    }
    public void setStapleStatus(StapleStatus status) {
        this.stapleStatus = status;
    }

    @Override
    public String toString() {
        return ingredient.getName() + ": " + (isStaple() ? "staple, " + stapleStatus.label().toLowerCase() : quantity);
    }
}