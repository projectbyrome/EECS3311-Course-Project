package mealplanner.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mealplanner.domain.Ingredient;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.model.RecipeSelection;
import mealplanner.model.ShoppingNeed;

/**
 * F03 exact arithmetic (no AI): scales the chosen recipes, merges the same ingredient across recipes,
 * and subtracts what the pantry already has. Claude only tidies the result; it never does the maths.
 */
public class ShoppingNeedsCalculator {

    private final UnitConverter converter;

    public ShoppingNeedsCalculator(UnitConverter converter) {
        this.converter = converter;
    }

    /**
     * @param selections the recipes and servings the user picked
     * @param recipes    the Recipe objects for those selections, in the same order
     * @param pantry     the current pantry
     */
    public List<ShoppingNeed> calculate(List<RecipeSelection> selections, List<Recipe> recipes,
                                        List<PantryItem> pantry) {
        if (selections.size() != recipes.size()) {
            throw new IllegalArgumentException("One recipe is needed for each selection");
        }
        List<RecipeIngredient> requirements = new ArrayList<>();
        for (int i = 0; i < selections.size(); i++) {
            double servings = selections.get(i).servings;
            if (servings <= 0) {
                throw new InvalidInputException("Servings for " + recipes.get(i).getName() + " must be greater than 0");
            }
            requirements.addAll(recipes.get(i).scaledTo(servings));
        }

        List<ShoppingNeed> needs = new ArrayList<>();
        for (RecipeIngredient need : merge(requirements)) {
            Quantity left = subtractPantry(need, pantry);
            if (left.getAmount() > 1e-9) {
                needs.add(new ShoppingNeed(need.getIngredient(), left));
            }
        }
        return needs;
    }

    /**
     * Adds up the same ingredient across recipes, in the unit it was first seen in.
     * Amounts whose units cannot be converted (e.g. PIECE with no weight per piece) stay as separate lines.
     */
    private List<RecipeIngredient> merge(List<RecipeIngredient> requirements) {
        Map<Long, List<RecipeIngredient>> byIngredient = new LinkedHashMap<>();
        for (RecipeIngredient req : requirements) {
            List<RecipeIngredient> lines = byIngredient.computeIfAbsent(req.getIngredient().getId(),
                    id -> new ArrayList<>());
            boolean merged = false;
            for (int i = 0; i < lines.size(); i++) {
                RecipeIngredient line = lines.get(i);
                Ingredient ing = line.getIngredient();
                Quantity have = line.getQuantity();
                if (converter.canConvert(req.getQuantity().getUnit(), have.getUnit(), ing)) {
                    Quantity sum = have.plus(converter.convert(req.getQuantity(), have.getUnit(), ing));
                    lines.set(i, new RecipeIngredient(ing, sum));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                lines.add(req);
            }
        }
        List<RecipeIngredient> result = new ArrayList<>();
        byIngredient.values().forEach(result::addAll);
        return result;
    }

    private Quantity subtractPantry(RecipeIngredient need, List<PantryItem> pantry) {
        Ingredient ing = need.getIngredient();
        Quantity amount = need.getQuantity();
        double left = amount.getAmount();
        for (PantryItem item : pantry) {
            if (item.getIngredient().getId() != ing.getId()) continue;
            Quantity have = item.getQuantity();
            if (converter.canConvert(have.getUnit(), amount.getUnit(), ing)) {
                left -= converter.convert(have, amount.getUnit(), ing).getAmount();
            }
        }
        return new Quantity(Math.max(0, left), amount.getUnit());
    }
}