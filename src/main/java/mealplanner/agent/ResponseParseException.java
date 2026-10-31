package mealplanner.agent;

import mealplanner.domain.AppException;

/** Claude's reply did not contain the JSON the workflow asked for. Workflows catch it and fall back. */
public class ResponseParseException extends AppException {

    public ResponseParseException(String message) {
        super(message);
    }

    public ResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}