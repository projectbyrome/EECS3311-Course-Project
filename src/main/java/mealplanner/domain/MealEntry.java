package mealplanner.domain;

public class MealEntry {

    private long id;
    private final MealSlot slot;
    private final Recipe recipe;
    private final double servings;
    private boolean  skipped;
    private boolean missingIngredients;


    public MealEntry(MealSlot slot, Recipe recipe, double servings) {
        if (servings<=0){
            throw new InvalidInputException("Servings must be greater than 0");
        }
        this.slot = slot;
        this.recipe = recipe;
        this.servings = servings;
    }

    public void markSkipped(){
        this.skipped=true;
    }

    public void setMissing(boolean flag) {
        this.missingIngredients = flag;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public MealSlot getSlot() {
        return slot;
    }
    public Recipe getRecipe() {
        return recipe;
    }
    public double getServings() {
        return servings;
    }
    public boolean isSkipped() {
        return skipped;
    }
    public boolean isMissingIngredients() {
        return missingIngredients;
    }

    @Override
    public String toString() {
        String text = recipe.getName() + " x" + servings;
        if (skipped) {
            text += " (skipped)";
        }
        else if (missingIngredients) {
            text += " [!!]";
        }
        return text;
    }


}
