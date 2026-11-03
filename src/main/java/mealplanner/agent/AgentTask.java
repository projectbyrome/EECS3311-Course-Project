package mealplanner.agent;

import java.util.List;
import mealplanner.domain.UserProfile;

/**
 * What the agent should do: the goal, extra context, which tools Claude may use, and the answer format.
 * The profile is passed along so the system prompt can state the strict dietary rules.
 */
public class AgentTask {

    public final String goal;
    public final String context;
    public final List<String> allowedTools;
    public final String outputFormat;
    public final UserProfile profile;

    public AgentTask(String goal, String context, List<String> allowedTools, String outputFormat,
                     UserProfile profile) {
        this.goal = goal;
        this.context = context;
        this.allowedTools = List.copyOf(allowedTools);
        this.outputFormat = outputFormat;
        this.profile = profile;
    }
}