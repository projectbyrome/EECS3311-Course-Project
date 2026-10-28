package mealplanner.data;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.RecipeSource;
import mealplanner.domain.Unit;

/**
 * SQLite implementation of RecipeRepository (the user's recipe library). Archived recipes are still
 * returned, so meals already in the calendar keep working; RecipeLibraryService hides them.
 * F08 adds the nutrition column (nutrition per serving as JSON, the same format as the catalogue).
 */
public class SqliteRecipeRepository implements RecipeRepository {

    private static final String SELECT = "SELECT id, name, categories, cook_minutes, servings, instructions, warnings, "
            + "favourite, archived, source, catalog_id, nutrition FROM recipe ";

    private final Database db;

    public SqliteRecipeRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<Recipe> findById(long id) {
        return query(SELECT + "WHERE id = ?", id).stream().findFirst();
    }

    /** Recipes whose name contains the query, without case ("" returns every recipe). */
    @Override
    public List<Recipe> search(String query) {
        String q = query == null ? "" : query.trim();
        return query(SELECT + "WHERE name LIKE ? ORDER BY name", "%" + q + "%");
    }

    @Override
    public List<Recipe> findAll() {
        return query(SELECT + "ORDER BY name", null);
    }

    /** Inserts (id 0 gets a new id) or updates the recipe, and rewrites its ingredient lines. */
    @Override
    public long save(Recipe r) {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            String sql = r.getId() == 0
                    ? "INSERT INTO recipe(name, categories, cook_minutes, servings, instructions, warnings, favourite, "
                    + "archived, source, catalog_id, nutrition) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT INTO recipe(name, categories, cook_minutes, servings, instructions, warnings, favourite, "
                    + "archived, source, catalog_id, nutrition, id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, categories = excluded.categories, "
                    + "cook_minutes = excluded.cook_minutes, servings = excluded.servings, "
                    + "instructions = excluded.instructions, warnings = excluded.warnings, "
                    + "favourite = excluded.favourite, archived = excluded.archived, "
                    + "source = excluded.source, catalog_id = excluded.catalog_id, nutrition = excluded.nutrition";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.getName());
                ps.setString(2, String.join("\n", r.getCategories().stream().map(RecipeCategory::toKey).toList()));
                ps.setInt(3, r.getCookMinutes());
                ps.setInt(4, r.getServings());
                ps.setString(5, String.join("\n", r.getInstructions()));
                ps.setString(6, String.join("\n", r.getWarnings()));
                ps.setInt(7, r.isFavourite() ? 1 : 0);
                ps.setInt(8, r.isArchived() ? 1 : 0);
                ps.setString(9, r.getSource().name());
                if (r.getCatalogId() == null) ps.setNull(10, Types.INTEGER); else ps.setLong(10, r.getCatalogId());
                ps.setString(11, r.getNutritionPerServing() == null ? null
                        : SqliteNutritionCacheRepository.toJson(r.getNutritionPerServing()));
                if (r.getId() != 0) ps.setLong(12, r.getId());
                ps.executeUpdate();
                if (r.getId() == 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) r.setId(keys.getLong(1));
                    }
                }
            }
            try (PreparedStatement del = c.prepareStatement("DELETE FROM recipe_ingredient WHERE recipe_id = ?")) {
                del.setLong(1, r.getId());
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO recipe_ingredient(recipe_id, ingredient_id, amount, unit) VALUES(?,?,?,?)")) {
                for (RecipeIngredient ri : r.ingredients()) {
                    ins.setLong(1, r.getId());
                    ins.setLong(2, ri.getIngredient().getId());
                    ins.setDouble(3, ri.getQuantity().getAmount());
                    ins.setString(4, ri.getQuantity().getUnit().name());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            c.commit();
            return r.getId();
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot save recipe " + r.getName(), e);
        }
    }

    private List<Recipe> query(String sql, Object param) {
        List<Recipe> recipes = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipes.add(read(rs));
                }
            }
            for (Recipe r : recipes) {
                loadIngredients(c, r);
            }
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot read recipes", e);
        }
        return recipes;
    }

    private static Recipe read(ResultSet rs) throws SQLException, IOException {
        Recipe r = new Recipe(rs.getLong("id"), rs.getString("name"), rs.getInt("servings"));
        for (String key : lines(rs.getString("categories"))) {
            r.addCategory(RecipeCategory.parse(key));
        }
        r.setCookMinutes(rs.getInt("cook_minutes"));
        r.getInstructions().addAll(lines(rs.getString("instructions")));
        r.setWarnings(lines(rs.getString("warnings")));
        r.setFavourite(rs.getInt("favourite") == 1);
        if (rs.getInt("archived") == 1) r.archive();
        long catalogId = rs.getLong("catalog_id");
        Long catalog = rs.wasNull() ? null : catalogId; // read wasNull() before the next column
        r.setSource(RecipeSource.valueOf(rs.getString("source")), catalog);
        String nutrition = rs.getString("nutrition");
        if (nutrition != null) r.setNutrition(SqliteNutritionCacheRepository.fromJson(nutrition));
        return r;
    }

    private static void loadIngredients(Connection c, Recipe r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ri.amount, ri.unit, i.id, i.name, i.category, i.allergens, i.fdc_id, i.grams_per_piece, "
                        + "i.density_g_per_ml FROM recipe_ingredient ri JOIN ingredient i ON i.id = ri.ingredient_id "
                        + "WHERE ri.recipe_id = ? ORDER BY ri.id")) {
            ps.setLong(1, r.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.addIngredient(new RecipeIngredient(SqliteIngredientRepository.read(rs),
                            new Quantity(rs.getDouble("amount"), Unit.valueOf(rs.getString("unit")))));
                }
            }
        }
    }

    private static List<String> lines(String text) {
        return text == null || text.isEmpty() ? List.of() : Arrays.asList(text.split("\n"));
    }
}
