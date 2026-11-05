package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mealplanner.agent.llm.ToolResult;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.model.RecommendationFilter;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeCatalogService;

/**
 * search_recipe_catalog (SD06): searches the built-in catalogue. The profile's dietary rules are always
 * applied here, whatever Claude asks, so an unsafe recipe never reaches Claude.
 */
public class SearchCatalogTool extends AbstractTool {

    static final int MAX_RESULTS = 10;

    private final RecipeCatalogService recipeCatalogService;
    private final ProfileService profileService;

    public SearchCatalogTool(RecipeCatalogService recipeCatalogService, ProfileService profileService) {
        this.recipeCatalogService = recipeCatalogService;
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "search_recipe_catalog";
    }

    @Override
    public String description() {
        return "Searches the built-in recipe catalogue. All fields are optional. The user's allergies and dietary "
                + "restrictions are always applied. The daily goal is compared with one serving as one third of a day. "
                + "Returns at most " + MAX_RESULTS + " recipes with their catalogId.";
    }

    @Override
    public JsonNode inputSchema() {
        return schema("""
                {"type": "object", "properties": {
                  "category": {"type": "string", "description": "one category name, e.g. Curry, Soup or Poultry (flavours such as Spicy are not searchable); omit for any"},
                  "maxCookMinutes": {"type": "integer", "description": "maximum cooking time in minutes"},
                  "dailyGoal": {"type": "object", "description": "daily goal; protein is a minimum, the rest maximums",
                    "properties": {"calories": {"type": "number"}, "proteinG": {"type": "number"},
                                   "carbsG": {"type": "number"}, "fatG": {"type": "number"}}}}}""");
    }

    @Override
    protected ToolResult doExecute(JsonNode args) {
        RecommendationFilter filter = new RecommendationFilter();
        filter.category = readString(args, "category");
        double minutes = readNumber(args, "maxCookMinutes");
        if (!Double.isNaN(minutes)) {
            filter.maxCookMinutes = (int) minutes;
        }
        JsonNode goal = args.get("dailyGoal");
        if (goal != null && goal.isObject()) {
            filter.goal = new NutritionGoal(num(goal, "calories"), num(goal, "proteinG"), num(goal, "carbsG"),
                    num(goal, "fatG"));
        }
        List<Recipe> found = recipeCatalogService.search(
                recipeCatalogService.buildFilters(filter, profileService.getProfile()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recipe r : found.subList(0, Math.min(MAX_RESULTS, found.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("catalogId", r.getCatalogId());
            m.put("name", r.getName());
            m.put("categories", r.getCategories().stream().map(RecipeCategory::toString).sorted().toList());
            m.put("cookMinutes", r.getCookMinutes());
            m.put("ingredients", r.ingredients().stream().map(RecipeIngredient::toString).toList());
            NutritionSummary n = r.getNutritionPerServing();
            if (n != null) {
                m.put("perServing", String.format("%.0f kcal, %.0f g protein, %.0f g carbs, %.0f g fat",
                        n.getCalories(), n.getProteinG(), n.getCarbsG(), n.getFatG()));
            }
            out.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", found.size());
        result.put("recipes", out);
        return ToolResult.ok(toJson(result));
    }

    private static double num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return 0;
        if (!v.isNumber()) throw new InvalidInputException("dailyGoal." + field + " must be a number");
        return v.asDouble();
    }
}