import mealplanner.data.Database;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteIngredientRepository;
import mealplanner.data.SqliteRecipeRepository;
import mealplanner.domain.Recipe;

/** Manual check of the data layer (Step 2): creates datacheck.db, loads the starter data and reads it back. */
public class DataCheck {

    public static void main(String[] args) {
        Database db = new Database("datacheck.db");
        db.migrate();
        SeedLoader loader = new SeedLoader();
        db.seedIfEmpty(loader);

        SqliteIngredientRepository ingredients = new SqliteIngredientRepository(db);
        SqliteRecipeRepository recipes = new SqliteRecipeRepository(db);
        if (recipes.findAll().isEmpty()) {
            for (Recipe r : loader.loadSampleRecipes("/data/sample-recipes.json", ingredients)) {
                recipes.save(r);
            }
        }
        System.out.println("Ingredients known: " + ingredients.search("").size());
        for (Recipe r : recipes.findAll()) {
            System.out.println(r.getId() + " " + r.getName() + " " + r.getCategories() + " " + r.ingredients());
        }
    }
}
 