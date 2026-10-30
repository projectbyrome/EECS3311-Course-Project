package mealplanner.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the Anthropic Messages API (POST /v1/messages). Turns LLMRequest into the API's JSON,
 * the reply back into an LLMResponse, and every network or HTTP problem into LLMUnavailableException.
 */
public class ClaudeClient implements LLMClient {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    public ClaudeClient(String apiKey, String model, Duration timeout) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(timeout).build(), timeout);
    }

    public ClaudeClient(String apiKey, String model, HttpClient http, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = http;
        this.timeout = timeout;
    }

    @Override
    public LLMResponse send(LLMRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LLMUnavailableException("No Anthropic API key is set");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(ENDPOINT)
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LLMUnavailableException("Claude could not be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMUnavailableException("Request to Claude was interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new LLMUnavailableException("Claude returned HTTP " + response.statusCode());
        }
        return fromJson(response.body());
    }

    String toJson(LLMRequest request) {
        ObjectNode root = json.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", request.maxTokens);
        if (request.system != null && !request.system.isBlank()) {
            root.put("system", request.system);
        }
        ArrayNode messages = root.putArray("messages");
        for (Message m : request.messages) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role.name().toLowerCase(Locale.ROOT));
            ArrayNode content = msg.putArray("content");
            for (ContentBlock block : m.content) {
                ObjectNode b = content.addObject();
                b.put("type", block.type());
                if (block instanceof TextBlock t) {
                    b.put("text", t.text);
                } else if (block instanceof ToolUseBlock u) {
                    b.put("id", u.id);
                    b.put("name", u.name);
                    b.set("input", u.input);
                } else if (block instanceof ToolResultBlock r) {
                    b.put("tool_use_id", r.toolUseId);
                    b.put("content", r.content);
                    b.put("is_error", r.isError);
                }
            }
        }
        if (!request.tools.isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition d : request.tools) {
                ObjectNode t = tools.addObject();
                t.put("name", d.name);
                t.put("description", d.description);
                t.set("input_schema", d.inputSchema);
            }
        }
        try {
            return json.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write request JSON", e);
        }
    }

    LLMResponse fromJson(String body) {
        try {
            JsonNode root = json.readTree(body);
            List<ContentBlock> blocks = new ArrayList<>();
            for (JsonNode b : root.path("content")) {
                switch (b.path("type").asText()) {
                    case "text" -> blocks.add(new TextBlock(b.path("text").asText()));
                    case "tool_use" -> blocks.add(new ToolUseBlock(b.path("id").asText(),
                            b.path("name").asText(), b.path("input")));
                    default -> { } // other block types are ignored
                }
            }
            JsonNode usage = root.path("usage");
            return new LLMResponse(blocks, root.path("stop_reason").asText(),
                    usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt());
        } catch (IOException e) {
            throw new LLMUnavailableException("Claude's reply was not valid JSON", e);
        }
    }
}