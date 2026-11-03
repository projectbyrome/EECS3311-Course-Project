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
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.UserProfile;
import mealplanner.model.Recommendation;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationPick;
import mealplanner.model.RecommendationResult;
import mealplanner.service.ProfileService;
import mealplanner.service.RecipeCatalogService;
import mealplanner.service.RecipeFilter;
import mealplanner.service.RecipeLibraryService;

/**
 * F07 Recipe Recommendations (SD06, UC07). The only workflow that uses the agent's tool loop: Claude
 * searches the catalogue with read-only tools and picks up to 3 recipes with reasons. Every pick is then
 * checked again in code (it must exist, pass the user's filters and break no dietary rule).
 * If Claude is unavailable, hits the step limit or gives an unusable answer, fallback() shows the first
 * recipes that pass the filters, without reasons (4a).
 */
public class RecipeRecommender {

    static final int MAX_PICKS = 3;
    static final List<String> TOOLS = List.of("get_profile", "search_recipe_library", "search_recipe_catalog");

    private static final String OUTPUT_FORMAT = """
            Reply with JSON only: an array of at most 3 objects, best first, e.g.
            [{"catalogId": 4, "reason": "one short sentence for the user"}]
            Use only catalogId values returned by search_recipe_catalog. Reply [] if nothing fits.""";

    private final AgentController agentController;
    private final RecipeCatalogService recipeCatalogService;
    private final RecipeLibraryService recipeLibraryService;
    private final ProfileService profileService;
    private final ResponseParser responseParser;

    public RecipeRecommender(AgentController agentController, RecipeCatalogService recipeCatalogService,
                             RecipeLibraryService recipeLibraryService, ProfileService profileService,
                             ResponseParser responseParser) {
        this.agentController = agentController;
        this.recipeCatalogService = recipeCatalogService;
        this.recipeLibraryService = recipeLibraryService;
        this.profileService = profileService;
        this.responseParser = responseParser;
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
        Set<Long> seen = new LinkedHashSet<>();
        for (RecommendationPick pick : picks) {
            if (cards.size() == MAX_PICKS || !seen.add(pick.catalogId)) continue;
            Recipe recipe;
            try {
                recipe = recipeCatalogService.getById(pick.catalogId);
            } catch (AppException e) {
                continue; // an id Claude made up
            }
            // safety net: the pick must pass the user's own filters and break no dietary rule
            if (!filters.stream().allMatch(f -> f.matches(recipe)) || !profile.violations(recipe).isEmpty()) {
                continue;
            }
            cards.add(new Recommendation(recipe, pick.reason));
        }
        if (cards.isEmpty()) {
            // Claude found nothing usable; if the catalogue has matches after all, show them without reasons
            return fallback(filter, profile, null);
        }
        return new RecommendationResult(cards, null);
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
        String goal = "Recommend up to " + MAX_PICKS + " recipes from the catalogue that match the user's filters. "
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