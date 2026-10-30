package mealplanner.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** Claude asks the program to run a tool (used by the F07 agent loop). */
public class ToolUseBlock extends ContentBlock {

    public final String id;
    public final String name;
    public final JsonNode input;

    public ToolUseBlock(String id, String name, JsonNode input) {
        this.id = id;
        this.name = name;
        this.input = input;
    }

    @Override
    public String type() {
        return "tool_use";
    }
}