package mealplanner.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import mealplanner.domain.Ingredient;

/** The one SQLite database shared by every repository. */
public class Database {

    private final String url;

    public Database(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
    }

    public Connection connection() {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database " + url, e);
        }
    }

    /** Creates the tables if they do not exist (F01-F08). */
    public void migrate() {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS day_plan (
                        date   TEXT PRIMARY KEY,
                        closed INTEGER NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS meal_entry (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        date      TEXT NOT NULL REFERENCES day_plan(date),
                        slot      TEXT NOT NULL,
                        recipe_id INTEGER NOT NULL,
                        servings  REAL NOT NULL,
                        skipped   INTEGER NOT NULL DEFAULT 0,
                        missing   INTEGER NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS checklist (
                        id   INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS checklist_item (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        checklist_id INTEGER NOT NULL REFERENCES checklist(id),
                        name         TEXT NOT NULL,
                        amount       REAL NOT NULL,
                        unit         TEXT NOT NULL,
                        checked      INTEGER NOT NULL DEFAULT 0,
                        in_pantry    INTEGER NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ingredient (
                        id               INTEGER PRIMARY KEY AUTOINCREMENT,
                        name             TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        category         TEXT,
                        allergens        TEXT NOT NULL DEFAULT '',
                        fdc_id           TEXT,
                        grams_per_piece  REAL NOT NULL DEFAULT 0,
                        density_g_per_ml REAL NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pantry_item (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        ingredient_id INTEGER NOT NULL REFERENCES ingredient(id),
                        amount        REAL NOT NULL,
                        unit          TEXT NOT NULL,
                        added_on      TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recipe (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        name         TEXT NOT NULL,
                        categories   TEXT NOT NULL DEFAULT '',
                        cook_minutes INTEGER NOT NULL DEFAULT 0,
                        servings     INTEGER NOT NULL,
                        instructions TEXT NOT NULL DEFAULT '',
                        warnings     TEXT NOT NULL DEFAULT '',
                        favourite    INTEGER NOT NULL DEFAULT 0,
                        archived     INTEGER NOT NULL DEFAULT 0,
                        source       TEXT NOT NULL DEFAULT 'USER',
                        catalog_id   INTEGER
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recipe_ingredient (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        recipe_id     INTEGER NOT NULL REFERENCES recipe(id),
                        ingredient_id INTEGER NOT NULL REFERENCES ingredient(id),
                        amount        REAL NOT NULL,
                        unit          TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS nutrition_cache (
                        fdc_id    TEXT PRIMARY KEY,
                        nutrients TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS profile (
                        id            INTEGER PRIMARY KEY CHECK (id = 1),
                        restrictions  TEXT NOT NULL DEFAULT '',
                        allergies     TEXT NOT NULL DEFAULT '',
                        favourite_categories TEXT NOT NULL DEFAULT '',
                        goal_calories REAL NOT NULL,
                        goal_protein  REAL NOT NULL,
                        goal_carbs    REAL NOT NULL,
                        goal_fat      REAL NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS catalog_recipe (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        name         TEXT NOT NULL,
                        categories   TEXT NOT NULL DEFAULT '',
                        cook_minutes INTEGER NOT NULL DEFAULT 0,
                        servings     INTEGER NOT NULL,
                        instructions TEXT NOT NULL DEFAULT '',
                        nutrition    TEXT
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS catalog_ingredient (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        catalog_id    INTEGER NOT NULL REFERENCES catalog_recipe(id),
                        ingredient_id INTEGER NOT NULL REFERENCES ingredient(id),
                        amount        REAL NOT NULL,
                        unit          TEXT NOT NULL
                    )""");
            // F08: nutrition per serving of library recipes (JSON, like the catalogue). Added with ALTER TABLE
            // so a database made by an earlier version gets the column too.
            boolean hasNutrition = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(recipe)")) {
                while (rs.next()) {
                    if (rs.getString("name").equals("nutrition")) hasNutrition = true;
                }
            }
            if (!hasNutrition) {
                st.executeUpdate("ALTER TABLE recipe ADD COLUMN nutrition TEXT");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Migration failed", e);
        }
    }

    /** Path of the bundled list of known ingredients (on the classpath, or a file path). */
    public static final String INGREDIENTS_JSON = "/data/ingredients.json";
    /** Path of the built-in recipe catalogue used by recommendations (F07). */
    public static final String CATALOG_JSON = "/data/recipe-catalog.json";

    /**
     * Loads the bundled data. Ingredients are added with INSERT OR IGNORE on every start, so names added to
     * ingredients.json in a later version reach an existing database (existing rows are never changed).
     * The recipe catalogue is loaded only when its table is empty.
     */
    public void seedIfEmpty(SeedLoader loader) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO ingredient(name, category, allergens, fdc_id, grams_per_piece, "
                            + "density_g_per_ml) VALUES(?,?,?,?,?,?)")) {
                for (Ingredient i : loader.loadIngredients(INGREDIENTS_JSON)) {
                    ps.setString(1, i.getName());
                    ps.setString(2, i.getCategory());
                    ps.setString(3, String.join(",", i.getAllergenTags()));
                    ps.setString(4, i.getFdcId());
                    ps.setDouble(5, i.getGramsPerPiece());
                    ps.setDouble(6, i.getDensityGPerMl());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            // TODO (F07): load the recipe catalogue when its table is empty
        } catch (SQLException e) {
            throw new DataAccessException("Cannot load the bundled data", e);
        }
    }
}
