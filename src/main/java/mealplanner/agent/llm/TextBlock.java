package mealplanner.agent.llm;

/** Plain text content. */
public class TextBlock extends ContentBlock {

    public final String text;

    public TextBlock(String text) {
        this.text = text;
    }

    @Override
    public String type() {
        return "text";
    }
}