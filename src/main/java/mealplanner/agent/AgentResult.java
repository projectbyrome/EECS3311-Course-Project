package mealplanner.agent;

/** Claude's final answer, whether it finished within the step limit, and the trace of what happened. */
public class AgentResult {

    public final String finalText;
    public final boolean completed;
    public final int steps;
    public final AgentTrace trace;

    public AgentResult(String finalText, boolean completed, int steps, AgentTrace trace) {
        this.finalText = finalText;
        this.completed = completed;
        this.steps = steps;
        this.trace = trace;
    }
}