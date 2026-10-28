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
import mealplanner.domain.Ingredient;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;

public class SeedLoader {

    private final ObjectMapper json = new ObjectMapper();

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

    /** Sample recipes to try the app before the Recipe Library (F08) exists. Unknown ingredients are skipped. */
    public List<Recipe> loadSampleRecipes(String path, IngredientRepository ingredients) {
        List<Recipe> list = new ArrayList<>();
        for (JsonNode n : read(path)) {
            Recipe r = new Recipe(0, n.path("name").asText(), n.path("servings").asInt(1));
            r.setCuisine(n.path("cuisine").asText(null));
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

    /** The built-in recipe catalogue (F07). Like the samples, plus nutrition. Unknown ingredients are an error. */
    public List<Recipe> loadCatalog(String path, IngredientRepository ingredients) {
        List<Recipe> list = new ArrayList<>();
        for (JsonNode n : read(path)) {
            Recipe r = new Recipe(0, n.path("name").asText(), n.path("servings").asInt(1));
            r.setCuisine(n.path("cuisine").asText(null));
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