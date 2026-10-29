package mealplanner.data;

import java.util.List;
import java.util.Optional;
import mealplanner.domain.Checklist;

/** Stores the shopping checklists and their items (F02). */
public interface ChecklistRepository {

    List<Checklist> findAll();

    Optional<Checklist> findById(long id);

    /** The checklist that holds the given item (used when an item is ticked). */
    Optional<Checklist> findByItemId(long itemId);

    /** Saves the checklist and its items. New checklists and items get their id here. */
    void save(Checklist checklist);
}