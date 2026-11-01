package mealplanner.domain;

/** One meal placed in a calendar slot, with its servings, notes and a "missing ingredients" flag. */
public class MealEntry {

    /** 0 until the CalendarRepository saves the entry. */
    private long id;
    private MealSlot slot;
    private Recipe recipe;
    private double servings;
    /** Anything the user wants to remember about this meal, e.g. "make extra rice". Never null. */
    private String notes = "";
    private boolean skipped;
    private boolean missingIngredients;

    public MealEntry(MealSlot slot, Recipe recipe, double servings) {
        this(slot, recipe, servings, "");
    }

    public MealEntry(MealSlot slot, Recipe recipe, double servings, String notes) {
        update(slot, recipe, servings, notes);
    }

    /** Changes the meal in place (the Meal details box). The id stays the same. */
    public void update(MealSlot slot, Recipe recipe, double servings, String notes) {
        if (slot == null || recipe == null) {
            throw new InvalidInputException("Choose a meal and a recipe");
        }
        if (servings <= 0) {
            throw new InvalidInputException("Servings must be greater than 0");
        }
        this.slot = slot;
        this.recipe = recipe;
        this.servings = servings;
        this.notes = notes == null ? "" : notes.trim();
    }

    public void markSkipped() {
        this.skipped = true;
    }

    public void setMissing(boolean flag) {
        this.missingIngredients = flag;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public MealSlot getSlot() { return slot; }
    public Recipe getRecipe() { return recipe; }
    public double getServings() { return servings; }
    public String getNotes() { return notes; }
    public boolean isSkipped() { return skipped; }
    public boolean isMissingIngredients() { return missingIngredients; }

    @Override
    public String toString() {
        String text = recipe.getName() + " x" + servings;
        if (skipped) text += " (skipped)";
        else if (missingIngredients) text += " [!]";
        return text;
    }
}