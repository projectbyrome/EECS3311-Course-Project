package mealplanner.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMResponse;
import mealplanner.agent.llm.ToolResult;
import mealplanner.agent.llm.ToolUseBlock;

/** Record of one agent run (each LLM call and tool call), for debugging and for the Stage 3 tests. */
public class AgentTrace {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<TraceStep> steps = new ArrayList<>();

    public void recordLlmCall(LLMRequest request, LLMResponse response) {
        String detail = "stop=" + response.stopReason + ", tools asked=" + response.toolUses().size()
                + ", tokens in/out=" + response.inputTokens + "/" + response.outputTokens;
        steps.add(new TraceStep("llm", "messages:" + request.messages.size(), detail));
    }

    public void recordToolCall(ToolUseBlock call, ToolResult result) {
        String content = result.content == null ? "" : result.content;
        steps.add(new TraceStep("tool", call.name, "input=" + call.input + ", "
                + (result.success ? "ok" : "error") + ": " + (content.length() > 200 ? content.substring(0, 200) + "..." : content)));
    }

    public List<TraceStep> getSteps() {
        return List.copyOf(steps);
    }

    public int toolCallCount(String toolName) {
        return (int) steps.stream().filter(s -> s.kind.equals("tool") && s.name.equals(toolName)).count();
    }

    public String toJson() {
        ArrayNode array = JSON.createArrayNode();
        for (TraceStep s : steps) {
            ObjectNode o = array.addObject();
            o.put("kind", s.kind);
            o.put("name", s.name);
            o.put("detail", s.detail);
        }
        return array.toString();
    }
}