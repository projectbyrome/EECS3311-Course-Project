package mealplanner.agent;

import mealplanner.agent.llm.LLMRequest;

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

    // TODO (F07): buildTaskRequest(AgentTask task, UserProfile profile, List<ToolDefinition> tools)
    //             and systemPrompt(UserProfile profile)
}