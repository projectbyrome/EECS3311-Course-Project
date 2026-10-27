package mealplanner.data;
import mealplanner.domain.AppException;


public class DataAccessException extends AppException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}