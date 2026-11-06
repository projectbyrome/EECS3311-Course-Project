package mealplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What Claude says about an ingredient name that isn't in the ingredient list yet (e.g. ketchup):
 * whether it is a food, its usual simple name, a category, its allergens and how heavy it is.
 * If Claude could not be asked, checkedByClaude is false and only the typed name is known.
 */
public class IngredientCheck {

    public boolean food = true;
    public String name;
    public String category = "Other";
    public List<String> allergens = new ArrayList<>();
    public double gramsPerPiece;
    public double densityGPerMl;
    public String note = "";
    public boolean checkedByClaude;

    /** Used when Claude is unavailable: the typed name, category Other, nothing else known. */
    public static IngredientCheck unchecked(String typedName) {
        IngredientCheck check = new IngredientCheck();
        check.name = typedName == null ? "" : typedName.trim().toLowerCase();
        return check;
    }

    /** "Condiments, allergens: egg" or "Other" when Claude wasn't asked. */
    public String describe() {
        String text = category == null || category.isBlank() ? "Other" : category;
        if (checkedByClaude) {
            text += allergens.isEmpty() ? ", no common allergens" : ", allergens: " + String.join(", ", allergens);
        }
        return text;
    }
}