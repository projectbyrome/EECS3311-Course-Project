package mealplanner.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mealplanner.domain.Ingredient;

public class SqliteIngredientRepository implements IngredientRepository {

    private static final String COLUMNS = "id, name, category, allergens, fdc_id, grams_per_piece, density_g_per_ml";

    private final Database db;

    public SqliteIngredientRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<Ingredient> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return query("SELECT " + COLUMNS + " FROM ingredient WHERE name = ? COLLATE NOCASE", name.trim())
                .stream().findFirst();
    }

    @Override
    public List<Ingredient> search(String prefix) {
        String p = prefix == null ? "" : prefix.trim().replace("%", "").replace("_", "");
        return query("SELECT " + COLUMNS + " FROM ingredient WHERE name LIKE ? ORDER BY name", p + "%");
    }

    public Optional<Ingredient> findById(long id) {
        return query("SELECT " + COLUMNS + " FROM ingredient WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public void save(Ingredient i) {
        try (Connection c = db.connection()) {
            if (i.getId() == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ingredient(name, category, allergens, fdc_id, grams_per_piece, density_g_per_ml) "
                                + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps, i);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) i.setId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ingredient SET name = ?, category = ?, allergens = ?, fdc_id = ?, "
                                + "grams_per_piece = ?, density_g_per_ml = ? WHERE id = ?")) {
                    bind(ps, i);
                    ps.setLong(7, i.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot save ingredient " + i.getName(), e);
        }
    }

    private static void bind(PreparedStatement ps, Ingredient i) throws SQLException {
        ps.setString(1, i.getName().trim().toLowerCase());
        ps.setString(2, i.getCategory());
        ps.setString(3, String.join(",", i.getAllergenTags()));
        ps.setString(4, i.getFdcId());
        ps.setDouble(5, i.getGramsPerPiece());
        ps.setDouble(6, i.getDensityGPerMl());
    }

    private List<Ingredient> query(String sql, Object param) {
        List<Ingredient> list = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read ingredients", e);
        }
        return list;
    }

    static Ingredient read(ResultSet rs) throws SQLException {
        Ingredient i = new Ingredient(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                rs.getDouble("grams_per_piece"), rs.getDouble("density_g_per_ml"));
        String allergens = rs.getString("allergens");
        if (allergens != null && !allergens.isBlank()) {
            i.getAllergenTags().addAll(Arrays.asList(allergens.split(",")));
        }
        i.setFdcId(rs.getString("fdc_id"));
        return i;
    }
}