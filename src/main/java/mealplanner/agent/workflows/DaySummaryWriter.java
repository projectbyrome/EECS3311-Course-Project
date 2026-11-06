package mealplanner.agent.workflows;

import java.util.List;
import java.util.Map;
import mealplanner.agent.PromptBuilder;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;

/**
 * F10 (SD07): asks Claude, in one request, for a short summary of the day with suggestions.
 * Claude only gets the numbers worked out by NutritionService; it does not calculate anything.
 */
public class DaySummaryWriter {

    static final int MAX_TOKENS = 300;
    /** Longer replies are cut, so the view stays readable. */
    static final int MAX_CHARS = 800;
    public static final String FALLBACK_NOTE = "(Simple summary: Claude could not be reached.)";

    private static final String INSTRUCTIONS = """
            You are the nutrition coach inside a meal-planning app.
            You get the totals of the meals a user planned for one day and their daily goals.
            Protein and fibre are minimums; calories, carbohydrates, fat and sodium are maximums. A goal of 0 means
            no goal. If fibre or sodium has no goal, you may still mention it when fibre is well under about 25 g
            or sodium is over about 2300 mg for the day ("n/a" means unknown; don't mention those).
            Write 2 to 4 short sentences (under 80 words): say how well the day meets the goals,
            then give one or two concrete suggestions (a food to add, swap or reduce).
            Only use the numbers you are given. If the totals are marked incomplete, say the real totals are higher.
            Plain text only: no markdown, no lists, no headings, no medical advice.""";

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;

    public DaySummaryWriter(LLMClient llmClient, PromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    /** SD07: write(totals, goal) -> send(request); on LLMUnavailableException or an empty reply -> fallback. */
    public String write(NutritionSummary totals, NutritionGoal goal) {
        try {
            LLMRequest request = promptBuilder.buildTextRequest(INSTRUCTIONS, summaryPrompt(totals, goal), MAX_TOKENS);
            String text = llmClient.send(request).text().strip();
            if (!text.isEmpty()) {
                return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS).strip() + "..." : text;
            }
        } catch (LLMUnavailableException e) {
            // 4a: fall through to the template
        }
        return fallback(totals, goal);
    }

    private String summaryPrompt(NutritionSummary totals, NutritionGoal goal) {
        StringBuilder sb = new StringBuilder("Totals for the day:\n");
        sb.append(String.format("- Calories: %.0f kcal%n", totals.getCalories()));
        sb.append(String.format("- Protein: %.1f g%n", totals.getProteinG()));
        sb.append(String.format("- Carbohydrates: %.1f g%n", totals.getCarbsG()));
        sb.append(String.format("- Fat: %.1f g%n", totals.getFatG()));
        sb.append(String.format("- Sugar: %.1f g%n", totals.getSugarG()));
        sb.append("- Fibre: ").append(totals.fiberText()).append(String.format("%n"));
        sb.append("- Sodium: ").append(totals.sodiumText()).append(String.format("%n"));
        totals.getVitamins().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("- %s: %.2f%n", e.getKey(), e.getValue())));
        sb.append(totals.isComplete() ? "Data: complete\n" : "Data: INCOMPLETE (some ingredients have no nutrition data)\n");
        sb.append("\nDaily goals:\n");
        sb.append(String.format("- Calories: max %.0f kcal%n", goal.getCalories()));
        sb.append(String.format("- Protein: min %.0f g%n", goal.getProteinG()));
        sb.append(String.format("- Carbohydrates: max %.0f g%n", goal.getCarbsG()));
        sb.append(String.format("- Fat: max %.0f g%n", goal.getFatG()));
        sb.append(String.format("- Fibre: min %.0f g%n", goal.getFiberG()));
        sb.append(String.format("- Sodium: max %.0f mg%n", goal.getSodiumMg()));
        List<String> misses = goal.misses(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        sb.append(String.format("%nGoal check (%.0f%% tolerance): ", NutritionGoal.DAILY_TOLERANCE_PCT));
        sb.append(misses.isEmpty() ? "all goals met" : "not met: " + String.join("; ", misses));
        return sb.toString();
    }

    /** UC10 4a: the same facts as sentences, with one suggestion per goal that is not met. */
    String fallback(NutritionSummary totals, NutritionGoal goal) {
        StringBuilder sb = new StringBuilder();
        if (goal.getCalories() > 0) {
            sb.append(String.format("You planned %.0f of your %.0f kcal (%.0f%%). ", totals.getCalories(),
                    goal.getCalories(), totals.getCalories() * 100 / goal.getCalories()));
        } else {
            sb.append(String.format("You planned %.0f kcal. ", totals.getCalories()));
        }
        List<String> misses = goal.misses(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        if (misses.isEmpty()) {
            sb.append("All your goals are met. ");
        } else {
            sb.append("Not met: ").append(String.join("; ", misses)).append(". ");
            for (String miss : misses) {
                sb.append(suggestion(miss)).append(' ');
            }
        }
        if (!totals.isComplete()) {
            sb.append("Some nutrition data is missing, so the real totals are higher. ");
        }
        return sb.append(FALLBACK_NOTE).toString();
    }

    private static String suggestion(String miss) {
        if (miss.startsWith("protein")) return "Add a protein-rich food such as eggs, chicken, tofu or beans.";
        if (miss.startsWith("calories")) return "Try smaller servings or a lighter meal.";
        if (miss.startsWith("carbohydrates")) return "Swap some bread, pasta or rice for vegetables.";
        return "Use less oil, butter or cheese.";
    }
}