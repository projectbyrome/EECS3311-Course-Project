package mealplanner.agent;

/** One entry of an AgentTrace: an LLM call or a tool call, with a short summary. */
public class TraceStep {

    public final String kind;
    public final String name;
    public final String detail;

    public TraceStep(String kind, String name, String detail) {
        this.kind = kind;
        this.name = name;
        this.detail = detail;
    }
}