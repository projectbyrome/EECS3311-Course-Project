package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mealplanner.agent.llm.ToolDefinition;
import mealplanner.agent.llm.ToolResult;
import mealplanner.agent.llm.ToolUseBlock;

/**
 * Keeps the registered tools, gives Claude their definitions, and checks every call against the tool's
 * schema before running it (required fields, field types, no unknown fields).
 */
public class ToolManager {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public List<ToolDefinition> definitionsFor(List<String> names) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (String name : names) {
            Tool t = tools.get(name);
            if (t == null) {
                throw new IllegalArgumentException("No tool named " + name + " is registered");
            }
            defs.add(new ToolDefinition(t.name(), t.description(), t.inputSchema()));
        }
        return defs;
    }

    public ToolResult execute(ToolUseBlock call) {
        Tool tool = tools.get(call.name);
        if (tool == null) {
            return ToolResult.error("Unknown tool: " + call.name);
        }
        List<String> problems = validateArgs(tool, call.input);
        if (!problems.isEmpty()) {
            return ToolResult.error("Invalid arguments for " + call.name + ": " + String.join("; ", problems));
        }
        return tool.execute(call.input);
    }

    private List<String> validateArgs(Tool tool, JsonNode args) {
        List<String> problems = new ArrayList<>();
        if (args == null || args.isNull() || args.isMissingNode()) {
            args = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        if (!args.isObject()) {
            problems.add("input must be a JSON object");
            return problems;
        }
        JsonNode schema = tool.inputSchema();
        JsonNode properties = schema.path("properties");
        for (JsonNode required : schema.path("required")) {
            JsonNode v = args.get(required.asText());
            if (v == null || v.isNull()) {
                problems.add("missing " + required.asText());
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            JsonNode prop = properties.get(f.getKey());
            if (prop == null) {
                problems.add("unknown field " + f.getKey());
            } else if (!f.getValue().isNull() && !hasType(f.getValue(), prop.path("type").asText())) {
                problems.add(f.getKey() + " must be " + prop.path("type").asText());
            }
        }
        return problems;
    }

    private static boolean hasType(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber() || (value.isNumber() && value.asDouble() == Math.rint(value.asDouble()));
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> true;
        };
    }
}