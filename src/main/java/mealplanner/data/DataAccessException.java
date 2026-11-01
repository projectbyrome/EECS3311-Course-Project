package mealplanner.data;

import mealplanner.domain.AppException;

/** Wraps SQL errors so the services never see SQLException. */
public class DataAccessException extends AppException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}