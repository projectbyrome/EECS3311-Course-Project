package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import mealplanner.agent.llm.ToolResult;

/** A read-only tool Claude can ask the program to run (CD2). */
public interface Tool {

    String name();

    String description();

    /** JSON Schema of the input object, as sent to Claude. */
    JsonNode inputSchema();

    ToolResult execute(JsonNode args);
}