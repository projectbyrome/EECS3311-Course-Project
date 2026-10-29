package mealplanner.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;

/** SQLite implementation of ChecklistRepository. */
public class SqliteChecklistRepository implements ChecklistRepository {

    private final Database db;

    public SqliteChecklistRepository(Database db) {
        this.db = db;
    }

    @Override
    public List<Checklist> findAll() {
        return query("SELECT id, name FROM checklist ORDER BY id", null);
    }

    @Override
    public Optional<Checklist> findById(long id) {
        return query("SELECT id, name FROM checklist WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public Optional<Checklist> findByItemId(long itemId) {
        return query("SELECT c.id, c.name FROM checklist c JOIN checklist_item i ON i.checklist_id = c.id "
                + "WHERE i.id = ?", itemId).stream().findFirst();
    }

    @Override
    public void save(Checklist checklist) {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            if (checklist.getId() == 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO checklist(name) VALUES(?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, checklist.getName());
                    ps.executeUpdate();
                    checklist.setId(generatedId(ps));
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("UPDATE checklist SET name = ? WHERE id = ?")) {
                    ps.setString(1, checklist.getName());
                    ps.setLong(2, checklist.getId());
                    ps.executeUpdate();
                }
            }
            Set<Long> keep = new HashSet<>();
            for (ChecklistItem item : checklist.getItems()) {
                if (item.getId() == 0) {
                    insertItem(c, checklist.getId(), item);
                } else {
                    updateItem(c, item);
                }
                keep.add(item.getId());
            }
            deleteRemovedItems(c, checklist.getId(), keep);
            c.commit();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot save checklist " + checklist.getName(), e);
        }
    }

    private void insertItem(Connection c, long checklistId, ChecklistItem item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO checklist_item(checklist_id, name, amount, unit, checked, in_pantry) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, checklistId);
            ps.setString(2, item.getName());
            ps.setDouble(3, item.getQuantity().getAmount());
            ps.setString(4, item.getQuantity().getUnit().name());
            ps.setInt(5, item.isChecked() ? 1 : 0);
            ps.setInt(6, item.isAlreadyInPantry() ? 1 : 0);
            ps.executeUpdate();
            item.setId(generatedId(ps));
        }
    }

    private void updateItem(Connection c, ChecklistItem item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE checklist_item SET checked = ?, in_pantry = ? WHERE id = ?")) {
            ps.setInt(1, item.isChecked() ? 1 : 0);
            ps.setInt(2, item.isAlreadyInPantry() ? 1 : 0);
            ps.setLong(3, item.getId());
            ps.executeUpdate();
        }
    }

    private void deleteRemovedItems(Connection c, long checklistId, Set<Long> keep) throws SQLException {
        List<Long> existing = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM checklist_item WHERE checklist_id = ?")) {
            ps.setLong(1, checklistId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) existing.add(rs.getLong(1));
            }
        }
        try (PreparedStatement del = c.prepareStatement("DELETE FROM checklist_item WHERE id = ?")) {
            for (long id : existing) {
                if (!keep.contains(id)) {
                    del.setLong(1, id);
                    del.executeUpdate();
                }
            }
        }
    }

    private List<Checklist> query(String sql, Long param) {
        List<Checklist> lists = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Checklist list = new Checklist(rs.getString("name"));
                    list.setId(rs.getLong("id"));
                    loadItems(c, list);
                    lists.add(list);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read checklists", e);
        }
        return lists;
    }

    private void loadItems(Connection c, Checklist list) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, amount, unit, checked, in_pantry FROM checklist_item WHERE checklist_id = ? ORDER BY id")) {
            ps.setLong(1, list.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChecklistItem item = new ChecklistItem(rs.getString("name"),
                            new Quantity(rs.getDouble("amount"), Unit.valueOf(rs.getString("unit"))));
                    item.setId(rs.getLong("id"));
                    item.setChecked(rs.getInt("checked") == 1);
                    // only the flag is stored; ChecklistService fills in the pantry amount when the lists are shown
                    item.setAlreadyInPantry(rs.getInt("in_pantry") == 1);
                    list.addItem(item);
                }
            }
        }
    }

    private static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
        }
        throw new SQLException("No id was generated");
    }
}