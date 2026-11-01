package mealplanner.external;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.NutritionSummary;
import mealplanner.model.FoodCandidate;

/**
 * Target interface (Adapter) and component interface (Decorator) for nutrition data.
 * Implemented by UsdaFoodDataClient and wrapped by CachedNutritionSource.
 */
public interface NutritionSource {

    /** Possible foods for an ingredient name, best match first. @throws NutritionSourceException */
    List<FoodCandidate> search(String name);

    /** Nutrients per 100 g, or empty if the food does not exist. @throws NutritionSourceException */
    Optional<NutritionSummary> nutrientsFor(String fdcId);
}