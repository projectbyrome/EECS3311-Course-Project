package mealplanner.agent.workflows;

import java.util.Locale;
import mealplanner.agent.PromptBuilder;
import mealplanner.agent.ResponseParseException;
import mealplanner.agent.ResponseParser;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.model.IngredientCheck;

/**
 * Extra: asks Claude about an ingredient name that isn't in the ingredient list yet (Pantry, Macro Generator).
 * Claude says whether it is a food and gives its simple name, category, allergens and weight, so the new
 * ingredient gets real allergen tags and unit conversions. Nutrition still comes from USDA, never from Claude.
 * If Claude is unavailable or the reply can't be read, the typed name is used as it is (category Other).
 */
public class IngredientChecker {

    static final int MAX_TOKENS = 300;

    private static final String INSTRUCTIONS = """
            You check a food ingredient name typed into a meal-planning app, before it is added to the app's
            ingredient list. Reply with JSON only, in this form:
            {"food": true, "name": "mayonnaise", "category": "Condiments",
             "allergens": ["egg"], "gramsPerPiece": 0, "densityGPerMl": 0.91, "note": "Egg and oil sauce."}
            - food: false if the name is not something people cook with or eat (then the other fields don't matter).
            - name: the simple, common singular name in lower case, fixing spelling (e.g. "mayo" -> "mayonnaise").
            - category: one of Produce, Meat & Fish, Dairy & Eggs, Grains & Bread, Canned & Jarred, Condiments,
              Spices & Herbs, Baking, Snacks, Drinks, Frozen, Other.
            - allergens: every common allergen it usually contains, using: peanut, tree nut, milk, egg, soy,
              gluten, fish, shellfish, sesame. Use [] if none.
            - gramsPerPiece: weight of one usual piece in grams, or 0 if it isn't counted in pieces.
            - densityGPerMl: grams per millilitre for liquids, sauces and powders, or 0 if unknown.
            - note: one short sentence saying what it is.""";

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public IngredientChecker(LLMClient llmClient, PromptBuilder promptBuilder, ResponseParser responseParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    public IngredientCheck check(String typedName) {
        IngredientCheck fallback = IngredientCheck.unchecked(typedName);
        if (fallback.name.isEmpty()) {
            return fallback;
        }
        try {
            LLMRequest request = promptBuilder.buildTextRequest(INSTRUCTIONS, "Ingredient: " + fallback.name, MAX_TOKENS);
            IngredientCheck check = responseParser.parse(llmClient.send(request).text(), IngredientCheck.class);
            check.name = check.name == null || check.name.isBlank() ? fallback.name
                    : check.name.trim().toLowerCase(Locale.ROOT);
            if (check.category == null || check.category.isBlank()) check.category = "Other";
            if (check.allergens == null) check.allergens = new java.util.ArrayList<>();
            check.allergens.replaceAll(a -> a.trim().toLowerCase(Locale.ROOT));
            check.gramsPerPiece = Math.max(0, check.gramsPerPiece);
            check.densityGPerMl = Math.max(0, check.densityGPerMl);
            if (check.note == null) check.note = "";
            check.checkedByClaude = true;
            return check;
        } catch (LLMUnavailableException | ResponseParseException e) {
            return fallback;
        }
    }
}