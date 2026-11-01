package mealplanner.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.UserProfile;

/** SQLite implementation of ProfileRepository: a single row (id 1) in the profile table. */
public class SqliteProfileRepository implements ProfileRepository {

    private final Database db;

    public SqliteProfileRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<UserProfile> load() {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT restrictions, allergies, favourite_categories, goal_calories, "
                     + "goal_protein, goal_carbs, goal_fat FROM profile WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            Set<DietaryRestriction> restrictions = new TreeSet<>();
            for (String r : split(rs.getString("restrictions"))) {
                restrictions.add(DietaryRestriction.valueOf(r));
            }
            NutritionGoal goal = new NutritionGoal(rs.getDouble("goal_calories"), rs.getDouble("goal_protein"),
                    rs.getDouble("goal_carbs"), rs.getDouble("goal_fat"));
            return Optional.of(new UserProfile(restrictions, new TreeSet<>(split(rs.getString("allergies"))),
                    new LinkedHashSet<>(lines(rs.getString("favourite_categories"))), goal));
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the profile", e);
        }
    }

    @Override
    public void save(UserProfile p) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO profile(id, restrictions, allergies, favourite_categories, "
                     + "goal_calories, goal_protein, goal_carbs, goal_fat) VALUES(1,?,?,?,?,?,?,?) "
                     + "ON CONFLICT(id) DO UPDATE SET restrictions = excluded.restrictions, "
                     + "allergies = excluded.allergies, favourite_categories = excluded.favourite_categories, "
                     + "goal_calories = excluded.goal_calories, goal_protein = excluded.goal_protein, "
                     + "goal_carbs = excluded.goal_carbs, goal_fat = excluded.goal_fat")) {
            ps.setString(1, String.join(",", p.getRestrictions().stream().map(Enum::name).sorted().toList()));
            ps.setString(2, String.join(",", new TreeSet<>(p.getAllergies())));
            ps.setString(3, String.join("\n", p.getFavouriteCategories())); // one per line, like recipe categories
            ps.setDouble(4, p.getDefaultGoal().getCalories());
            ps.setDouble(5, p.getDefaultGoal().getProteinG());
            ps.setDouble(6, p.getDefaultGoal().getCarbsG());
            ps.setDouble(7, p.getDefaultGoal().getFatG());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot save the profile", e);
        }
    }

    private static java.util.List<String> split(String csv) {
        return csv == null || csv.isBlank() ? java.util.List.of() : Arrays.asList(csv.split(","));
    }

    /** Favourite categories are stored one per line. */
    private static java.util.List<String> lines(String text) {
        return text == null || text.isBlank() ? java.util.List.of()
                : text.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}