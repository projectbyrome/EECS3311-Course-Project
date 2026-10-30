package mealplanner.agent.llm;

import java.util.ArrayList;
import java.util.List;

/** Everything sent to Claude in one call: system prompt, conversation, tools and the token limit. */
public class LLMRequest {

    public String system;
    public final List<Message> messages = new ArrayList<>();
    public final List<ToolDefinition> tools = new ArrayList<>();
    public int maxTokens;

    public LLMRequest(String system, int maxTokens) {
        this.system = system;
        this.maxTokens = maxTokens;
    }

    public void addUserText(String text) {
        messages.add(new Message(Role.USER, List.of(new TextBlock(text))));
    }

    /** Keeps Claude's reply in the conversation (needed before sending tool results back). */
    public void addAssistantMessage(LLMResponse response) {
        messages.add(new Message(Role.ASSISTANT, response.content));
    }

    /**
     * Adds the answer to one tool call. Answers to several calls from the same reply go into one user
     * message, as the Messages API expects.
     */
    public void addToolResult(ToolUseBlock call, ToolResult result) {
        ToolResultBlock block = new ToolResultBlock(call.id, result.content, !result.success);
        Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (last != null && last.role == Role.USER && !last.content.isEmpty()
                && last.content.stream().allMatch(b -> b instanceof ToolResultBlock)) {
            last.content.add(block);
        } else {
            messages.add(new Message(Role.USER, List.of(block)));
        }
    }
}