package mealplanner.agent.llm;

import java.util.List;
import java.util.stream.Collectors;

/** Claude's reply: content blocks, why it stopped, and token counts. */
public class LLMResponse {

    public final List<ContentBlock> content;
    public final String stopReason;
    public final int inputTokens;
    public final int outputTokens;

    public LLMResponse(List<ContentBlock> content, String stopReason, int inputTokens, int outputTokens) {
        this.content = List.copyOf(content);
        this.stopReason = stopReason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    /** All text blocks joined together. */
    public String text() {
        return content.stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text)
                .collect(Collectors.joining("\n"));
    }

    public List<ToolUseBlock> toolUses() {
        return content.stream()
                .filter(b -> b instanceof ToolUseBlock)
                .map(b -> (ToolUseBlock) b)
                .toList();
    }

    public boolean hasToolUses() {
        return !toolUses().isEmpty();
    }
}