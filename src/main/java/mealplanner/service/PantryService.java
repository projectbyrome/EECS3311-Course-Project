package mealplanner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mealplanner.data.IngredientRepository;
import mealplanner.data.PantryRepository;
import mealplanner.domain.Ingredient;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.events.EventBus;
import mealplanner.events.PantryChanged;

/**
 * Pantry logic. For F01 only the parts the calendar needs are written:
 * missingFor() (meal flags) and consume() (end-of-day update).
 * add/update/remove/addPurchased come with F04 and F02.
 */
public class PantryService {

    private final PantryRepository pantryRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitConverter converter;
    private final EventBus eventBus;

    public PantryService(PantryRepository pantryRepository, IngredientRepository ingredientRepository,
                         UnitConverter converter, EventBus eventBus) {
        this.pantryRepository = pantryRepository;
        this.ingredientRepository = ingredientRepository;
        this.converter = converter;
        this.eventBus = eventBus;
    }

    public List<PantryItem> getItems() {
        return pantryRepository.findAll();
    }

    /**
     * Ingredients the pantry cannot cover for this recipe and number of servings.
     * Each returned RecipeIngredient holds the amount that is still missing.
     */
    public List<RecipeIngredient> missingFor(Recipe recipe, double servings) {
        List<RecipeIngredient> missing = new ArrayList<>();
        for (RecipeIngredient need : recipe.scaledTo(servings)) {
            Ingredient ingredient = need.getIngredient();
            Optional<PantryItem> item = pantryRepository.findByIngredient(ingredient.getId());
            if (item.isEmpty()) {
                missing.add(need);
                continue;
            }
            Quantity have = item.get().getQuantity();
            if (!converter.canConvert(have.getUnit(), need.getQuantity().getUnit(), ingredient)) {
                missing.add(need); // cannot compare, so treat as missing
                continue;
            }
            double haveAmount = converter.convert(have, need.getQuantity().getUnit(), ingredient).getAmount();
            double shortBy = need.getQuantity().getAmount() - haveAmount;
            if (shortBy > 1e-9) {
                missing.add(new RecipeIngredient(ingredient, new Quantity(shortBy, need.getQuantity().getUnit())));
            }
        }
        return missing;
    }

    /**
     * Removes used ingredients from the pantry (SD08). If there is not enough, the item goes to 0
     * and a warning is returned so the UI can show a notice.
     */
    public List<String> consume(List<RecipeIngredient> ingredients) {
        List<String> warnings = new ArrayList<>();
        for (RecipeIngredient used : ingredients) {
            Ingredient ingredient = used.getIngredient();
            Optional<PantryItem> found = pantryRepository.findByIngredient(ingredient.getId());
            if (found.isEmpty()) {
                warnings.add(ingredient.getName() + " was used but is not in the pantry");
                continue;
            }
            PantryItem item = found.get();
            Quantity have = item.getQuantity();
            if (!converter.canConvert(used.getQuantity().getUnit(), have.getUnit(), ingredient)) {
                warnings.add("Could not subtract " + used + " (units do not match the pantry)");
                continue;
            }
            double usedAmount = converter.convert(used.getQuantity(), have.getUnit(), ingredient).getAmount();
            double left = have.getAmount() - usedAmount;
            if (left < 0) {
                warnings.add("Not enough " + ingredient.getName() + " in the pantry; set to 0");
                left = 0;
            }
            item.setQuantity(new Quantity(left, have.getUnit()));
            pantryRepository.save(item);
        }
        eventBus.publish(new PantryChanged());
        return warnings;
    }

    public Optional<PantryItem> findByIngredient(String name) {
        return ingredientRepository.findByName(name)
                .flatMap(i -> pantryRepository.findByIngredient(i.getId()));
    }

    // TODO (F04): add(name, quantity), update(itemId, quantity), remove(itemId)
    // TODO (F02): addPurchased(items): FinishResult
}
