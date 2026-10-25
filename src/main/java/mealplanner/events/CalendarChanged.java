package mealplanner.events;

import java.time.LocalDate;

public class CalendarChanged extends AppEvent {

    public final LocalDate date;


    public CalendarChanged(LocalDate date) {
        this.date = date;
    }
}