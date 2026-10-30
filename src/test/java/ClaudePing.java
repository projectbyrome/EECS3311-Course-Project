import java.time.Duration;
import mealplanner.agent.llm.ClaudeClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMResponse;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.main.AppConfig;

/** Manual check that the API key in .env works: sends one short message to Claude. */
public class ClaudePing {

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        ClaudeClient client = new ClaudeClient(config.anthropicApiKey, config.model, Duration.ofSeconds(30));
        LLMRequest request = new LLMRequest("You are a friendly cooking assistant.", 100);
        request.addUserText("Say hello and name one easy breakfast in one sentence.");
        try {
            LLMResponse response = client.send(request);
            System.out.println(response.text());
            System.out.println("Tokens: " + response.inputTokens + " in, " + response.outputTokens + " out");
        } catch (LLMUnavailableException e) {
            System.out.println("Claude is not available: " + e.getMessage());
        }
    }
}