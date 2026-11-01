package mealplanner.agent.workflows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mealplanner.agent.PromptBuilder;
import mealplanner.agent.ResponseParseException;
import mealplanner.agent.ResponseParser;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.domain.Ingredient;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.external.NutritionSourceException;
import mealplanner.model.FoodCandidate;
import mealplanner.model.IngredientPortion;
import mealplanner.model.NutritionSelection;
import mealplanner.model.RecipePortion;
import mealplanner.service.NutritionService;
import mealplanner.service.RecipeLibraryService;

/**
 * F05 Macro Generator (SD05). The numbers always come from the nutrition database, never from Claude.
 * Claude is only asked to pick which USDA food matches an ingredient that has not been matched yet.
 * If Claude is unavailable, the top USDA match is used and the result says "check match" (UC05 3a).
 */
public class MacroGenerator {

    static final int MAX_TOKENS = 200;

    private static final String INSTRUCTIONS = """
            You match a recipe ingredient to a food in the USDA FoodData Central database.
            You get the ingredient name and a numbered list of candidate foods.
            Pick the candidate that best describes the ingredient as it is usually bought
            (prefer raw or plain forms unless the name says otherwise).
            You must pick one of the given fdcId values.
            Reply with JSON only, in this form: {"fdcId": "123456", "reason": "short reason"}""";

    private final NutritionService nutritionService;
    private final RecipeLibraryService recipeLibraryService;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public MacroGenerator(NutritionService nutritionService, RecipeLibraryService recipeLibraryService,
                          LLMClient llmClient, PromptBuilder promptBuilder, ResponseParser responseParser) {
        this.nutritionService = nutritionService;
        this.recipeLibraryService = recipeLibraryService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    /** SD05: validate, match unmatched ingredients to USDA foods, then add up the nutrients. */
    public NutritionSummary generate(List<NutritionSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new InvalidInputException("Choose at least one recipe or ingredient.");
        }
        // 1a: check everything before any network call, so a bad entry changes nothing.
        Map<NutritionSelection, Object> resolved = new LinkedHashMap<>();
        // every Ingredient object by id: two recipes may hold separate copies of the same ingredient
        Map<Long, List<Ingredient>> ingredients = new LinkedHashMap<>();
        for (NutritionSelection s : selections) {
            if (s instanceof RecipePortion rp) {
                if (!(rp.servings > 0)) {
                    throw new InvalidInputException("Servings must be greater than 0");
                }
                Recipe recipe = recipeLibraryService.getById(rp.recipeId); // unknown recipe -> InvalidInputException
                resolved.put(s, recipe);
                for (RecipeIngredient ri : recipe.ingredients()) {
                    ingredients.computeIfAbsent(ri.getIngredient().getId(), k -> new ArrayList<>()).add(ri.getIngredient());
                }
            } else if (s instanceof IngredientPortion ip) {
                if (ip.amount == null || !(ip.amount.getAmount() > 0)) {
                    throw new InvalidInputException("Amount for " + ip.ingredientName + " must be greater than 0");
                }
                Ingredient ingredient = nutritionService.findIngredient(ip.ingredientName)
                        .orElseThrow(() -> new InvalidInputException("Unknown ingredient: " + ip.ingredientName));
                resolved.put(s, ingredient);
                ingredients.computeIfAbsent(ingredient.getId(), k -> new ArrayList<>()).add(ingredient);
            } else {
                throw new InvalidInputException("Unsupported selection: " + s);
            }
        }

        // 3: match each ingredient that has no USDA food yet.
        List<String> notes = matchFoods(ingredients.values().stream().flatMap(List::stream).toList());

        // 4-5: nutrients for each selection, added together.
        NutritionSummary total = NutritionSummary.zero();
        for (Map.Entry<NutritionSelection, Object> e : resolved.entrySet()) {
            if (e.getKey() instanceof RecipePortion rp) {
                total = total.plus(nutritionService.recipeNutrition((Recipe) e.getValue(), rp.servings));
            } else {
                IngredientPortion ip = (IngredientPortion) e.getKey();
                total = total.plus(nutritionService.ingredientNutrition((Ingredient) e.getValue(), ip.amount));
            }
        }
        for (String note : notes) {
            total = total.withNote(note);
        }
        return total;
    }

    /**
     * SD05 step 3, also used by F08 when a recipe is saved (SD03 "lookup as in SD05"): finds the USDA food
     * of every ingredient that has none yet. Returns the notes to show ("check match", database unreachable).
     */
    public List<String> matchFoods(List<Ingredient> ingredients) {
        // every Ingredient object by id: two recipes may hold separate copies of the same ingredient
        Map<Long, List<Ingredient>> byId = new LinkedHashMap<>();
        for (Ingredient i : ingredients) {
            byId.computeIfAbsent(i.getId(), k -> new ArrayList<>()).add(i);
        }
        List<String> notes = new ArrayList<>();
        for (List<Ingredient> copies : byId.values()) {
            Ingredient ingredient = copies.get(0);
            if (nutritionService.findFoodId(ingredient).isEmpty()) {
                resolveFood(ingredient, notes).ifPresent(id -> copies.forEach(c -> c.setFdcId(id)));
            }
        }
        return notes;
    }

    /**
     * Finds the USDA food for one ingredient. Claude picks among the candidates when there is more than
     * one; a confirmed match is saved. If Claude is unavailable or picks an id that is not a candidate,
     * the top candidate is used for this calculation only and a "check match" note is added.
     */
    private Optional<String> resolveFood(Ingredient ingredient, List<String> notes) {
        List<FoodCandidate> candidates;
        try {
            candidates = nutritionService.searchCandidates(ingredient.getName());
        } catch (NutritionSourceException e) {
            notes.add("Nutrition database could not be reached to match " + ingredient.getName());
            return Optional.empty(); // 4a: the summary is marked incomplete for this ingredient
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        FoodCandidate top = candidates.get(0);
        if (candidates.size() == 1) {
            nutritionService.saveFoodId(ingredient, top.fdcId);
            return Optional.of(top.fdcId);
        }
        try {
            LLMRequest request = promptBuilder.buildTextRequest(INSTRUCTIONS,
                    foodMatchPrompt(ingredient.getName(), candidates), MAX_TOKENS);
            String picked = responseParser.parseField(llmClient.send(request).text(), "fdcId").trim();
            if (candidates.stream().anyMatch(c -> c.fdcId.equals(picked))) {
                nutritionService.saveFoodId(ingredient, picked);
                return Optional.of(picked);
            }
        } catch (LLMUnavailableException | ResponseParseException e) {
            // fall through to the top candidate
        }
        notes.add("check match: " + ingredient.getName() + " -> " + top.description);
        return Optional.of(top.fdcId);
    }

    private String foodMatchPrompt(String name, List<FoodCandidate> candidates) {
        StringBuilder sb = new StringBuilder("Ingredient: ").append(name).append("\nCandidates:\n");
        for (int i = 0; i < candidates.size(); i++) {
            FoodCandidate c = candidates.get(i);
            sb.append(i + 1).append(". fdcId ").append(c.fdcId).append(": ").append(c.description).append('\n');
        }
        return sb.toString();
    }
}