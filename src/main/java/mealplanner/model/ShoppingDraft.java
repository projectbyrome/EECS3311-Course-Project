package mealplanner.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The generated shopping list shown to the user before it is approved (F03). */
public class ShoppingDraft {

    public List<DraftItem> items = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public ShoppingDraft() {
    }

    public ShoppingDraft(List<DraftItem> items, List<String> warnings) {
        this.items = new ArrayList<>(items);
        this.warnings = new ArrayList<>(warnings);
    }

    /** Draft with no items and one message, e.g. "Everything is already in your pantry". */
    public static ShoppingDraft empty(String message) {
        return new ShoppingDraft(List.of(), List.of(message));
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /** Items grouped by store section, in first-seen order. */
    public Map<String, List<DraftItem>> bySection() {
        Map<String, List<DraftItem>> groups = new LinkedHashMap<>();
        for (DraftItem item : items) {
            String section = item.category == null || item.category.isBlank() ? "Other" : item.category;
            groups.computeIfAbsent(section, s -> new ArrayList<>()).add(item);
        }
        return groups;
    }
}