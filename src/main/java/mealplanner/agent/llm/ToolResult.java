package mealplanner.agent.llm;

/** What a tool returned (JSON text), or an error message. */
public class ToolResult {

    public final boolean success;
    public final String content;

    private ToolResult(boolean success, String content) {
        this.success = success;
        this.content = content;
    }

    public static ToolResult ok(String json) {
        return new ToolResult(true, json);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message);
    }
}