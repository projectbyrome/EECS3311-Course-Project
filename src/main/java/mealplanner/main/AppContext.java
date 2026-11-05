package mealplanner.main;

import java.io.PrintStream;
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
import mealplanner.agent.workflows.DaySummaryWriter;
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
import mealplanner.cli.SummaryCommand;
import mealplanner.data.Database;
import mealplanner.data.DataAccessException;
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
import mealplanner.service.WrittenRecipeConverter;
import mealplanner.ui.ChecklistView;
import mealplanner.ui.MacroGeneratorView;
import mealplanner.ui.MainWindow;
import mealplanner.ui.MealCalendarView;
import mealplanner.ui.NutritionSummaryView;
import mealplanner.ui.PantryView;
import mealplanner.ui.ProfileView;
import mealplanner.ui.RecipeLibraryView;
import mealplanner.ui.RecommendationView;

/**
 * Composition root (CD10): reads the settings, builds every object once, wires the Observer
 * subscriptions and registers the CLI commands. Nothing else in the program creates services.
 * Built for F01-F10.
 */
public class AppContext {

    /** Starter recipes for a new, empty library (their nutrition is worked out the first time they are edited). */
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

    /** Same as create(), but with a different LLM client and nutrition source (used by tests). */
    public static AppContext create(AppConfig config, UiMode mode, LLMClient llm, NutritionSource nutrition) {
        AppContext context = new AppContext(config, mode);
        context.buildServices(llm, nutrition);
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
            PantryView pantryView = new PantryView(facade);
            MacroGeneratorView macroView = new MacroGeneratorView(facade);
            eventBus.subscribe(CalendarChanged.class, calendarView);
            eventBus.subscribe(ChecklistChanged.class, checklistView);
            eventBus.subscribe(PantryChanged.class, checklistView);
            // The Shopping page's pantry status also depends on the planned meals.
            eventBus.subscribe(CalendarChanged.class, checklistView);
            eventBus.subscribe(PantryChanged.class, pantryView);
            ProfileView profileView = new ProfileView(facade);
            RecommendationView recommendationView = new RecommendationView(facade);
            RecipeLibraryView libraryView = new RecipeLibraryView(facade);
            // Observer: saved, removed, imported or re-warned recipes redraw the library list.
            eventBus.subscribe(LibraryChanged.class, libraryView);
            NutritionSummaryView summaryView = new NutritionSummaryView(facade);
            // F10: the calendar's "Nutrition" buttons open the summary under the week (UC10 trigger).
            calendarView.setSummaryHandlers(summaryView::onShowDay, summaryView::onShowWeek);
            // The Profile's Daily goals card opens the same summary, and shows goals saved elsewhere.
            profileView.setSummaryHandlers(summaryView::onShowDay, summaryView::onShowWeek);
            eventBus.subscribe(ProfileChanged.class, profileView);
            // Observer: new goals, a changed day or an edited recipe redraw the summary shown.
            eventBus.subscribe(ProfileChanged.class, summaryView);
            eventBus.subscribe(CalendarChanged.class, summaryView);
            eventBus.subscribe(LibraryChanged.class, summaryView);
            mainWindow = new MainWindow(calendarView, checklistView, pantryView, macroView, libraryView,
                    profileView, recommendationView, summaryView);
        }
        return mainWindow;
    }

    public CliApp cliApp() {
        if (cliApp == null) {
            Scanner in = new Scanner(System.in);
            cliApp = new CliApp(in, System.out);
            registerCommands(cliApp, in, System.out);
        }
        return cliApp;
    }

    /** For tests: a CLI that reads and writes the given streams. */
    public CliApp cliApp(Scanner in, PrintStream out) {
        CliApp cli = new CliApp(in, out);
        registerCommands(cli, in, out);
        return cli;
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
                recipeLibraryService, profileService, responseParser, new WrittenRecipeConverter(ingredientRepo));

        DaySummaryWriter daySummaryWriter = new DaySummaryWriter(llm, promptBuilder);

        facade = new AppFacade(mealCalendarService, recipeLibraryService, pantryService, checklistService,
                shoppingListGenerator, macroGenerator, profileService, recipeRecommender, recipeCatalogService,
                nutritionService, daySummaryWriter);

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

    private void registerCommands(CliApp cli, Scanner in, PrintStream out) {
        cli.register(new CalendarCommand(facade, in, out));
        cli.register(new ChecklistCommand(facade, in, out));
        cli.register(new ShoppingCommand(facade, in, out));
        cli.register(new PantryCommand(facade, in, out));
        cli.register(new MacrosCommand(facade, in, out));
        cli.register(new ProfileCommand(facade, in, out));
        cli.register(new RecommendCommand(facade, in, out));
        cli.register(new RecipeCommand(facade, in, out));
        cli.register(new FavCommand(facade, in, out));
        cli.register(new SummaryCommand(facade, in, out));
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