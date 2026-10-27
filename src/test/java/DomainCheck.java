import mealplanner.domain.Ingredient;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;
import mealplanner.events.CalendarChanged;
import mealplanner.events.EventBus;

public class DomainCheck {

    public static void main(String[] args) {

        Ingredient eggs = new Ingredient(1, "eggs", "Dairy & Eggs", 50, 0);
        Recipe omelette = new Recipe(1, "Omelette", 1);
        omelette.addIngredient(new RecipeIngredient(eggs, new Quantity(3, Unit.PIECE)));

        System.out.println("For 2 servings: " + omelette.scaledTo(2));

        EventBus bus = new EventBus();
        bus.subscribe(CalendarChanged.class, event -> System.out.println("Heard: " + event));
        bus.publish(new CalendarChanged(java.time.LocalDate.now()));
    }
}