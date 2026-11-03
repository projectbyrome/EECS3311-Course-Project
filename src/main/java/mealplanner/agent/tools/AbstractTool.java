package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mealplanner.agent.llm.ToolResult;
import mealplanner.domain.AppException;
import mealplanner.domain.InvalidInputException;

/**
 * Template Method: execute() is final and handles errors the same way for every tool; each tool only
 * writes doExecute(). An application error becomes ToolResult.error, so Claude sees it and can try again.
 */
public abstract class AbstractTool implements Tool {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public final ToolResult execute(JsonNode args) {
        try {
            return doExecute(args == null || args.isNull() ? JSON.createObjectNode() : args);
        } catch (AppException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("Tool " + name() + " failed: " + e.getMessage());
        }
    }

    protected abstract ToolResult doExecute(JsonNode args);

    /**  text value of the field, or null when it is missing. */
    protected String readString(JsonNode args, String field) {
        JsonNode v = args.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** number value of the field, or NaN when it is missing. */
    protected double readNumber(JsonNode args, String field) {
        JsonNode v = args.get(field);
        if (v == null || v.isNull()) {
            return Double.NaN;
        }
        if (!v.isNumber()) {
            throw new InvalidInputException(field + " must be a number");
        }
        return v.asDouble();
    }

    /** parses a schema written as a JSON string. */
    protected static JsonNode schema(String json) {
        try {
            return JSON.readTree(json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Bad schema", e);
        }
    }

    /** turns a result object into the JSON text sent back to Claude. */
    protected static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot write tool result", e);
        }
    }
}