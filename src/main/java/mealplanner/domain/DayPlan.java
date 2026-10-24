package mealplanner.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DayPlan {

    private final LocalDate date;
    private boolean closed;
    private final List<MealEntry> entries = new ArrayList<>();

    public DayPlan(LocalDate date) {
        this.date = date;
    }

    public void addEntry(MealEntry entry) {
        if (closed) {
            throw new InvalidInputException("Cannot add a meal to a day that has passed: " + date);
        }
        entries.add(entry);
    }

    public void removeEntry(long entryId) {
        boolean removed = entries.removeIf(e -> e.getId() == entryId);
        if (!removed) {
            throw new InvalidInputException("No meal with id " + entryId + " on " + date);
        }
    }

    public List<MealEntry> activeEntries() {
        return entries.stream().filter(e -> !e.isSkipped()).toList();
    }

    public void close() {
        this.closed = true;
    }

    public MealEntry findEntry(long entryId) {
        MealEntry entry = entries.stream()
                .filter(e -> e.getId() == entryId)
                .findFirst()
                .orElseThrow(() -> new InvalidInputException(
                        "No meal with id " + entryId + " on " + date));
        return entry;
    }

    public List<MealEntry> entriesIn(MealSlot slot) {
        List<MealEntry> matching = entries.stream()
                .filter(e -> e.getSlot() == slot)
                .toList();
        return matching;
    }

    public LocalDate getDate() {
        return date;
    }
    public boolean isClosed() {
        return closed;
    }
    public List<MealEntry> getEntries() {
        return List.copyOf(entries);
    }



}
