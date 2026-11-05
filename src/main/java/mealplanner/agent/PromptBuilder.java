package mealplanner.agent;

import java.util.List;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.ToolDefinition;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.UserProfile;

/**
 * Builds the requests sent to Claude from text the workflows supply, so the agent core knows
 * nothing about individual features.
 */
public class PromptBuilder {

    /** A single-turn request (F03, F05, F10): instructions as the system prompt, the input as the user message. */
    public LLMRequest buildTextRequest(String instructions, String input, int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        LLMRequest request = new LLMRequest(instructions, maxTokens);
        request.addUserText(input);
        return request;
    }

    /** Room for up to 3 recipes written by Claude (F07 extra). */
    static final int TASK_MAX_TOKENS = 4000;

    /** A tool-using request (F07): profile rules and answer format in the system prompt, the goal as the user message. */
    public LLMRequest buildTaskRequest(AgentTask task, UserProfile profile, List<ToolDefinition> tools) {
        String system = systemPrompt(profile);
        if (task.outputFormat != null && !task.outputFormat.isBlank()) {
            system += "\n\nAnswer format for your final answer:\n" + task.outputFormat;
        }
        LLMRequest request = new LLMRequest(system, TASK_MAX_TOKENS);
        request.tools.addAll(tools);
        String input = task.goal + (task.context == null || task.context.isBlank() ? "" : "\n\n" + task.context);
        request.addUserText(input);
        return request;
    }

    /** Restrictions and allergies are stated as strict rules, ahead of the favourite categories (preferences). */
    private String systemPrompt(UserProfile profile) {
        StringBuilder sb = new StringBuilder("""
                You are the assistant inside a recipe and meal-planning app.
                You can read the user's data only through the tools you are given; you cannot change any data.
                Never invent recipes or ids: only use recipes returned by the tools.""");
        if (profile != null) {
            sb.append("\n\nSTRICT RULES (never break these):");
            sb.append("\n- Dietary restrictions: ").append(profile.getRestrictions().isEmpty() ? "none"
                    : String.join(", ", profile.getRestrictions().stream().map(DietaryRestriction::label).toList()));
            sb.append("\n- Allergies (the recipe must not contain them): ").append(profile.getAllergies().isEmpty()
                    ? "none" : String.join(", ", profile.getAllergies()));
            sb.append("\n\nPreferences (use them to rank, they are not rules):");
            sb.append("\n- Favourite recipe categories: ").append(profile.getFavouriteCategories().isEmpty() ? "none"
                    : String.join(", ", profile.getFavouriteCategories().stream().sorted().toList()));
        }
        return sb.toString();
    }
}