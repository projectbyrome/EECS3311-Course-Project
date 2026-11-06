package mealplanner.external;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mealplanner.data.NutritionCacheRepository;
import mealplanner.domain.NutritionSummary;
import mealplanner.model.FoodCandidate;

/**
 * Decorator: same interface and contract as the wrapped NutritionSource. Nutrient values are kept in
 * the SQLite cache, so each food is fetched from USDA only once. Exceptions pass through unchanged.
 */
public class CachedNutritionSource implements NutritionSource {

    private final NutritionSource delegate;
    private final NutritionCacheRepository cache;
    /** Search results are only kept while the app runs; matches that matter are saved as Ingredient.fdcId. */
    private final Map<String, List<FoodCandidate>> searches = new HashMap<>();

    public CachedNutritionSource(NutritionSource delegate, NutritionCacheRepository cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<FoodCandidate> search(String name) {
        String key = name.trim().toLowerCase();
        List<FoodCandidate> cached = searches.get(key);
        if (cached != null) {
            return cached;
        }
        List<FoodCandidate> found = List.copyOf(delegate.search(name));
        searches.put(key, found);
        return found;
    }

    /** SD05: find(fdcId) in the cache; if not cached, ask the delegate and save what it returns. */
    @Override
    public Optional<NutritionSummary> nutrientsFor(String fdcId) {
        Optional<NutritionSummary> cached = cache.find(fdcId);
        if (cached.isPresent() && cached.get().hasSodiumAndFiber()) {
            return cached;
        }
        // not cached, or cached before sodium and fibre were tracked: ask USDA again (keep the old values if it fails)
        Optional<NutritionSummary> fetched;
        try {
            fetched = delegate.nutrientsFor(fdcId);
        } catch (RuntimeException e) {
            if (cached.isPresent()) return cached;
            throw e;
        }
        fetched.ifPresent(n -> cache.save(fdcId, n));
        return fetched.isPresent() ? fetched : cached;
    }
}