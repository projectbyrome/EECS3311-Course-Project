package mealplanner.agent.llm;

import java.util.ArrayList;
import java.util.List;

/** One turn of the conversation: a role and its content blocks. */
public class Message {

    public final Role role;
    public final List<ContentBlock> content;

    public Message(Role role, List<ContentBlock> content) {
        this.role = role;
        this.content = new ArrayList<>(content);
    }
}