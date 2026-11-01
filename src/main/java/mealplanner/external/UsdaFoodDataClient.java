package mealplanner.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mealplanner.domain.NutritionSummary;
import mealplanner.model.FoodCandidate;

/**
 * Adapter for the USDA FoodData Central API. Turns its JSON into FoodCandidate and NutritionSummary
 * (per 100 g), and network or HTTP errors into NutritionSourceException. A 404 means "no such food".
 * Only Foundation and SR Legacy foods are searched, because their values are per 100 g.
 */
public class UsdaFoodDataClient implements NutritionSource {

    public static final String DEFAULT_BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private static final int MAX_CANDIDATES = 5;

    /** USDA nutrient numbers for the vitamins we show. */
    private static final Map<String, String> VITAMINS = Map.of(
            "320", "Vitamin A (µg)",
            "401", "Vitamin C (mg)",
            "328", "Vitamin D (µg)",
            "323", "Vitamin E (mg)",
            "430", "Vitamin K (µg)",
            "415", "Vitamin B6 (mg)",
            "418", "Vitamin B12 (µg)");

    private final String apiKey;
    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout = Duration.ofSeconds(10);
    private final ObjectMapper json = new ObjectMapper();

    public UsdaFoodDataClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_BASE_URL);
    }

    public UsdaFoodDataClient(String apiKey, HttpClient http, String baseUrl) {
        this.apiKey = apiKey;
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<FoodCandidate> search(String name) {
        String url = baseUrl + "/foods/search?query=" + encode(name)
                + "&dataType=" + encode("Foundation,SR Legacy") + "&pageSize=" + MAX_CANDIDATES
                + "&api_key=" + encode(apiKey);
        HttpResponse<String> response = get(url);
        if (response.statusCode() / 100 != 2) {
            throw new NutritionSourceException("USDA search returned HTTP " + response.statusCode());
        }
        return parseSearch(response.body());
    }

    @Override
    public Optional<NutritionSummary> nutrientsFor(String fdcId) {
        HttpResponse<String> response = get(baseUrl + "/food/" + encode(fdcId) + "?api_key=" + encode(apiKey));
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new NutritionSourceException("USDA returned HTTP " + response.statusCode() + " for food " + fdcId);
        }
        return Optional.of(parseFood(response.body()));
    }

    private HttpResponse<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new NutritionSourceException("USDA FoodData Central could not be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NutritionSourceException("Request to USDA was interrupted", e);
        }
    }

    List<FoodCandidate> parseSearch(String body) {
        List<FoodCandidate> candidates = new ArrayList<>();
        for (JsonNode food : readTree(body).path("foods")) {
            candidates.add(new FoodCandidate(food.path("fdcId").asText(), food.path("description").asText()));
            if (candidates.size() == MAX_CANDIDATES) break;
        }
        return candidates;
    }

    /**
     * Reads the nutrients of one food (values are per 100 g). The summary is marked incomplete if
     * energy, carbohydrate, protein or fat is missing. Missing sugar counts as 0.
     */
    NutritionSummary parseFood(String body) {
        Map<String, Double> byNumber = new LinkedHashMap<>();
        for (JsonNode n : readTree(body).path("foodNutrients")) {
            String number = n.path("nutrient").path("number").asText();
            JsonNode amount = n.get("amount");
            if (!number.isEmpty() && amount != null && amount.isNumber()) {
                byNumber.putIfAbsent(number, amount.asDouble());
            }
        }
        // Energy is 208 (kcal); some Foundation foods only have the Atwater values 958 / 957.
        Double calories = first(byNumber, "208", "958", "957");
        Double carbs = first(byNumber, "205");
        Double protein = first(byNumber, "203");
        Double fat = first(byNumber, "204");
        Double sugar = first(byNumber, "269", "269.3");
        Map<String, Double> vitamins = new LinkedHashMap<>();
        VITAMINS.forEach((number, label) -> {
            if (byNumber.containsKey(number)) vitamins.put(label, byNumber.get(number));
        });
        boolean complete = calories != null && carbs != null && protein != null && fat != null;
        List<String> notes = complete ? List.of() : List.of("USDA has no full macro data for this food");
        return new NutritionSummary(orZero(calories), orZero(carbs), orZero(protein), orZero(fat), orZero(sugar),
                vitamins, complete, notes);
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new NutritionSourceException("USDA reply was not valid JSON", e);
        }
    }

    private static Double first(Map<String, Double> values, String... numbers) {
        for (String n : numbers) {
            if (values.containsKey(n)) return values.get(n);
        }
        return null;
    }

    private static double orZero(Double d) {
        return d == null ? 0 : d;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}