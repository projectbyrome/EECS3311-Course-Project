package mealplanner.cli;

import java.io.PrintStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.DayPlan;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.MealEntry;
import mealplanner.domain.MealSlot;

/** F01 from the command line: calendar show | add | remove | skip. */
public class CalendarCommand extends AbstractCliCommand {

    public CalendarCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "calendar";
    }

    @Override
    public String usage() {
        return """
                calendar show   [--date yyyy-mm-dd]   (shows the week containing the date)
                calendar add    --date d --slot BREAKFAST|LUNCH|DINNER|SNACK --recipe id --servings n
                calendar remove --date d --entry id
                calendar skip   --date d --entry id""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "show", "" -> show(args);
            case "add" -> add(args);
            case "remove" -> {
                facade.removeMeal(parseDate(args), parseLong(requireOption(args, "entry"), "entry"));
                yield CommandResult.ok("Meal removed.");
            }
            case "skip" -> {
                facade.skipMeal(parseDate(args), parseLong(requireOption(args, "entry"), "entry"));
                yield CommandResult.ok("Meal marked as skipped.");
            }
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    private CommandResult show(CliArgs args) {
        LocalDate weekStart = parseDate(args).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<DayPlan> week = facade.getWeek(weekStart);
        StringBuilder sb = new StringBuilder();
        for (DayPlan day : week) {
            sb.append(day.getDate()).append(' ').append(day.getDate().getDayOfWeek())
                    .append(day.isClosed() ? " (closed)" : "").append('\n');
            for (MealSlot slot : MealSlot.values()) {
                sb.append(String.format("  %-9s", slot));
                List<MealEntry> entries = day.entriesIn(slot);
                if (entries.isEmpty()) {
                    sb.append(" -");
                }
                for (MealEntry e : entries) {
                    sb.append(" [#").append(e.getId()).append("] ").append(e);
                }
                sb.append('\n');
            }
        }
        sb.append("[!] = not enough ingredients in the pantry");
        return CommandResult.ok(sb.toString());
    }

    private CommandResult add(CliArgs args) {
        LocalDate date = parseDate(args);
        MealSlot slot;
        try {
            slot = MealSlot.valueOf(requireOption(args, "slot").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Slot must be BREAKFAST, LUNCH, DINNER or SNACK");
        }
        long recipeId = parseLong(requireOption(args, "recipe"), "recipe");
        double servings;
        try {
            servings = Double.parseDouble(requireOption(args, "servings"));
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Servings must be a number");
        }
        DayPlan day = facade.addMealToDay(date, slot, recipeId, servings);
        MealEntry added = day.getEntries().get(day.getEntries().size() - 1);
        String text = "Added " + added + " to " + slot + " on " + date + " (entry #" + added.getId() + ").";
        if (added.isMissingIngredients()) {
            text += "\nWarning: the pantry does not have enough ingredients for this meal.";
        }
        return CommandResult.ok(text);
    }

    private long parseLong(String value, String what) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("--" + what + " must be a whole number");
        }
    }
}
