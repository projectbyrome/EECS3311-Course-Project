package mealplanner.data;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.Ingredient;

public interface IngredientRepository {

    Optional<Ingredient> findByName(String name);
    List<Ingredient> search(String prefix);

    void save(Ingredient ingredient);

}