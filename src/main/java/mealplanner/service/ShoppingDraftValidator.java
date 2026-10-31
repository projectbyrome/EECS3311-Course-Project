package mealplanner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mealplanner.domain.Ingredient;
import mealplanner.domain.ValidationReport;
import mealplanner.model.DraftItem;
import mealplanner.model.ShoppingDraft;
import mealplanner.model.ShoppingNeed;

/**
 * Checks Claude's shopping draft against the exact needs: Claude may rename the sections and
 * merge lines, but it must not add an item, lose an item, or lower an amount.
 */
public class ShoppingDraftValidator {

    private static final double TOLERANCE = 0.01; // 1 % for rounding

    private final UnitConverter converter;

    public ShoppingDraftValidator(UnitConverter converter) {
        this.converter = converter;
    }

    public ValidationReport validate(ShoppingDraft draft, List<ShoppingNeed> needs) {
        ValidationReport report = new ValidationReport();
        if (draft == null || draft.items == null) {
            report.add("The draft has no items");
            return report;
        }
        List<DraftItem> unmatched = new ArrayList<>();
        for (DraftItem item : draft.items) {
            if (item == null || item.name == null || item.quantity == null) {
                report.add("A draft item has no name or quantity");
            } else if (needs.stream().noneMatch(n -> sameName(n, item))) {
                unmatched.add(item);
            }
        }
        unmatched.forEach(i -> report.add("Item that was not needed: " + i.name));

        for (ShoppingNeed need : needs) {
            Ingredient ing = need.ingredient;
            double needed = need.quantity.getAmount();
            double inDraft = 0;
            boolean found = false;
            for (DraftItem item : draft.items) {
                if (item == null || item.quantity == null || !sameName(need, item)) continue;
                found = true;
                if (converter.canConvert(item.quantity.getUnit(), need.quantity.getUnit(), ing)) {
                    inDraft += converter.convert(item.quantity, need.quantity.getUnit(), ing).getAmount();
                }
            }
            if (!found) {
                report.add("Missing item: " + ing.getName());
            } else if (inDraft < needed * (1 - TOLERANCE)) {
                report.add("Amount of " + ing.getName() + " was lowered (" + inDraft + " < " + needed + ")");
            }
        }
        return report;
    }

    private static boolean sameName(ShoppingNeed need, DraftItem item) {
        return item.name != null && normalise(need.ingredient.getName()).equals(normalise(item.name));
    }

    private static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}