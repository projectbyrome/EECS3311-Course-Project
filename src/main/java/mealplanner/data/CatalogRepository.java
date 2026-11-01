package mealplanner.data;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.Recipe;

/** The built-in recipe catalogue used by recommendations (F07). Read-only for the rest of the app. */
public interface CatalogRepository {

    List<Recipe> findAll();

    Optional<Recipe> findById(long id);
}