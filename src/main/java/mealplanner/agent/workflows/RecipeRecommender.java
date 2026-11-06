package mealplanner.agent.workflows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mealplanner.agent.AgentController;
import mealplanner.agent.AgentResult;
import mealplanner.agent.AgentTask;
import mealplanner.agent.ResponseParseException;
import mealplanner.agent.ResponseParser;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.domain.AppException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.UserProfile;
import mealplanner.model.Recommendation;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationPick;
import mealplanner.model.RecommendationResult;
import mealplanner.model.WrittenRecipe;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.RecipeFilter;
import mealplanner.service.RecipeLibraryService;
import mealplanner.service.WrittenRecipeConverter;

/**
 * F07 Recipe Recommendations (SD06, UC07). The only workflow that uses the agent's tool loop: Claude
 * searches the catalogue with read-only tools and picks up to 3 recipes with reasons. Every pick is then
 * checked again in code (it must exist, pass the user's filters and break no dietary rule).
 * If Claude is unavailable, hits the step limit or gives an unusable answer, fallback() shows the first
 * recipes that pass the filters, without reasons (4a).
 * Extra: when the catalogue has no good fit, Claude may write a new recipe itself. WrittenRecipeConverter
 * turns it into a Recipe, and it gets the same checks; addWritten() saves it to the library when approved.
 * When the user asks for a specific dish ("omelette") that the catalogue doesn't have, Claude writes that dish
 * instead of offering different ones. If a written recipe is left out by the checks, the message says why.
 */
public class RecipeRecommender {

    static final int MAX_PICKS = 3;
    static final List<String> TOOLS = List.of("get_profile", "search_recipe_library", "search_recipe_catalog");

    private static final String OUTPUT_FORMAT = """
            Reply with JSON only: an array of at most 3 objects, best first, e.g.
            [{"catalogId": 4, "reason": "one short sentence for the user"}]
            Use only catalogId values returned by search_recipe_catalog.
            Prefer catalogue recipes. If fewer than 3 catalogue recipes fit well, or the user asked for a dish the
            catalogue doesn't have, write new recipes yourself, in this form instead of catalogId:
            {"newRecipe": {"name": "Miso Glazed Salmon", "servings": 2, "cookMinutes": 25,
              "categories": ["DISH_TYPE:Main Course", "MAIN_INGREDIENT:Fish", "COOKING_METHOD:Baked"],
              "ingredients": [{"name": "salmon fillet", "amount": 300, "unit": "g", "allergens": ["fish"]},
                              {"name": "miso paste", "amount": 2, "unit": "tbsp", "allergens": ["soy"]},
                              {"name": "egg", "amount": 2, "unit": "piece", "gramsPerPiece": 50, "allergens": ["egg"]}],
              "steps": ["Heat the oven to 200 °C (400 °F).",
                        "Whisk the miso with 1 tbsp water for about 30 seconds, until smooth, and brush it over the salmon.",
                        "Bake for about 12-15 minutes, until the salmon flakes easily and the glaze is browned at the edges."],
              "nutritionPerServing": {"calories": 420, "proteinG": 35, "carbsG": 12, "fatG": 24}},
             "reason": "one short sentence for the user"}
            Write the steps of a new recipe in full; never shorten or leave out a step. Every step that takes time
            (whisking, kneading, frying, boiling, simmering, baking, roasting, resting, marinating, ...) gives a time
            or a range and what to look for when it is done ("until golden", "until doubled in size", "until stiff
            peaks form"). Give the heat level (low, medium, medium-high, high) or the oven temperature in °C and °F
            whenever something is cooked. Times are estimates, so write "about" before them (e.g. "for about 3-5
            minutes"); never make an estimate look exact.
            A new recipe must follow every strict rule and the user's filters. Units: g, kg, ml, l, tsp, tbsp, cup or
            piece. List every allergen of each ingredient, using words like peanut, tree nut, milk, egg, soy, gluten,
            fish, shellfish, sesame. Use simple, common ingredient names. Reply [] if nothing fits.""";

    private final AgentController agentController;
    private final RecipeCatalogService recipeCatalogService;
    private final RecipeLibraryService recipeLibraryService;
    private final ProfileService profileService;
    private final ResponseParser responseParser;
    /** Null means Claude's new recipes are ignored and only catalogue recipes are shown. */
    private final WrittenRecipeConverter converter;

    public RecipeRecommender(AgentController agentController, RecipeCatalogService recipeCatalogService,
                             RecipeLibraryService recipeLibraryService, ProfileService profileService,
                             ResponseParser responseParser) {
        this(agentController, recipeCatalogService, recipeLibraryService, profileService, responseParser, null);
    }

    public RecipeRecommender(AgentController agentController, RecipeCatalogService recipeCatalogService,
                             RecipeLibraryService recipeLibraryService, ProfileService profileService,
                             ResponseParser responseParser, WrittenRecipeConverter converter) {
        this.agentController = agentController;
        this.recipeCatalogService = recipeCatalogService;
        this.recipeLibraryService = recipeLibraryService;
        this.profileService = profileService;
        this.responseParser = responseParser;
        this.converter = converter;
    }

    public RecommendationResult recommend(RecommendationFilter filter) {
        UserProfile profile = profileService.getProfile();
        List<RecipeFilter> filters = recipeCatalogService.buildFilters(filter, profile); // bad values -> InvalidInputException
        List<Recipe> favourites = recipeLibraryService.favourites();

        AgentResult result;
        try {
            result = agentController.runTask(buildTask(filter, favourites, profile));
        } catch (LLMUnavailableException e) {
            return fallback(filter, profile, "Claude is unavailable, so these are the first recipes that match your filters.");
        }
        if (!result.completed) {
            return fallback(filter, profile, "The assistant took too many steps, so these are the first recipes that match your filters.");
        }
        List<RecommendationPick> picks;
        try {
            picks = responseParser.parseList(result.finalText, RecommendationPick.class);
        } catch (ResponseParseException e) {
            return fallback(filter, profile, "The assistant's answer could not be read, so these are the first recipes that match your filters.");
        }

        List<Recommendation> cards = new ArrayList<>();
        List<String> leftOut = new ArrayList<>(); // written recipes the checks removed, with the reason
        Set<Long> seen = new LinkedHashSet<>();
        for (RecommendationPick pick : picks) {
            if (cards.size() == MAX_PICKS) break;
            boolean written = pick.newRecipe != null;
            if (!written && !seen.add(pick.catalogId)) continue;
            Recipe recipe;
            try {
                recipe = written ? toRecipe(pick.newRecipe) : recipeCatalogService.getById(pick.catalogId);
            } catch (AppException e) {
                // an id Claude made up (skipped quietly), or a written recipe the app cannot read (named in the note)
                if (written) leftOut.add((pick.newRecipe.name == null ? "a recipe" : pick.newRecipe.name)
                        + " (couldn't be read: " + e.getMessage() + ")");
                continue;
            }
            if (recipe == null) continue;
            // safety net: the pick must pass the user's own filters and break no dietary rule
            List<String> broken = profile.violations(recipe);
            if (!broken.isEmpty()) {
                if (written) leftOut.add(recipe.getName() + " (" + String.join("; ", broken) + ")");
                continue;
            }
            if (!filters.stream().allMatch(f -> f.matches(recipe))) {
                if (written) leftOut.add(recipe.getName() + " (doesn't match your filters, e.g. category or cooking time)");
                continue;
            }
            cards.add(new Recommendation(recipe, pick.reason, written));
        }
        String note = leftOut.isEmpty() ? null
                : "Claude wrote " + String.join(", ", leftOut) + ", but it was left out.";
        if (cards.isEmpty()) {
            // Claude found nothing usable; if the catalogue has matches after all, show them without reasons
            RecommendationResult shown = fallback(filter, profile, note);
            return note == null || !shown.isEmpty() ? shown : new RecommendationResult(List.of(), note);
        }
        return new RecommendationResult(cards, note);
    }

    /**
     * Adds a recipe Claude wrote to the library: new ingredients are saved first, then RecipeLibraryService.save()
     * checks it, works out the nutrition (USDA, as in SD03) and adds the profile's warnings. If USDA cannot work
     * the nutrition out, Claude's estimate is kept instead.
     */
    public Recipe addWritten(Recipe recipe) {
        if (converter == null) {
            throw new IllegalStateException("Written recipes are not enabled");
        }
        NutritionSummary estimate = converter.savedEstimate(recipe);
        converter.addNewIngredients(recipe);
        Recipe saved = recipeLibraryService.save(recipe);
        return recipeLibraryService.keepEstimateIfIncomplete(saved, estimate);
    }

    private Recipe toRecipe(WrittenRecipe written) {
        return converter == null ? null : converter.toRecipe(written);
    }

    private AgentTask buildTask(RecommendationFilter filter, List<Recipe> favourites, UserProfile profile) {
        StringBuilder context = new StringBuilder("Filters: ").append(filter == null ? "none" : filter.describe());
        if (filter != null && filter.goal != null && !filter.goal.isEmpty()) {
            context.append(String.format("%nDaily goal for search_recipe_catalog: calories %.0f, proteinG %.0f, carbsG %.0f, fatG %.0f (0 = no limit)",
                    filter.goal.getCalories(), filter.goal.getProteinG(), filter.goal.getCarbsG(), filter.goal.getFatG()));
        }
        context.append("\nFavourite recipes (rank similar recipes higher): ");
        context.append(favourites.isEmpty() ? "none" : String.join(", ", favourites.stream()
                .map(r -> r.getName() + (r.getCategories().isEmpty() ? "" : " (" + String.join(", ",
                        r.getCategories().stream().map(RecipeCategory::getName).toList()) + ")")).toList()));
        if (filter != null && filter.hasRequest()) {
            context.append("\nWhat the user asked for, in their own words: \"").append(filter.request.trim()).append("\"")
                    .append("\nThis is a wish, not a category name: search without a category if needed.")
                    .append("\nIf it names a specific dish (for example \"omelette\" or \"banana bread\"), the user wants ")
                    .append("that dish: use a catalogue recipe for it if one exists; otherwise write that dish yourself as ")
                    .append("a newRecipe and put it first. Do not swap it for a different dish. You may add up to 2 ")
                    .append("variations of it (e.g. a cheese omelette and a vegetable omelette).")
                    .append("\nIf it is a general wish (\"something light\", \"quick and spicy\"), pick the catalogue ")
                    .append("recipes that fit it best, and write new ones only if fewer than 3 fit well.")
                    .append("\nThe strict rules and filters still apply: if the dish breaks one (e.g. an egg allergy), ")
                    .append("don't write it; pick the closest safe recipes and say why in the reason.");
        }
        String goal = "Recommend up to " + MAX_PICKS + " recipes that match the user's filters, from the catalogue when it has them. "
                + "Use search_recipe_catalog with the filters below, and say in each reason why the recipe fits.";
        return new AgentTask(goal, context.toString(), TOOLS, OUTPUT_FORMAT, profile);
    }

    /**
     * 4a / 5a: the first recipes that pass the filters, catalogue recipes the user has as favourites first,
     * then recipes with a favourite category; "No recipes found. Try loosening your filters." if there are none.
     */
    private RecommendationResult fallback(RecommendationFilter filter, UserProfile profile, String message) {
        List<Recipe> matches = new ArrayList<>(recipeCatalogService.search(
                recipeCatalogService.buildFilters(filter, profile)));
        if (matches.isEmpty()) {
            return RecommendationResult.nothingFound();
        }
        Set<Long> favouriteIds = new LinkedHashSet<>();
        recipeLibraryService.favourites().forEach(r -> { if (r.getCatalogId() != null) favouriteIds.add(r.getCatalogId()); });
        Set<String> favouriteCategories = profile.getFavouriteCategories();
        matches.sort(Comparator
                .comparing((Recipe r) -> !favouriteIds.contains(r.getCatalogId()))
                .thenComparing(r -> favouriteCategories.stream().noneMatch(r::hasCategory)));
        List<Recommendation> cards = new ArrayList<>();
        for (Recipe r : matches.subList(0, Math.min(MAX_PICKS, matches.size()))) {
            cards.add(new Recommendation(r, null));
        }
        return new RecommendationResult(cards, message);
    }
}