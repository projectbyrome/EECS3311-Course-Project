package mealplanner.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mealplanner.data.IngredientRepository;
import mealplanner.data.PantryRepository;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.Ingredient;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.events.EventBus;
import mealplanner.events.PantryChanged;
import mealplanner.model.FinishResult;

/**
 * Pantry logic (F04, CD4): validates and merges pantry items. Also used by F01 (missingFor, consume)
 * and F02 (addPurchased, findByIngredient). Every change publishes PantryChanged (Observer).
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
     * (Design returns void; returning the warnings is how SD08's "show a notice" gets its text.)
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

    /**
     * Finish Shopping (F02): moves the ticked checklist items into the pantry.
     * Known items are added to what is already there (converted with UnitConverter);
     * if the units cannot be combined the item becomes a separate pantry entry (UC02 6b);
     * unknown names are not moved and are listed in notAdded (UC02 6a).
     */
    public FinishResult addPurchased(List<ChecklistItem> items) {
        int moved = 0;
        int merged = 0;
        List<String> notAdded = new ArrayList<>();
        for (ChecklistItem bought : items) {
            Optional<Ingredient> known = ingredientRepository.findByName(bought.getName());
            if (known.isEmpty()) {
                notAdded.add(bought.getName());
                continue;
            }
            Ingredient ingredient = known.get();
            Quantity q = bought.getQuantity();
            Optional<PantryItem> existing = pantryRepository.findByIngredient(ingredient.getId());
            if (existing.isPresent()
                    && converter.canConvert(q.getUnit(), existing.get().getQuantity().getUnit(), ingredient)) {
                PantryItem item = existing.get();
                Quantity have = item.getQuantity();
                item.setQuantity(have.plus(converter.convert(q, have.getUnit(), ingredient)));
                pantryRepository.save(item);
                merged++;
            } else {
                pantryRepository.save(new PantryItem(0, ingredient, q, LocalDate.now()));
            }
            moved++;
        }
        if (moved > 0) {
            eventBus.publish(new PantryChanged());
        }
        return new FinishResult(moved, merged, notAdded);
    }

    /**
     * SD02 / UC04: adds an ingredient to the pantry. The name must be a known ingredient (2a) and the
     * amount greater than 0 (3a). If the ingredient is already there, the amounts are added together,
     * converted with UnitConverter; if the units cannot be combined it becomes a separate entry.
     */
    public PantryItem add(String name, Quantity quantity) {
        String cleanName = name == null ? "" : name.trim();
        Ingredient ingredient = ingredientRepository.findByName(cleanName)
                .orElseThrow(() -> new InvalidInputException("Unknown ingredient: " + cleanName));
        requirePositive(quantity);
        Optional<PantryItem> existing = pantryRepository.findByIngredient(ingredient.getId());
        PantryItem item;
        if (existing.isPresent()
                && converter.canConvert(quantity.getUnit(), existing.get().getQuantity().getUnit(), ingredient)) {
            item = existing.get();
            Quantity have = item.getQuantity();
            item.setQuantity(have.plus(converter.convert(quantity, have.getUnit(), ingredient)));
        } else {
            item = new PantryItem(0, ingredient, quantity, LocalDate.now());
        }
        pantryRepository.save(item);
        eventBus.publish(new PantryChanged());
        return item;
    }

    /** Sets a new quantity on a pantry item (UC04 "changes its quantity"). 0 is allowed; negative is not. */
    public void update(long itemId, Quantity quantity) {
        if (quantity == null) {
            throw new InvalidInputException("Enter an amount and a unit");
        }
        PantryItem item = requireItem(itemId);
        item.setQuantity(quantity);
        pantryRepository.save(item);
        eventBus.publish(new PantryChanged());
    }

    public void remove(long itemId) {
        requireItem(itemId);
        pantryRepository.delete(itemId);
        eventBus.publish(new PantryChanged());
    }

    private PantryItem requireItem(long itemId) {
        return pantryRepository.findAll().stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("No pantry item with id " + itemId));
    }

    private static void requirePositive(Quantity quantity) {
        if (quantity == null) {
            throw new InvalidInputException("Enter an amount and a unit");
        }
        if (quantity.getAmount() <= 0 || Double.isNaN(quantity.getAmount()) || Double.isInfinite(quantity.getAmount())) {
            throw new InvalidInputException("Amount must be greater than 0");
        }
    }
}