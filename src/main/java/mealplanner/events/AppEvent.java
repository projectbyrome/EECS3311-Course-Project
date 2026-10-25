package mealplanner.events;

import java.time.Instant;

public abstract class AppEvent {

    private final Instant timestamp = Instant.now();

    public Instant getTimestamp() {
        return timestamp;
    }
}