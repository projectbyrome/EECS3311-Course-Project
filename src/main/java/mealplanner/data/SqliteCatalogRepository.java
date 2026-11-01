package mealplanner.data;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * SQLite implementation of CatalogRepository. The catalogue is loaded once from recipe-recipe-catalog.json
 * (Database.seedIfEmpty). Each recipe comes back with source CATALOG and its catalogue id.
 */
public class SqliteCatalogRepository implements CatalogRepository {

    private static final String SELECT = "SELECT id, name, categories, cook_minutes, servings, instructions, nutrition "
            + "FROM catalog_recipe ";

    private final Database db;

    public SqliteCatalogRepository(Database db) {
        this.db = db;
    }

    @Override
    public List<Recipe> findAll() {
        return query(SELECT + "ORDER BY id", null);
    }

    @Override
    public Optional<Recipe> findById(long id) {
        return query(SELECT + "WHERE id = ?", id).stream().findFirst();
    }

    /** Used only when seeding: stores the catalogue recipes with their ingredient lines. */
    void insertAll(Connection c, List<Recipe> recipes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO catalog_recipe(name, categories, cook_minutes, "
                + "servings, instructions, nutrition) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement line = c.prepareStatement("INSERT INTO catalog_ingredient(catalog_id, ingredient_id, "
                     + "amount, unit) VALUES(?,?,?,?)")) {
            for (Recipe r : recipes) {
                ps.setString(1, r.getName());
                ps.setString(2, String.join("\n", r.getCategories().stream().map(RecipeCategory::toKey).toList()));
                ps.setInt(3, r.getCookMinutes());
                ps.setInt(4, r.getServings());
                ps.setString(5, String.join("\n", r.getInstructions()));
                try {
                    ps.setString(6, r.getNutritionPerServing() == null ? null
                            : SqliteNutritionCacheRepository.toJson(r.getNutritionPerServing()));
                } catch (IOException e) {
                    throw new DataAccessException("Cannot write nutrition of " + r.getName(), e);
                }
                ps.executeUpdate();
                long id;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
                for (RecipeIngredient ri : r.ingredients()) {
                    line.setLong(1, id);
                    line.setLong(2, ri.getIngredient().getId());
                    line.setDouble(3, ri.getQuantity().getAmount());
                    line.setString(4, ri.getQuantity().getUnit().name());
                    line.addBatch();
                }
            }
            line.executeBatch();
        }
    }

    private List<Recipe> query(String sql, java.lang.Object param) {
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
            throw new DataAccessException("Cannot read the recipe catalogue", e);
        }
        return recipes;
    }

    private static Recipe read(ResultSet rs) throws SQLException, IOException {
        long id = rs.getLong("id");
        Recipe r = new Recipe(id, rs.getString("name"), rs.getInt("servings"));
        String categories = rs.getString("categories");
        if (categories != null && !categories.isEmpty()) {
            for (String key : categories.split("\n")) {
                r.addCategory(RecipeCategory.parse(key)); // stored one "GROUP:Name" per line
            }
        }
        r.setCookMinutes(rs.getInt("cook_minutes"));
        String steps = rs.getString("instructions");
        if (steps != null && !steps.isEmpty()) {
            r.getInstructions().addAll(Arrays.asList(steps.split("\n")));
        }
        String nutrition = rs.getString("nutrition");
        if (nutrition != null) {
            r.setNutrition(SqliteNutritionCacheRepository.fromJson(nutrition));
        }
        r.setSource(RecipeSource.CATALOG, id);
        return r;
    }

    private static void loadIngredients(Connection c, Recipe r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ci.amount, ci.unit, i.id, i.name, i.category, i.allergens, i.fdc_id, i.grams_per_piece, "
                        + "i.density_g_per_ml FROM catalog_ingredient ci JOIN ingredient i ON i.id = ci.ingredient_id "
                        + "WHERE ci.catalog_id = ? ORDER BY ci.id")) {
            ps.setLong(1, r.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.addIngredient(new RecipeIngredient(SqliteIngredientRepository.read(rs),
                            new Quantity(rs.getDouble("amount"), Unit.valueOf(rs.getString("unit")))));
                }
            }
        }
    }
}