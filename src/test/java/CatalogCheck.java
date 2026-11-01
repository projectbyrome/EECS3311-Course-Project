import java.util.List;
import mealplanner.data.Database;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteCatalogRepository;
import mealplanner.domain.Recipe;
import mealplanner.main.AppConfig;
import mealplanner.service.CategoryFilter;
import mealplanner.service.CookTimeFilter;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.RecipeFilter;

/** Manual check of the recipe catalogue (F07): lists the categories and the quick recipes. */
public class CatalogCheck {

    public static void main(String[] args) {
        Database db = new Database(AppConfig.fromEnvironment().dbPath);
        db.migrate();
        try {
            db.seedIfEmpty(new SeedLoader());
        } catch (RuntimeException e) {
            System.out.println("Cause: " + e.getCause());
            throw e;
        }
        RecipeCatalogService catalog = new RecipeCatalogService(new SqliteCatalogRepository(db), null);
        System.out.println("Categories: " + catalog.categories());
        List<RecipeFilter> filters = List.of(new CookTimeFilter(20));
        for (Recipe r : catalog.search(filters)) {
            System.out.println(r.getName() + " (" + r.getCookMinutes() + " min): " + r.getCategories());
        }
        System.out.println("Curries ready in 30 minutes or less:");
        for (Recipe r : catalog.search(List.of(new CategoryFilter("Curry"), new CookTimeFilter(30)))) {
            System.out.println("  " + r.getName());
        }
    }
}