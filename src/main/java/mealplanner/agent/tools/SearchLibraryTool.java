package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mealplanner.agent.llm.ToolResult;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.service.RecipeLibraryService;

/** search_recipe_library: recipes already in the user's library (name contains the query), with favourites. */
public class SearchLibraryTool extends AbstractTool {

    private final RecipeLibraryService recipeLibraryService;

    public SearchLibraryTool(RecipeLibraryService recipeLibraryService) {
        this.recipeLibraryService = recipeLibraryService;
    }

    @Override
    public String name() {
        return "search_recipe_library";
    }

    @Override
    public String description() {
        return "Searches the user's own recipe library by name (empty query = all). Shows which recipes are "
                + "favourites and which catalogue recipe each one came from.";
    }

    @Override
    public JsonNode inputSchema() {
        return schema("""
                {"type": "object", "properties": {
                  "query": {"type": "string", "description": "part of a recipe name, or empty for all"},
                  "favouritesOnly": {"type": "boolean"}}}""");
    }

    @Override
    protected ToolResult doExecute(JsonNode args) {
        String query = readString(args, "query");
        boolean favouritesOnly = args.path("favouritesOnly").asBoolean(false);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recipe r : recipeLibraryService.search(query == null ? "" : query)) {
            if (favouritesOnly && !r.isFavourite()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("categories", r.getCategories().stream().map(RecipeCategory::toString).sorted().toList());
            m.put("cookMinutes", r.getCookMinutes());
            m.put("favourite", r.isFavourite());
            m.put("catalogId", r.getCatalogId());
            out.add(m);
        }
        return ToolResult.ok(toJson(out));
    }
}