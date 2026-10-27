package mealplanner.data;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mealplanner.domain.DayPlan;

public interface CalendarRepository {

    Optional<DayPlan> findDay(LocalDate date);

    List<DayPlan> findOpenDaysBefore(LocalDate date);

    List<DayPlan> findDaysFrom(LocalDate date);

    void save(DayPlan day);
}