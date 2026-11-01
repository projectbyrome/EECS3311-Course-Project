package mealplanner.model;

/** Something the user picked in the Macro Generator (F05): a recipe portion or an ingredient amount. */
public abstract class NutritionSelection {

    public abstract String describe();

    @Override
    public String toString() {
        return describe();
    }
}