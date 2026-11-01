import java.util.List;
import mealplanner.external.NutritionSourceException;
import mealplanner.external.UsdaFoodDataClient;
import mealplanner.main.AppConfig;
import mealplanner.model.FoodCandidate;

/** Manual check that USDA FoodData Central answers: searches one food and prints its nutrients. */
public class UsdaPing {

    public static void main(String[] args) {
        UsdaFoodDataClient usda = new UsdaFoodDataClient(AppConfig.fromEnvironment().usdaApiKey);
        try {
            List<FoodCandidate> foods = usda.search("banana");
            foods.stream().limit(3).forEach(f -> System.out.println("Found: " + f));
            if (!foods.isEmpty()) {
                System.out.println("Per 100 g: " + usda.nutrientsFor(foods.get(0).fdcId).orElse(null));
            }
        } catch (NutritionSourceException e) {
            System.out.println("USDA is not available: " + e.getMessage());
        }
    }
}