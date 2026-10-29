package mealplanner.events;

/** Published when a checklist or its items change, so the Shopping screen can redraw. */
public class ChecklistChanged extends AppEvent {

    public final long checklistId;

    public ChecklistChanged(long checklistId) {
        this.checklistId = checklistId;
    }
}