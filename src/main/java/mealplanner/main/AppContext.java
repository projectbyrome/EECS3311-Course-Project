package mealplanner.main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.cli.CalendarCommand;
import mealplanner.cli.ChecklistCommand;
import mealplanner.cli.CliApp;
import mealplanner.data.DataAccessException;
import mealplanner.data.Database;
import mealplanner.data.RecipeRepository;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteCalendarRepository;
import mealplanner.data.SqliteChecklistRepository;
import mealplanner.data.SqliteIngredientRepository;
import mealplanner.data.SqlitePantryRepository;
import mealplanner.data.SqliteRecipeRepository;
import mealplanner.domain.Recipe;
import mealplanner.events.CalendarChanged;
import mealplanner.events.ChecklistChanged;
import mealplanner.events.EventBus;
import mealplanner.events.PantryChanged;
import mealplanner.service.ChecklistService;
import mealplanner.service.MealCalendarService;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeLibraryService;
import mealplanner.service.UnitConverter;
import mealplanner.ui.ChecklistView;
import mealplanner.ui.MainWindow;
import mealplanner.ui.MealCalendarView;

/**
 * Composition root (CD10): reads the settings, builds every object once, wires the Observer
 * subscriptions and registers the CLI commands. Nothing else in the program creates services.
 */
public class AppContext {

    /** Starter recipes for a new, empty library. */
    static final String SAMPLE_RECIPES_JSON = "/data/sample-recipes.json";

    private final AppConfig config;
    private final UiMode mode;
    private final EventBus eventBus = new EventBus();
    private AppFacade facade;
    private MealCalendarService mealCalendarService;
    private MainWindow mainWindow;
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

    /** Built on first use, because JavaFX controls must be created on the JavaFX thread. */
    public MainWindow mainWindow() {
        if (mode != UiMode.GUI) {
            throw new IllegalStateException("The window is only built in GUI mode");
        }
        if (mainWindow == null) {
            MealCalendarView calendarView = new MealCalendarView(facade);
            ChecklistView checklistView = new ChecklistView(facade);
            eventBus.subscribe(CalendarChanged.class, calendarView);
            eventBus.subscribe(ChecklistChanged.class, checklistView);
            eventBus.subscribe(PantryChanged.class, checklistView);
            mainWindow = new MainWindow(calendarView, checklistView);
        }
        return mainWindow;
    }

    public CliApp cliApp() {
        if (cliApp == null) {
            Scanner in = new Scanner(System.in);
            cliApp = new CliApp(in, System.out);
            cliApp.register(new CalendarCommand(facade, in, System.out));
            cliApp.register(new ChecklistCommand(facade, in, System.out));
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
        ChecklistService checklistService = new ChecklistService(new SqliteChecklistRepository(db),
                pantryService, eventBus);

        facade = new AppFacade(mealCalendarService, recipeLibraryService, pantryService, checklistService);

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