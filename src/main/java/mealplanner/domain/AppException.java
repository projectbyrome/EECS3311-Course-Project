package mealplanner.domain;

/** Base class of every application exception (unchecked). See CD6. */
public abstract class AppException extends RuntimeException {

    protected AppException(String message) {
        super(message);
    }

    protected AppException(String message, Throwable cause) {
        super(message, cause);
    }
}