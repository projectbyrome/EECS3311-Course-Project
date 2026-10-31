package mealplanner.agent.workflows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mealplanner.agent.PromptBuilder;
import mealplanner.agent.ResponseParseException;
import mealplanner.agent.ResponseParser;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMResponse;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Recipe;
import mealplanner.domain.ValidationReport;
import mealplanner.model.DraftItem;
import mealplanner.model.RecipeSelection;
import mealplanner.model.ShoppingDraft;
import mealplanner.model.ShoppingNeed;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeLibraryService;
import mealplanner.service.ShoppingDraftValidator;
import mealplanner.service.ShoppingNeedsCalculator;

/**
 * F03 Shopping List Generation (SD04). The exact quantities come from ShoppingNeedsCalculator;
 * Claude is sent one request to merge duplicate names and group the items by store section.
 * If Claude is unavailable or its answer fails the check, the plain list is used instead.
 */
public class ShoppingListGenerator {

    static final String EVERYTHING_IN_PANTRY = "Everything is already in your pantry";
    static final int MAX_TOKENS = 800;

    private static final String INSTRUCTIONS = """
            You tidy grocery lists. You get a list of items with exact quantities.
            Merge lines that are the same product, and give each item a store section
            (Produce, Meat & Fish, Dairy & Eggs, Bakery, Pantry, Frozen, Spices, Other).
            Rules: keep each item's name exactly as given; never add an item; never drop an item;
            never lower a quantity; keep the units as given.
            Reply with JSON only, in this form:
            {"items": [{"name": "eggs", "quantity": {"amount": 12, "unit": "PIECE"}, "category": "Dairy & Eggs"}]}""";

    private final ShoppingNeedsCalculator calculator;
    private final RecipeLibraryService recipeLibraryService;
    private final PantryService pantryService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;
    private final ShoppingDraftValidator validator;

    public ShoppingListGenerator(ShoppingNeedsCalculator calculator, RecipeLibraryService recipeLibraryService,
                                 PantryService pantryService, LLMClient llmClient, PromptBuilder promptBuilder,
                                 ResponseParser responseParser, ShoppingDraftValidator validator) {
        this.calculator = calculator;
        this.recipeLibraryService = recipeLibraryService;
        this.pantryService = pantryService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.validator = validator;
    }

    /** SD04: calculate the needs, ask Claude to tidy them, check the answer, fall back if needed. */
    public ShoppingDraft draft(List<RecipeSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new InvalidInputException("Choose at least one recipe.");
        }
        List<Recipe> recipes = new ArrayList<>();
        for (RecipeSelection s : selections) {
            recipes.add(recipeLibraryService.getById(s.recipeId));
        }
        List<ShoppingNeed> needs = calculator.calculate(selections, recipes, pantryService.getItems());
        if (needs.isEmpty()) {
            return ShoppingDraft.empty(EVERYTHING_IN_PANTRY); // UC03 2a
        }

        LLMRequest request = promptBuilder.buildTextRequest(INSTRUCTIONS, shoppingPrompt(needs), MAX_TOKENS);
        try {
            LLMResponse response = llmClient.send(request);
            ShoppingDraft draft = responseParser.parse(response.text(), ShoppingDraft.class);
            ValidationReport report = validator.validate(draft, needs);
            if (!report.isValid()) {
                return fallbackDraft(needs, "Claude's list did not match the exact amounts (" + report + ")");
            }
            if (draft.warnings == null) draft.warnings = new ArrayList<>();
            return draft;
        } catch (ResponseParseException e) {
            return fallbackDraft(needs, "Claude's reply could not be read");
        } catch (LLMUnavailableException e) {
            return fallbackDraft(needs, "Claude is unavailable"); // UC03 3a
        }
    }

    private String shoppingPrompt(List<ShoppingNeed> needs) {
        StringBuilder sb = new StringBuilder("Items to buy:\n");
        for (ShoppingNeed n : needs) {
            sb.append("- ").append(n.ingredient.getName())
                    .append(": ").append(round(n.quantity.getAmount())).append(' ').append(n.quantity.getUnit());
            if (n.ingredient.getCategory() != null) {
                sb.append(" (category hint: ").append(n.ingredient.getCategory()).append(')');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** The plain list straight from the calculator, grouped by the ingredient's own category. */
    private ShoppingDraft fallbackDraft(List<ShoppingNeed> needs, String reason) {
        List<DraftItem> items = new ArrayList<>();
        for (ShoppingNeed n : needs) {
            String category = n.ingredient.getCategory() == null ? "Other" : n.ingredient.getCategory();
            items.add(new DraftItem(n.ingredient.getName(), n.quantity, category));
        }
        items.sort(Comparator.comparing((DraftItem d) -> d.category).thenComparing(d -> d.name));
        return new ShoppingDraft(items, List.of(reason + "; showing the plain list."));
    }

    private static String round(double amount) {
        double r = Math.ceil(amount * 100) / 100; // round up so the prompt never lowers an amount
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}