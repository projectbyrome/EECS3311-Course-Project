package mealplanner.main;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings read at start-up: ANTHROPIC_API_KEY, MEALPLANNER_MODEL, USDA_API_KEY (defaults to USDA's
 * rate-limited DEMO_KEY) and MEALPLANNER_DB (database file).
 * Each one is taken from the environment, or else from a local ".env" file in the folder the app is
 * started from (copy .env.example). The .env file holds real keys, so it is in .gitignore.
 */
public class AppConfig {

    public static final Path LOCAL_CONFIG = Path.of(".env");

    public String anthropicApiKey;
    public String model = "claude-sonnet-5";
    public String usdaApiKey = "DEMO_KEY";
    public String dbPath;

    public static AppConfig fromEnvironment() {
        return from(System.getenv(), readLocalConfig(LOCAL_CONFIG));
    }

    /** Environment variables win over the file, so a key can still be set for one run only. */
    static AppConfig from(Map<String, String> env, Map<String, String> file) {
        Map<String, String> settings = new HashMap<>(file);
        env.forEach((name, value) -> {
            if (value != null && !value.isBlank()) {
                settings.put(name, value);
            }
        });
        AppConfig c = new AppConfig();
        c.anthropicApiKey = valueOr(settings, "ANTHROPIC_API_KEY", null);
        c.model = valueOr(settings, "MEALPLANNER_MODEL", c.model);
        c.usdaApiKey = valueOr(settings, "USDA_API_KEY", c.usdaApiKey);
        c.dbPath = valueOr(settings, "MEALPLANNER_DB",
                Path.of(System.getProperty("user.home"), ".mealplanner", "mealplanner.db").toString());
        return c;
    }

    /**
     * Reads NAME=VALUE lines. Blank lines and lines starting with # are skipped, quotes around a value
     * are removed, and the placeholder values from .env.example count as not set.
     */
    static Map<String, String> readLocalConfig(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, eq).strip();
                String value = trimmed.substring(eq + 1).strip();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.isEmpty() && !value.startsWith("YOUR_")) {
                    values.put(name, value);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return values;
    }

    private static String valueOr(Map<String, String> settings, String name, String fallback) {
        String value = settings.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
