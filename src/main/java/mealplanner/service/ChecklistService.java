package mealplanner.service;

import java.util.List;
import java.util.Optional;
import mealplanner.data.ChecklistRepository;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.events.ChecklistChanged;
import mealplanner.events.EventBus;
import mealplanner.model.DraftItem;
import mealplanner.model.FinishResult;

/**
 * F02 Shopping Checklist: manages several checklists, flags items that are already in the pantry,
 * and moves ticked items into the pantry on Finish Shopping. Also receives approved F03 drafts.
 */
public class ChecklistService {

    private final ChecklistRepository checklistRepository;
    private final PantryService pantryService;
    private final EventBus eventBus;

    public ChecklistService(ChecklistRepository checklistRepository, PantryService pantryService,
                            EventBus eventBus) {
        this.checklistRepository = checklistRepository;
        this.pantryService = pantryService;
        this.eventBus = eventBus;
    }

    public Checklist create(String name) {
        Checklist checklist = new Checklist(name);
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklist.getId()));
        return checklist;
    }

    /** Every checklist, with the "already in pantry" flags re-checked against the current pantry. */
    public List<Checklist> getAll() {
        List<Checklist> all = checklistRepository.findAll();
        for (Checklist checklist : all) {
            checklist.getItems().forEach(this::flag);
        }
        return all;
    }

    public Checklist get(long checklistId) {
        return checklistRepository.findById(checklistId)
                .orElseThrow(() -> new InvalidInputException("No checklist with id " + checklistId));
    }

    /** UC02 steps 2-3: adds the item; if the pantry already has it, it is still added, with a flag (3a). */
    public ChecklistItem addItem(long checklistId, String name, Quantity quantity) {
        Checklist checklist = get(checklistId);
        ChecklistItem item = new ChecklistItem(name, quantity);
        flag(item);
        checklist.addItem(item);
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklistId));
        return item;
    }

    /** F03: adds the items of an approved shopping draft to a checklist. */
    public Checklist addItems(long checklistId, List<DraftItem> items) {
        Checklist checklist = get(checklistId);
        for (DraftItem d : items) {
            ChecklistItem item = new ChecklistItem(d.name, d.quantity);
            flag(item);
            checklist.addItem(item);
        }
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklistId));
        return checklist;
    }

    public void setChecked(long itemId, boolean checked) {
        Checklist checklist = checklistRepository.findByItemId(itemId)
                .orElseThrow(() -> new InvalidInputException("No checklist item with id " + itemId));
        if (checked) {
            checklist.check(itemId);
        } else {
            checklist.uncheck(itemId);
        }
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklist.getId()));
    }

    /** Removes one item from its checklist without moving it to the pantry. */
    public void removeItem(long itemId) {
        Checklist checklist = checklistRepository.findByItemId(itemId)
                .orElseThrow(() -> new InvalidInputException("No checklist item with id " + itemId));
        checklist.removeItem(itemId);
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklist.getId()));
    }

    /** Deletes a whole checklist and its items. Nothing goes to the pantry. */
    public void delete(long checklistId) {
        get(checklistId); // checks that it exists
        checklistRepository.delete(checklistId);
        eventBus.publish(new ChecklistChanged(checklistId));
    }

    /**
     * UC02 steps 5-6: ticked items go into the pantry (PantryService publishes PantryChanged),
     * then the checklist keeps only the unticked items.
     */
    public FinishResult finishShopping(long checklistId) {
        Checklist checklist = get(checklistId);
        List<ChecklistItem> ticked = checklist.checkedItems();
        if (ticked.isEmpty()) {
            throw new InvalidInputException("Tick the items you bought first.");
        }
        FinishResult result = pantryService.addPurchased(ticked);
        checklist.removeChecked();
        checklistRepository.save(checklist);
        eventBus.publish(new ChecklistChanged(checklistId));
        return result;
    }

    private void flag(ChecklistItem item) {
        Optional<PantryItem> inPantry = pantryService.findByIngredient(item.getName());
        item.setPantryQuantity(inPantry.map(PantryItem::getQuantity).orElse(null));
    }
}