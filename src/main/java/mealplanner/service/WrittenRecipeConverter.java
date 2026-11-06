package mealplanner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mealplanner.data.IngredientRepository;
import mealplanner.domain.CategoryGroup;
import mealplanner.domain.Ingredient;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;
import mealplanner.model.WrittenRecipe;

/**
 * F07 extra: turns a recipe Claude wrote into a Recipe, so the same filters and dietary rules can check it.
 */
public class WrittenRecipeConverter {

    static final String ESTIMATE_NOTE = "Estimated by Claude; worked out again when added to your library";
    static final String SAVED_NOTE = "Estimated by Claude, because USDA could not work it out";

    private final IngredientRepository ingredientRepository;

    public WrittenRecipeConverter(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    /** Throws InvalidInputException if the recipe is missing something the app needs. */
    public Recipe toRecipe(WrittenRecipe w) {
        if (w == null || w.name == null || w.name.isBlank()) {
            throw new InvalidInputException("The written recipe has no name");
        }
        if (w.ingredients == null || w.ingredients.isEmpty()) {
            throw new InvalidInputException("The written recipe has no ingredients");
        }
        Recipe recipe = new Recipe(0, w.name.trim(), Math.max(1, w.servings));
        recipe.setCookMinutes(Math.max(0, w.cookMinutes));
        List<RecipeCategory> categories = new ArrayList<>();
        for (String c : w.categories == null ? List.<String>of() : w.categories) {
            RecipeCategory category = category(c);
            if (category != null) categories.add(category);
        }
        recipe.setCategories(categories);
        for (WrittenRecipe.Line line : w.ingredients) {
            if (line == null || line.name == null || line.name.isBlank()) {
                continue; // a line without a name can't be used; the rest of the recipe still can
            }
            Quantity quantity = quantity(line);
            Unit unit = quantity.getUnit();
            Ingredient ingredient = ingredientRepository.findByName(line.name.trim())
                    .orElseGet(() -> newIngredient(line, unit));
            recipe.addIngredient(new RecipeIngredient(ingredient, quantity));
        }
        if (recipe.ingredients().isEmpty()) {
            throw new InvalidInputException("The written recipe has no usable ingredients");
        }
        if (w.steps != null) {
            w.steps.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(recipe.getInstructions()::add);
        }
        if (w.nutritionPerServing != null) {
            WrittenRecipe.Estimate e = w.nutritionPerServing;
            recipe.setNutrition(new NutritionSummary(e.calories, e.carbsG, e.proteinG, e.fatG, 0, Map.of(), true,
                    List.of(ESTIMATE_NOTE)));
        }
        return recipe;
    }

    /** Claude's estimate as it should be stored in the library, or null if the recipe has none. */
    public NutritionSummary savedEstimate(Recipe recipe) {
        NutritionSummary n = recipe.getNutritionPerServing();
        if (n == null || !n.getNotes().contains(ESTIMATE_NOTE)) {
            return null;
        }
        return new NutritionSummary(n.getCalories(), n.getCarbsG(), n.getProteinG(), n.getFatG(), 0, Map.of(), true,
                List.of(SAVED_NOTE));
    }

    /** Saves the ingredients the app did not know yet (id 0), so RecipeLibraryService.save() accepts them. */
    public void addNewIngredients(Recipe recipe) {
        for (RecipeIngredient ri : recipe.ingredients()) {
            Ingredient i = ri.getIngredient();
            if (i.getId() == 0 && ingredientRepository.findByName(i.getName()).isEmpty()) {
                ingredientRepository.save(i);
            }
        }
    }

    /**
     * Claude's amount and unit as a Quantity the app understands. Kitchen words are turned into app units
     * ("pinch" -> 1/8 tsp, "clove" or "large" -> piece, "oz" -> g), and "to taste" (no amount) becomes a pinch,
     * so one unusual line no longer throws the whole recipe away.
     */
    static Quantity quantity(WrittenRecipe.Line line) {
        String u = line.unit == null ? "" : line.unit.trim().toLowerCase(Locale.ROOT).replace(".", "");
        double amount = line.amount;
        if (amount <= 0) {
            return new Quantity(0.125, Unit.TSP); // "salt to taste": a pinch
        }
        switch (u) {
            case "pinch", "pinches", "dash", "dashes" -> { return new Quantity(amount * 0.125, Unit.TSP); }
            case "oz", "ounce", "ounces" -> { return new Quantity(amount * 28.35, Unit.G); }
            case "lb", "lbs", "pound", "pounds" -> { return new Quantity(amount * 453.6, Unit.G); }
            case "fl oz", "fluid ounce", "fluid ounces" -> { return new Quantity(amount * 29.57, Unit.ML); }
            case "", "clove", "cloves", "slice", "slices", "whole", "large", "medium", "small", "egg", "eggs",
                 "can", "cans", "bunch", "bunches", "sprig", "sprigs", "leaf", "leaves", "handful", "handfuls",
                 "stalk", "stalks", "head", "heads", "fillet", "fillets", "breast", "breasts" -> {
                return new Quantity(amount, Unit.PIECE);
            }
            default -> { }
        }
        try {
            return new Quantity(amount, Unit.parse(u));
        } catch (InvalidInputException e) {
            return new Quantity(amount, Unit.PIECE); // an unknown word: count it as pieces
        }
    }

    private static Ingredient newIngredient(WrittenRecipe.Line line, Unit unit) {
        double gramsPerPiece = unit == Unit.PIECE ? Math.max(0, line.gramsPerPiece) : 0;
        // density 1 g/ml (like water) lets cups and spoons be converted for shopping lists
        Ingredient ingredient = new Ingredient(0, line.name.trim().toLowerCase(Locale.ROOT), "Other", gramsPerPiece, 1.0);
        if (line.allergens != null) {
            line.allergens.stream().filter(a -> a != null && !a.isBlank())
                    .map(a -> a.trim().toLowerCase(Locale.ROOT)).forEach(ingredient.getAllergenTags()::add);
        }
        return ingredient;
    }

    /** "DISH_TYPE:Soup" or "Dish Type: Soup"; an unknown group becomes OTHER, a bad value is skipped. */
    private static RecipeCategory category(String text) {
        if (text == null || text.isBlank()) return null;
        int colon = text.indexOf(':');
        String name = (colon < 0 ? text : text.substring(colon + 1)).trim();
        if (name.isEmpty()) return null;
        CategoryGroup group = CategoryGroup.OTHER;
        if (colon > 0) {
            String g = text.substring(0, colon).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            try {
                group = CategoryGroup.valueOf(g);
            } catch (IllegalArgumentException e) {
                group = CategoryGroup.OTHER;
            }
        }
        return new RecipeCategory(group, name);
    }
}