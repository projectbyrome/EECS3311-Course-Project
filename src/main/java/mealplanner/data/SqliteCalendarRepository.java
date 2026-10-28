package mealplanner.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mealplanner.domain.DayPlan;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;
import mealplanner.domain.Recipe;

public class SqliteCalendarRepository implements CalendarRepository {

    private final Database db;
    private final RecipeRepository recipes;

    public SqliteCalendarRepository(Database db, RecipeRepository recipes) {
        this.db = db;
        this.recipes = recipes;
    }

    @Override
    public Optional<DayPlan> findDay(LocalDate date) {
        List<DayPlan> days = query("SELECT date, closed FROM day_plan WHERE date = ?", date.toString());
        return days.stream().findFirst();
    }

    @Override
    public List<DayPlan> findOpenDaysBefore(LocalDate date) {
        return query("SELECT date, closed FROM day_plan WHERE date < ? AND closed = 0 ORDER BY date",
                date.toString());
    }

    @Override
    public List<DayPlan> findDaysFrom(LocalDate date) {
        return query("SELECT date, closed FROM day_plan WHERE date >= ? ORDER BY date", date.toString());
    }

    @Override
    public void save(DayPlan day) {
        String date = day.getDate().toString();
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO day_plan(date, closed) VALUES(?, ?) "
                            + "ON CONFLICT(date) DO UPDATE SET closed = excluded.closed")) {
                ps.setString(1, date);
                ps.setInt(2, day.isClosed() ? 1 : 0);
                ps.executeUpdate();
            }
            Set<Long> keep = new HashSet<>();
            for (MealEntry e : day.getEntries()) {
                if (e.getId() == 0) {
                    insertEntry(c, date, e);
                } else {
                    updateEntry(c, e);
                }
                keep.add(e.getId());
            }
            deleteRemovedEntries(c, date, keep);
            c.commit();
        } catch (SQLException ex) {
            throw new DataAccessException("Cannot save day " + date, ex);
        }
    }

    private void insertEntry(Connection c, String date, MealEntry e) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO meal_entry(date, slot, recipe_id, servings, skipped, missing) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, date);
            ps.setString(2, e.getSlot().name());
            ps.setLong(3, e.getRecipe().getId());
            ps.setDouble(4, e.getServings());
            ps.setInt(5, e.isSkipped() ? 1 : 0);
            ps.setInt(6, e.isMissingIngredients() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    e.setId(keys.getLong(1));
                }
            }
        }
    }

    private void updateEntry(Connection c, MealEntry e) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE meal_entry SET skipped = ?, missing = ? WHERE id = ?")) {
            ps.setInt(1, e.isSkipped() ? 1 : 0);
            ps.setInt(2, e.isMissingIngredients() ? 1 : 0);
            ps.setLong(3, e.getId());
            ps.executeUpdate();
        }
    }

    private void deleteRemovedEntries(Connection c, String date, Set<Long> keep) throws SQLException {
        List<Long> existing = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM meal_entry WHERE date = ?")) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) existing.add(rs.getLong(1));
            }
        }
        try (PreparedStatement del = c.prepareStatement("DELETE FROM meal_entry WHERE id = ?")) {
            for (long id : existing) {
                if (!keep.contains(id)) {
                    del.setLong(1, id);
                    del.executeUpdate();
                }
            }
        }
    }

    private List<DayPlan> query(String sql, String param) {
        List<DayPlan> days = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DayPlan day = new DayPlan(LocalDate.parse(rs.getString("date")));
                    loadEntries(c, day);
                    if (rs.getInt("closed") == 1) day.close();
                    days.add(day);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read calendar", e);
        }
        return days;
    }

    private void loadEntries(Connection c, DayPlan day) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, slot, recipe_id, servings, skipped, missing FROM meal_entry WHERE date = ? ORDER BY id")) {
            ps.setString(1, day.getDate().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long recipeId = rs.getLong("recipe_id");
                    Recipe recipe = recipes.findById(recipeId)
                            .orElseThrow(() -> new DataAccessException("Recipe " + recipeId + " not found", null));
                    MealEntry e = new MealEntry(MealSlot.valueOf(rs.getString("slot")), recipe,
                            rs.getDouble("servings"));
                    e.setId(rs.getLong("id"));
                    if (rs.getInt("skipped") == 1) e.markSkipped();
                    e.setMissing(rs.getInt("missing") == 1);
                    day.addEntry(e);
                }
            }
        }
    }
}