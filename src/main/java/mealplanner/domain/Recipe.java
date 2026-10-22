package mealplanner.domain;


import java.util.ArrayList;
import java.util.List;

public class Recipe {

    private long id;
    private String name;
    private String cuisine;
    private int cookMinutes;
    private int servings;
    private List<String> instructions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean favourite;
    private boolean archived;
    private RecipeSource source = RecipeSource.USER;
    private Long catalogId;
    private NutritionSummary nutritionPerServing;

    private final List<RecipeIngredient> ingredients = new ArrayList<>();

    public Recipe(long id, String name, int servings) {
        if (servings <= 0) {
            throw new InvalidInputException("A recipe must be able to make atleast 1 servings.");
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

    public void setIngredients(List<RecipeIngredient> lines) {
        ingredients.clear();
        ingredients.addAll(lines);
    }

    public List<RecipeIngredient> scaledTo(double servings) {
        double factor = servings / this.servings;
        List<RecipeIngredient> scaled = new ArrayList<>();
        for (RecipeIngredient ri : ingredients) {
            scaled.add(new RecipeIngredient(ri.getIngredient(), ri.getQuantity().times(factor)));
        }
        return scaled;
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

    /** Removed recipes are archived, not deleted */
    public void archive() { this.archived = true; }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public String getCuisine() {
        return cuisine;
    }
    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }
    public int getCookMinutes() {
        return cookMinutes;
    }
    public void setCookMinutes(int cookMinutes) {
        this.cookMinutes = cookMinutes;
    }
    public int getServings() {
        return servings;
    }
    public List<String> getInstructions() {
        return instructions;
    }
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }
    public boolean isFavourite() {
        return favourite;
    }
    public boolean isArchived() {
        return archived;
    }
    public RecipeSource getSource() {
        return source;
    }
    public Long getCatalogId() {
        return catalogId;
    }
    public NutritionSummary getNutritionPerServing() {
        return nutritionPerServing;
    }

    public void setSource(RecipeSource source, Long catalogId) {
        this.source = source;
        this.catalogId = catalogId;
    }

    @Override
    public String toString() {
        return name;
    }
}