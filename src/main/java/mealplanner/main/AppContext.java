package mealplanner.main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Scanner;
import mealplanner.agent.AgentController;
import mealplanner.agent.PromptBuilder;
import mealplanner.agent.ResponseParser;
import mealplanner.agent.llm.ClaudeClient;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.tools.GetProfileTool;
import mealplanner.agent.tools.SearchCatalogTool;
import mealplanner.agent.tools.SearchLibraryTool;
import mealplanner.agent.tools.ToolManager;
import mealplanner.agent.workflows.MacroGenerator;
import mealplanner.agent.workflows.RecipeRecommender;
import mealplanner.agent.workflows.ShoppingListGenerator;
import mealplanner.app.AppFacade;
import mealplanner.cli.CalendarCommand;
import mealplanner.cli.ChecklistCommand;
import mealplanner.cli.CliApp;
import mealplanner.cli.FavCommand;
import mealplanner.cli.MacrosCommand;
import mealplanner.cli.PantryCommand;
import mealplanner.cli.ProfileCommand;
import mealplanner.cli.RecipeCommand;
import mealplanner.cli.RecommendCommand;
import mealplanner.cli.ShoppingCommand;
import mealplanner.data.DataAccessException;
import mealplanner.data.Database;
import mealplanner.data.RecipeRepository;
import mealplanner.data.SeedLoader;
import mealplanner.data.SqliteCalendarRepository;
import mealplanner.data.SqliteCatalogRepository;
import mealplanner.data.SqliteChecklistRepository;
import mealplanner.data.SqliteIngredientRepository;
import mealplanner.data.SqliteNutritionCacheRepository;
import mealplanner.data.SqlitePantryRepository;
import mealplanner.data.SqliteProfileRepository;
import mealplanner.data.SqliteRecipeRepository;
import mealplanner.domain.Recipe;
import mealplanner.events.CalendarChanged;
import mealplanner.events.ChecklistChanged;
import mealplanner.events.EventBus;
import mealplanner.events.LibraryChanged;
import mealplanner.events.PantryChanged;
import mealplanner.events.ProfileChanged;
import mealplanner.external.CachedNutritionSource;
import mealplanner.external.NutritionSource;
import mealplanner.external.UsdaFoodDataClient;
import mealplanner.service.ChecklistService;
import mealplanner.service.MealCalendarService;
import mealplanner.service.NutritionService;
import mealplanner.service.PantryService;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.RecipeLibraryService;
import mealplanner.service.ShoppingDraftValidator;
import mealplanner.service.ShoppingNeedsCalculator;
import mealplanner.service.UnitConverter;
import mealplanner.ui.ChecklistView;
import mealplanner.ui.MacroGeneratorView;
import mealplanner.ui.MainWindow;
import mealplanner.ui.MealCalendarView;
import mealplanner.ui.PantryView;
import mealplanner.ui.ProfileView;
import mealplanner.ui.RecipeLibraryView;
import mealplanner.ui.RecommendationView;

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
            PantryView pantryView = new PantryView(facade);
            eventBus.subscribe(PantryChanged.class, checklistView);
            eventBus.subscribe(PantryChanged.class, pantryView);
            MacroGeneratorView macroView = new MacroGeneratorView(facade);
            ProfileView profileView = new ProfileView(facade);
            RecommendationView recommendationView = new RecommendationView(facade);
            RecipeLibraryView libraryView = new RecipeLibraryView(facade);
            // Observer: saved, removed, imported or re-warned recipes redraw the library list.
            eventBus.subscribe(LibraryChanged.class, libraryView);
            mainWindow = new MainWindow(calendarView, checklistView, pantryView, macroView, libraryView,
                    profileView, recommendationView);
        }
        return mainWindow;
    }

    public CliApp cliApp() {
        if (cliApp == null) {
            Scanner in = new Scanner(System.in);
            cliApp = new CliApp(in, System.out);
            cliApp.register(new CalendarCommand(facade, in, System.out));
            cliApp.register(new ChecklistCommand(facade, in, System.out));
            cliApp.register(new ShoppingCommand(facade, in, System.out));
            cliApp.register(new PantryCommand(facade, in, System.out));
            cliApp.register(new MacrosCommand(facade, in, System.out));
            cliApp.register(new ProfileCommand(facade, in, System.out));
            cliApp.register(new RecommendCommand(facade, in, System.out));
            cliApp.register(new RecipeCommand(facade, in, System.out));
            cliApp.register(new FavCommand(facade, in, System.out));
        }
        return cliApp;
    }

    private void buildServices() {
        LLMClient llm = new ClaudeClient(config.anthropicApiKey, config.model, Duration.ofSeconds(30));
        buildServices(llm, new UsdaFoodDataClient(config.usdaApiKey));
    }

    private void buildServices(LLMClient llm, NutritionSource usda) {
        Database db = openDatabase();
        SeedLoader seedLoader = new SeedLoader();
        db.seedIfEmpty(seedLoader);

        SqliteIngredientRepository ingredientRepo = new SqliteIngredientRepository(db);
        RecipeRepository recipeRepo = new SqliteRecipeRepository(db);
        seedSampleRecipes(seedLoader, ingredientRepo, recipeRepo);

        UnitConverter converter = new UnitConverter();
        PromptBuilder promptBuilder = new PromptBuilder();
        ResponseParser responseParser = new ResponseParser();

        PantryService pantryService = new PantryService(new SqlitePantryRepository(db), ingredientRepo,
                converter, eventBus);
        NutritionService nutritionService = new NutritionService(
                new CachedNutritionSource(usda, new SqliteNutritionCacheRepository(db)), ingredientRepo, converter);
        ProfileService profileService = new ProfileService(new SqliteProfileRepository(db), ingredientRepo, eventBus);
        RecipeCatalogService recipeCatalogService = new RecipeCatalogService(new SqliteCatalogRepository(db),
                nutritionService);
        RecipeLibraryService recipeLibraryService = new RecipeLibraryService(recipeRepo, ingredientRepo,
                recipeCatalogService, nutritionService, profileService, eventBus);
        mealCalendarService = new MealCalendarService(new SqliteCalendarRepository(db, recipeRepo),
                recipeLibraryService, pantryService, eventBus);
        ChecklistService checklistService = new ChecklistService(new SqliteChecklistRepository(db),
                pantryService, eventBus);

        ShoppingListGenerator shoppingListGenerator = new ShoppingListGenerator(
                new ShoppingNeedsCalculator(converter), recipeLibraryService, pantryService, llm,
                promptBuilder, responseParser, new ShoppingDraftValidator(converter));
        MacroGenerator macroGenerator = new MacroGenerator(nutritionService, recipeLibraryService, llm,
                promptBuilder, responseParser);
        // F08: saving a recipe matches its ingredients to USDA foods like the Macro Generator (SD03 -> SD05).
        recipeLibraryService.setFoodMatcher(macroGenerator);

        ToolManager toolManager = new ToolManager();
        registerTools(toolManager, profileService, recipeLibraryService, recipeCatalogService);
        AgentController agentController = new AgentController(llm, toolManager, promptBuilder);
        RecipeRecommender recipeRecommender = new RecipeRecommender(agentController, recipeCatalogService,
                recipeLibraryService, profileService, responseParser);

        facade = new AppFacade(mealCalendarService, recipeLibraryService, pantryService, checklistService,
                shoppingListGenerator, macroGenerator, profileService, recipeRecommender, recipeCatalogService);

        // Observer: the pantry changing re-checks the calendar flags (UC01 *a, UC04 step 5).
        eventBus.subscribe(PantryChanged.class, mealCalendarService);
        // Observer: a saved profile re-checks the recipe warnings (UC06 step 5).
        eventBus.subscribe(ProfileChanged.class, recipeLibraryService);
    }

    /** The three read-only tools Claude may call (CD2). */
    private static void registerTools(ToolManager manager, ProfileService profileService,
                                      RecipeLibraryService recipeLibraryService,
                                      RecipeCatalogService recipeCatalogService) {
        manager.register(new GetProfileTool(profileService));
        manager.register(new SearchLibraryTool(recipeLibraryService));
        manager.register(new SearchCatalogTool(recipeCatalogService, profileService));
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
