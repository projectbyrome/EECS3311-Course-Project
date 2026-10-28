package mealplanner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mealplanner.domain.NutritionSummary;

public class SqliteNutritionCacheRepository implements NutritionCacheRepository {

    private final Database db;
    private static final ObjectMapper json = new ObjectMapper();

    public SqliteNutritionCacheRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<NutritionSummary> find(String fdcId) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT nutrients FROM nutrition_cache WHERE fdc_id = ?")) {
            ps.setString(1, fdcId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(fromJson(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot read cached nutrients for " + fdcId, e);
        }
    }

    @Override
    public void save(String fdcId, NutritionSummary n) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO nutrition_cache(fdc_id, nutrients) VALUES(?, ?) "
                             + "ON CONFLICT(fdc_id) DO UPDATE SET nutrients = excluded.nutrients")) {
            ps.setString(1, fdcId);
            ps.setString(2, toJson(n));
            ps.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot cache nutrients for " + fdcId, e);
        }
    }

    static String toJson(NutritionSummary n) throws IOException {
        ObjectNode o = json.createObjectNode();
        o.put("calories", n.getCalories());
        o.put("carbsG", n.getCarbsG());
        o.put("proteinG", n.getProteinG());
        o.put("fatG", n.getFatG());
        o.put("sugarG", n.getSugarG());
        ObjectNode v = o.putObject("vitamins");
        n.getVitamins().forEach(v::put);
        o.put("complete", n.isComplete());
        n.getNotes().forEach(o.putArray("notes")::add);
        return json.writeValueAsString(o);
    }

    static NutritionSummary fromJson(String text) throws IOException {
        JsonNode o = json.readTree(text);
        Map<String, Double> vitamins = new LinkedHashMap<>();
        o.path("vitamins").fields().forEachRemaining(e -> vitamins.put(e.getKey(), e.getValue().asDouble()));
        List<String> notes = new ArrayList<>();
        o.path("notes").forEach(n -> notes.add(n.asText()));
        return new NutritionSummary(o.path("calories").asDouble(), o.path("carbsG").asDouble(),
                o.path("proteinG").asDouble(), o.path("fatG").asDouble(), o.path("sugarG").asDouble(),
                vitamins, o.path("complete").asBoolean(true), notes);
    }
}