package mealplanner.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed command line, e.g. "calendar add --date 2026-10-20 --slot LUNCH --recipe 3 --servings 2"
 * gives verb=calendar, subcommand=add and four options.
 */
public class CliArgs {

    public final String verb;
    public final String subcommand;
    public final Map<String, String> options;
    public final List<String> positional;

    private CliArgs(String verb, String subcommand, Map<String, String> options, List<String> positional) {
        this.verb = verb;
        this.subcommand = subcommand;
        this.options = options;
        this.positional = positional;
    }

    public static CliArgs parse(String[] tokens) {
        String verb = tokens.length > 0 ? tokens[0].toLowerCase() : "";
        String sub = "";
        Map<String, String> options = new HashMap<>();
        List<String> positional = new ArrayList<>();
        int i = 1;
        if (tokens.length > 1 && !tokens[1].startsWith("--")) {
            sub = tokens[1].toLowerCase();
            i = 2;
        }
        for (; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.startsWith("--")) {
                String key = t.substring(2);
                String value = (i + 1 < tokens.length && !tokens[i + 1].startsWith("--")) ? tokens[++i] : "true";
                options.put(key, value);
            } else {
                positional.add(t);
            }
        }
        return new CliArgs(verb, sub, options, positional);
    }
}
