package mealplanner.agent;

import java.util.List;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMResponse;
import mealplanner.agent.llm.ToolResult;
import mealplanner.agent.llm.ToolUseBlock;
import mealplanner.agent.tools.ToolManager;

/**
 * Runs the agent's tool-use loop (CD1, SD06): send the task to Claude with the allowed tools; while Claude
 * asks for tools, run them through ToolManager and send the results back; stop at the final answer or after
 * maxSteps calls to Claude. LLMUnavailableException is passed to the caller, which falls back.
 */
public class AgentController {

    private final LLMClient llmClient;
    private final ToolManager toolManager;
    private final PromptBuilder promptBuilder;
    private final int maxSteps;

    public AgentController(LLMClient llmClient, ToolManager toolManager, PromptBuilder promptBuilder) {
        this(llmClient, toolManager, promptBuilder, 8);
    }

    public AgentController(LLMClient llmClient, ToolManager toolManager, PromptBuilder promptBuilder, int maxSteps) {
        this.llmClient = llmClient;
        this.toolManager = toolManager;
        this.promptBuilder = promptBuilder;
        this.maxSteps = maxSteps;
    }

    public AgentResult runTask(AgentTask task) {
        LLMRequest request = promptBuilder.buildTaskRequest(task, task.profile,
                toolManager.definitionsFor(task.allowedTools));
        return runToolLoop(request, task.allowedTools, new AgentTrace());
    }

    private AgentResult runToolLoop(LLMRequest request, List<String> allowedTools, AgentTrace trace) {
        for (int step = 1; step <= maxSteps; step++) {
            LLMResponse response = llmClient.send(request);
            trace.recordLlmCall(request, response);
            if (!response.hasToolUses()) {
                return new AgentResult(response.text(), true, step, trace);
            }
            request.addAssistantMessage(response);
            for (ToolUseBlock call : response.toolUses()) {
                ToolResult result = allowedTools.contains(call.name)
                        ? toolManager.execute(call)
                        : ToolResult.error("Tool '" + call.name + "' is not allowed for this task");
                trace.recordToolCall(call, result);
                request.addToolResult(call, result);
            }
        }
        return new AgentResult(null, false, maxSteps, trace);
    }
}