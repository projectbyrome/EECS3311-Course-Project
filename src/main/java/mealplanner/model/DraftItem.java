package mealplanner.model;

import mealplanner.domain.Quantity;

/** One line of a shopping draft: name, quantity and store section (e.g. "Produce"). */
public class DraftItem {

    public String name;
    public Quantity quantity;
    public String category;

    public DraftItem() {
    }

    public DraftItem(String name, Quantity quantity, String category) {
        this.name = name;
        this.quantity = quantity;
        this.category = category;
    }

    @Override
    public String toString() {
        return name + " " + quantity;
    }
}