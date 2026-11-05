package mealplanner.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.StapleStatus;
import mealplanner.domain.Unit;

/**
 * SQLite implementation of PantryRepository
 */
public class SqlitePantryRepository implements PantryRepository {

    private static final String SELECT = "SELECT p.id AS pid, p.amount, p.unit, p.added_on, p.staple_status, "
            + "i.id, i.name, i.category, i.allergens, i.fdc_id, i.grams_per_piece, i.density_g_per_ml "
            + "FROM pantry_item p JOIN ingredient i ON i.id = p.ingredient_id ";

    private final Database db;

    public SqlitePantryRepository(Database db) {
        this.db = db;
    }

    @Override
    public List<PantryItem> findAll() {
        return query(SELECT + "ORDER BY i.name, p.id", null);
    }

    /**
     * The pantry item for this ingredient. If there are several (units that cannot be combined,
     * UC02 6b), the oldest one is returned.
     */
    @Override
    public Optional<PantryItem> findByIngredient(long ingredientId) {
        return query(SELECT + "WHERE p.ingredient_id = ? ORDER BY p.id", ingredientId).stream().findFirst();
    }

    @Override
    public void save(PantryItem item) {
        try (Connection c = db.connection()) {
            if (item.getId() == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pantry_item(ingredient_id, amount, unit, added_on, staple_status) VALUES(?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, item.getIngredient().getId());
                    ps.setDouble(2, item.getQuantity().getAmount());
                    ps.setString(3, item.getQuantity().getUnit().name());
                    ps.setString(4, item.getAddedOn().toString());
                    ps.setString(5, item.isStaple() ? item.getStapleStatus().name() : null);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) item.setId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pantry_item SET amount = ?, unit = ?, staple_status = ? WHERE id = ?")) {
                    ps.setDouble(1, item.getQuantity().getAmount());
                    ps.setString(2, item.getQuantity().getUnit().name());
                    ps.setString(3, item.isStaple() ? item.getStapleStatus().name() : null);
                    ps.setLong(4, item.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot save pantry item " + item, e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM pantry_item WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot delete pantry item " + id, e);
        }
    }

    private List<PantryItem> query(String sql, Long param) {
        List<PantryItem> items = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PantryItem item = new PantryItem(rs.getLong("pid"), SqliteIngredientRepository.read(rs),
                            new Quantity(rs.getDouble("amount"), Unit.valueOf(rs.getString("unit"))),
                            LocalDate.parse(rs.getString("added_on")));
                    String staple = rs.getString("staple_status");
                    if (staple != null) item.setStapleStatus(StapleStatus.valueOf(staple));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the pantry", e);
        }
        return items;
    }
}