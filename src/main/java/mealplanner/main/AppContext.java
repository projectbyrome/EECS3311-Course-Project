package mealplanner.main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.cli.CalendarCommand;
import mealplanner.cli.CliApp;
import mealplanner.data.DataAccessException;
import mealplanner.data.Database;
import mealplanner.data.RecipeRepository;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteCalendarRepository;
import mealplanner.data.SqliteIngredientRepository;
import mealplanner.data.SqlitePantryRepository;
import mealplanner.data.SqliteRecipeRepository;
import mealplanner.domain.Recipe;
import mealplanner.events.EventBus;
import mealplanner.events.PantryChanged;
import mealplanner.service.MealCalendarService;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeLibraryService;
import mealplanner.service.UnitConverter;

/**
 * Composition root (CD10): reads the settings, builds every object once, wires the Observer
 * subscriptions and registers the CLI commands.
 */
public class AppContext {

    /** Starter recipes for a new, empty library. */
    static final String SAMPLE_RECIPES_JSON = "/data/sample-recipes.json";

    private final AppConfig config;
    private final UiMode mode;
    private final EventBus eventBus = new EventBus();
    private AppFacade facade;
    private MealCalendarService mealCalendarService;
    private CliApp cliApp;

    private AppContext(AppConfig config, UiMode mode) {
        this.config = config;
        this.mode = mode;
    }

    public static AppContext create(AppConfig config, UiMode mode) {
        AppContext context = new AppContext(config, mode);
        context.buildServices();
        return context;
    }

    public AppFacade facade() {
        return facade;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public CliApp cliApp() {
        if (cliApp == null) {
            Scanner in = new Scanner(System.in);
            cliApp = new CliApp(in, System.out);
            cliApp.register(new CalendarCommand(facade, in, System.out));
        }
        return cliApp;
    }

    private void buildServices() {
        Database db = openDatabase();
        SeedLoader seedLoader = new SeedLoader();
        db.seedIfEmpty(seedLoader);

        SqliteIngredientRepository ingredientRepo = new SqliteIngredientRepository(db);
        RecipeRepository recipeRepo = new SqliteRecipeRepository(db);
        seedSampleRecipes(seedLoader, ingredientRepo, recipeRepo);

        UnitConverter converter = new UnitConverter();

        PantryService pantryService = new PantryService(new SqlitePantryRepository(db), ingredientRepo,
                converter, eventBus);
        RecipeLibraryService recipeLibraryService = new RecipeLibraryService(recipeRepo);
        mealCalendarService = new MealCalendarService(new SqliteCalendarRepository(db, recipeRepo),
                recipeLibraryService, pantryService, eventBus);

        facade = new AppFacade(mealCalendarService, recipeLibraryService);

        // Observer: the pantry changing re-checks the calendar flags (UC01 *a, UC04 step 5).
        eventBus.subscribe(PantryChanged.class, mealCalendarService);
    }

    private Database openDatabase() {
        try {
            Path parent = Path.of(config.dbPath).toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (java.io.IOException e) {
            throw new DataAccessException("Cannot create the folder for " + config.dbPath, e);
        }
        Database db = new Database(config.dbPath);
        db.migrate();
        return db;
    }

    private static void seedSampleRecipes(SeedLoader loader, SqliteIngredientRepository ingredients,
                                          RecipeRepository recipes) {
        if (!recipes.findAll().isEmpty()) {
            return;
        }
        for (Recipe r : loader.loadSampleRecipes(SAMPLE_RECIPES_JSON, ingredients)) {
            recipes.save(r);
        }
    }
}