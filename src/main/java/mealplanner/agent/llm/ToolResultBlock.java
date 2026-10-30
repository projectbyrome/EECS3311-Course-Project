package mealplanner.agent.llm;

/** The answer to a ToolUseBlock; carries the id of the call it answers. */
public class ToolResultBlock extends ContentBlock {

    public final String toolUseId;
    public final String content;
    public final boolean isError;

    public ToolResultBlock(String toolUseId, String content, boolean isError) {
        this.toolUseId = toolUseId;
        this.content = content;
        this.isError = isError;
    }

    @Override
    public String type() {
        return "tool_result";
    }
}