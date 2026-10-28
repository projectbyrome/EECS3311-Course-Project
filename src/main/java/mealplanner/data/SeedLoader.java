package mealplanner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mealplanner.domain.CategoryGroup;
import mealplanner.domain.Ingredient;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;

/**
 * Reads the bundled JSON data files. A path starting with "/" is looked up on the classpath first
 * (src/main/resources), otherwise it is read as a normal file.
 */
public class SeedLoader {

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Format: [{"name": "eggs", "category": "Dairy & Eggs", "gramsPerPiece": 50, "densityGPerMl": 0,
     *           "allergens": ["egg"], "fdcId": null}, ...]
     */
    public List<Ingredient> loadIngredients(String path) {
        List<Ingredient> list = new ArrayList<>();
        for (JsonNode n : read(path)) {
            Ingredient i = new Ingredient(0, n.path("name").asText().trim().toLowerCase(),
                    n.path("category").asText("Other"),
                    n.path("gramsPerPiece").asDouble(0), n.path("densityGPerMl").asDouble(0));
            for (JsonNode tag : n.path("allergens")) {
                i.getAllergenTags().add(tag.asText());
            }
            if (n.hasNonNull("fdcId")) {
                i.setFdcId(n.get("fdcId").asText());
            }
            list.add(i);
        }
        return list;
    }

    /**
     * Temporary sample recipes so the app can be tried before the Recipe Library (F08) exists. Format:
     * [{"name": "Omelette", "servings": 1, "cuisine": "French", "cookMinutes": 10,
     *   "ingredients": [{"name": "eggs", "amount": 3, "unit": "PIECE"}], "instructions": ["..."]}]
     * Lines whose ingredient is not in the ingredient list are skipped.
     */
    public List<Recipe> loadSampleRecipes(String path, IngredientRepository ingredients) {
        List<Recipe> list = new ArrayList<>();
        for (JsonNode n : read(path)) {
            Recipe r = new Recipe(0, n.path("name").asText(), n.path("servings").asInt(1));
            readCategories(n, r);
            r.setCookMinutes(n.path("cookMinutes").asInt(0));
            for (JsonNode step : n.path("instructions")) {
                r.getInstructions().add(step.asText());
            }
            for (JsonNode line : n.path("ingredients")) {
                ingredients.findByName(line.path("name").asText()).ifPresent(i -> r.addIngredient(
                        new RecipeIngredient(i, new Quantity(line.path("amount").asDouble(),
                                Unit.valueOf(line.path("unit").asText().toUpperCase())))));
            }
            list.add(r);
        }
        return list;
    }

    /**
     * The built-in recipe catalogue for recommendations (F07). Same format as the sample recipes, plus
     * "nutritionPerServing": {"calories": .., "carbsG": .., "proteinG": .., "fatG": .., "sugarG": ..}.
     * Unlike the sample recipes, an unknown ingredient is an error: the catalogue ships with the app.
     */
    public List<Recipe> loadCatalog(String path, IngredientRepository ingredients) {
        List<Recipe> list = new ArrayList<>();
        for (JsonNode n : read(path)) {
            Recipe r = new Recipe(0, n.path("name").asText(), n.path("servings").asInt(1));
            readCategories(n, r);
            r.setCookMinutes(n.path("cookMinutes").asInt(0));
            for (JsonNode step : n.path("instructions")) {
                r.getInstructions().add(step.asText());
            }
            for (JsonNode line : n.path("ingredients")) {
                String name = line.path("name").asText();
                Ingredient i = ingredients.findByName(name).orElseThrow(() -> new DataAccessException(
                        path + ": recipe " + r.getName() + " uses unknown ingredient " + name, null));
                r.addIngredient(new RecipeIngredient(i, new Quantity(line.path("amount").asDouble(),
                        Unit.valueOf(line.path("unit").asText().toUpperCase()))));
            }
            JsonNode nut = n.path("nutritionPerServing");
            if (nut.isObject()) {
                r.setNutrition(new NutritionSummary(nut.path("calories").asDouble(), nut.path("carbsG").asDouble(),
                        nut.path("proteinG").asDouble(), nut.path("fatG").asDouble(), nut.path("sugarG").asDouble(),
                        Map.of(), true, List.of()));
            }
            list.add(r);
        }
        return list;
    }

    /** Format: "categories": {"DISH_TYPE": ["Omelettes"], "FLAVOR": ["Savory", "Cheesy"]} */
    private static void readCategories(JsonNode n, Recipe r) {
        n.path("categories").fields().forEachRemaining(group -> {
            CategoryGroup g = CategoryGroup.valueOf(group.getKey());
            for (JsonNode value : group.getValue()) {
                r.addCategory(new RecipeCategory(g, value.asText()));
            }
        });
    }

    private JsonNode read(String path) {
        try (InputStream in = open(path)) {
            JsonNode root = json.readTree(in);
            if (!root.isArray()) {
                throw new DataAccessException(path + " must contain a JSON array", null);
            }
            return root;
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + path, e);
        }
    }

    private InputStream open(String path) throws IOException {
        InputStream resource = SeedLoader.class.getResourceAsStream(path);
        if (resource != null) {
            return resource;
        }
        return Files.newInputStream(Path.of(path));
    }
}
