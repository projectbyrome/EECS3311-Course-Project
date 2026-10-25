package mealplanner.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EventBus {

    private final Map<Class<? extends AppEvent>, List<EventListener>> listeners = new HashMap<>();

    public synchronized void subscribe(Class<? extends AppEvent> type, EventListener listener) {
        listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(listener);
    }

    public void publish(AppEvent event) {
        List<EventListener> targets;
        synchronized (this) { //synchronized
            targets = new ArrayList<>(listeners.getOrDefault(event.getClass(), List.of()));
        }
        for (EventListener listener : targets) {
            listener.onEvent(event);
        }
    }
}