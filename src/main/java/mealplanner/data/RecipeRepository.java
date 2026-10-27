package mealplanner.data;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.Recipe;

public interface RecipeRepository {

    Optional<Recipe> findById(long id);

    List<Recipe> search(String query);
    List<Recipe> findAll();

    long save(Recipe recipe);
}