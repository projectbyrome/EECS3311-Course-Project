package mealplanner.cli;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;
import mealplanner.model.IngredientCheck;

/** Shared base for CLI commands: holds the receiver (AppFacade) and the console, plus parsing helpers. */
public abstract class AbstractCliCommand implements CliCommand {

    protected final AppFacade facade;
    protected final Scanner in;
    protected final PrintStream out;

    protected AbstractCliCommand(AppFacade facade, Scanner in, PrintStream out) {
        this.facade = facade;
        this.in = in;
        this.out = out;
    }

    protected String requireOption(CliArgs args, String key) {
        String value = args.options.get(key);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Missing --" + key + ". Usage: " + usage());
        }
        return value;
    }

    /** Reads --date (yyyy-mm-dd); defaults to today when it is not given. */
    protected LocalDate parseDate(CliArgs args) {
        String value = args.options.get("date");
        if (value == null) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Date must look like 2026-10-20, not " + value);
        }
    }

    /**
     * Extra (Pantry, Macro Generator): a name that isn't in the ingredient list (e.g. ketchup) is checked with
     * Claude, then added if the user answers y. Returns the name to use (Claude may fix it, e.g. "mayo" ->
     * "mayonnaise"), or null if nothing should be added.
     */
    protected String knownIngredientOrAsk(String name) {
        if (name == null || name.isBlank() || facade.isKnownIngredient(name)) {
            return name;
        }
        String typed = name.trim();
        IngredientCheck check = facade.checkIngredient(typed);
        if (!check.food) {
            out.println("Claude doesn't think '" + typed + "' is a food" + (check.note.isBlank() ? "." : ": " + check.note));
            return null;
        }
        if (!check.name.equals(typed.toLowerCase()) && facade.isKnownIngredient(check.name)) {
            return confirm("Did you mean '" + check.name + "'?") ? check.name : null;
        }
        String about = check.checkedByClaude
                ? " Claude says: " + (check.note.isBlank() ? "" : check.note + " ") + "(" + check.describe() + ")."
                : " (Claude couldn't be reached: it will be added with category Other and no allergen tags.)";
        if (!confirm("'" + typed + "' isn't a known ingredient yet." + about
                + " Add '" + check.name + "' to the ingredient list?")) {
            return null;
        }
        return facade.addIngredient(check).getName();
    }

    protected boolean confirm(String question) {
        out.print(question + " [y/N] ");
        return in.hasNextLine() && in.nextLine().trim().equalsIgnoreCase("y");
    }
}