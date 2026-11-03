import com.fasterxml.jackson.databind.ObjectMapper;
import mealplanner.agent.llm.ToolResult;
import mealplanner.agent.llm.ToolUseBlock;
import mealplanner.agent.tools.GetProfileTool;
import mealplanner.agent.tools.SearchCatalogTool;
import mealplanner.agent.tools.ToolManager;
import mealplanner.data.Database;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteCatalogRepository;
import mealplanner.data.SqliteIngredientRepository;
import mealplanner.data.SqliteProfileRepository;
import mealplanner.events.EventBus;
import mealplanner.main.AppConfig;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeCatalogService;

/** Manual check of the agent tools (F07): runs each tool the way Claude would ask for it. */
public class ToolsCheck {

    public static void main(String[] args) throws Exception {
        Database db = new Database(AppConfig.fromEnvironment().dbPath);
        db.migrate();
        db.seedIfEmpty(new SeedLoader());
        ProfileService profiles = new ProfileService(new SqliteProfileRepository(db),
                new SqliteIngredientRepository(db), new EventBus());
        RecipeCatalogService catalog = new RecipeCatalogService(new SqliteCatalogRepository(db), null);

        ToolManager tools = new ToolManager();
        tools.register(new GetProfileTool(profiles));
        tools.register(new SearchCatalogTool(catalog, profiles));

        ObjectMapper json = new ObjectMapper();
        print(tools.execute(new ToolUseBlock("1", "get_profile", json.readTree("{}"))));
        print(tools.execute(new ToolUseBlock("2", "search_recipe_catalog",
                json.readTree("{\"maxCookMinutes\": 15}"))));
        print(tools.execute(new ToolUseBlock("3", "search_recipe_catalog",
                json.readTree("{\"category\": \"Curry\"}"))));
        // A bad call: ToolManager checks it against the schema and returns an error instead of running it.
        print(tools.execute(new ToolUseBlock("4", "search_recipe_catalog", json.readTree("{\"colour\": \"red\"}"))));
    }

    private static void print(ToolResult result) {
        System.out.println((result.success ? "OK: " : "ERROR: ") + result.content + "\n");
    }
}