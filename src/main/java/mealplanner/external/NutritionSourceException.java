package mealplanner.external;

import mealplanner.domain.AppException;

/** The nutrition database could not be reached or gave an error (network, HTTP 5xx, bad key). */
public class NutritionSourceException extends AppException {

    public NutritionSourceException(String message) {
        super(message);
    }

    public NutritionSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}