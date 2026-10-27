package mealplanner.data;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.PantryItem;

public interface PantryRepository {

    List<PantryItem> findAll();
    Optional<PantryItem> findByIngredient(long ingredientId);

    void save(PantryItem item);
    void delete(long id);

}