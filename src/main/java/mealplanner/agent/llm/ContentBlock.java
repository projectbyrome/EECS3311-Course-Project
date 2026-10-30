package mealplanner.agent.llm;

/** One part of a message (Anthropic Messages API): text, a tool call, or a tool result. */
public abstract class ContentBlock {

    /** "text", "tool_use" or "tool_result", as in the API JSON. */
    public abstract String type();
}