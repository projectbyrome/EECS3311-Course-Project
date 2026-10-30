package mealplanner.agent.llm;

/** Target interface (Adapter): all model access goes through here, so tests can use a fake client. */
public interface LLMClient {

    /** @throws LLMUnavailableException on a timeout, network error or HTTP error */

    LLMResponse send(LLMRequest request);
}