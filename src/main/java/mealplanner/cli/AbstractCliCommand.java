package mealplanner.cli;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;

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

    /** Reads --date (yyyy-mm-dd); defaults to today */
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

    protected boolean confirm(String question) {
        out.print(question + " [y/N] ");
        return in.hasNextLine() && in.nextLine().trim().equalsIgnoreCase("y");
    }
}
