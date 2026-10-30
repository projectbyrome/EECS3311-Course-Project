package mealplanner.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool Claude may call: name, description and JSON input schema (used by F07). */
public class ToolDefinition {

    public final String name;
    public final String description;
    public final JsonNode inputSchema;

    public ToolDefinition(String name, String description, JsonNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
}