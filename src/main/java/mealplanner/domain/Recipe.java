package mealplanner.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A recipe in the library. F01 only needs ingredients() and scaledTo();
 * F04 adds persistence (SqliteRecipeRepository) and F05 the nutrition per serving.
 * F08 saves, edits and archives recipes; F09 switches the favourite flag.
 */
public class Recipe {

    private long id;
    private String name;
    private int cookMinutes;

    /** Number of servings the ingredient amounts are written for. */
    private int servings;
    private List<String> instructions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean favourite;
    private boolean archived;
    private RecipeSource source = RecipeSource.USER;
    private Long catalogId;

    /** Filled in by F05/F08 (NutritionService.recipeNutrition for one serving); null until then. */
    private NutritionSummary nutritionPerServing;

    private final List<RecipeIngredient> ingredients = new ArrayList<>();
    private final Set<RecipeCategory> categories = new LinkedHashSet<>();

    public Recipe(long id, String name, int servings) {
        if (servings <= 0) {
            throw new InvalidInputException("A recipe must make at least one serving");
        }
        this.id = id;
        this.name = name;
        this.servings = servings;
    }

    public List<RecipeIngredient> ingredients() {
        return List.copyOf(ingredients);
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
    }

    /** F08: RecipeLibraryService swaps the typed ingredient lines for the checked ones. */
    public void setIngredients(List<RecipeIngredient> lines) {
        ingredients.clear();
        ingredients.addAll(lines);
    }

    /** The ingredient amounts needed to make the given number of servings. */
    public List<RecipeIngredient> scaledTo(double servings) {
        double factor = servings / this.servings;
        List<RecipeIngredient> scaled = new ArrayList<>();
        for (RecipeIngredient ri : ingredients) {
            scaled.add(new RecipeIngredient(ri.getIngredient(), ri.getQuantity().times(factor)));
        }
        return scaled;
    }

    public Set<RecipeCategory> getCategories() {
        return Collections.unmodifiableSet(categories);
    }

    public void addCategory(RecipeCategory category) {
        categories.add(category);
    }

    public void setCategories(Collection<RecipeCategory> list) {
        categories.clear();
        categories.addAll(list);
    }

    /** True if any of the recipe's categories has this name, in any group ("soup" matches Dish Type: Soup). */
    public boolean hasCategory(String name) {
        for (RecipeCategory c : categories) {
            if (c.getName().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    public void setFavourite(boolean value) {
        this.favourite = value;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = new ArrayList<>(warnings);
    }

    public void setNutrition(NutritionSummary n) {
        this.nutritionPerServing = n;
    }

    /** Removed recipes are archived, not deleted, so meals already in the calendar keep working. */
    public void archive() {
        this.archived = true;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public int getCookMinutes() { return cookMinutes; }
    public void setCookMinutes(int cookMinutes) { this.cookMinutes = cookMinutes; }
    public int getServings() { return servings; }
    public List<String> getInstructions() { return instructions; }
    public List<String> getWarnings() { return List.copyOf(warnings); }
    public boolean isFavourite() { return favourite; }
    public boolean isArchived() { return archived; }
    public RecipeSource getSource() { return source; }
    public Long getCatalogId() { return catalogId; }
    public NutritionSummary getNutritionPerServing() { return nutritionPerServing; }

    /** Used by SqliteRecipeRepository when it loads a recipe. */
    public void setSource(RecipeSource source, Long catalogId) {
        this.source = source;
        this.catalogId = catalogId;
    }

    @Override
    public String toString() {
        return name;
    }
}
