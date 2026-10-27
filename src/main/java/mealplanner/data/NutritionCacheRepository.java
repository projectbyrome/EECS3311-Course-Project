package mealplanner.data;

import java.util.Optional;
import mealplanner.domain.NutritionSummary;

public interface NutritionCacheRepository {

    Optional<NutritionSummary> find(String fdcId);
    void save(String fdcId, NutritionSummary nutrients);

}