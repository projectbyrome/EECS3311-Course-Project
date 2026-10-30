package mealplanner.agent.llm;

import mealplanner.domain.AppException;

/** Claude could not be reached (timeout, network or HTTP error) */
public class LLMUnavailableException extends AppException {

    public LLMUnavailableException(String message) {
        super(message);
    }

    public LLMUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}