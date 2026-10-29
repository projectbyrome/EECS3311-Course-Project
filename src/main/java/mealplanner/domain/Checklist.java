package mealplanner.domain;

import java.util.ArrayList;
import java.util.List;

/** A named grocery list; knows which of its items are ticked (F02). There can be several at a time. */
public class Checklist {

    /** 0 until the ChecklistRepository saves the checklist. */
    private long id;
    private String name;
    private final List<ChecklistItem> items = new ArrayList<>();

    public Checklist(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("A checklist needs a name");
        }
        this.name = name.trim();
    }

    public void addItem(ChecklistItem item) {
        items.add(item);
    }

    public void check(long itemId) {
        findItem(itemId).setChecked(true);
    }

    public void uncheck(long itemId) {
        findItem(itemId).setChecked(false);
    }

    public List<ChecklistItem> checkedItems() {
        return items.stream().filter(ChecklistItem::isChecked).toList();
    }

    /** Finish Shopping: ticked items have gone to the pantry, so only the unticked ones stay. */
    public void removeChecked() {
        items.removeIf(ChecklistItem::isChecked);
    }

    public ChecklistItem findItem(long itemId) {
        return items.stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("No item " + itemId + " on checklist " + name));
    }

    public boolean hasItem(long itemId) {
        return items.stream().anyMatch(i -> i.getId() == itemId);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ChecklistItem> getItems() { return List.copyOf(items); }

    @Override
    public String toString() {
        return name;
    }
}