package mealplanner.cli;

import java.io.PrintStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.DayPlan;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.ValidationReport;
import mealplanner.model.DaySummary;

/** F10 from the command line: summary day [date] | summary week [date] | summary goal [options]. */
public class SummaryCommand extends AbstractCliCommand {

    public SummaryCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "summary";
    }

    @Override
    public String usage() {
        return """
                summary day  [yyyy-mm-dd]      (today if left out; may ask Claude for the summary)
                summary week [yyyy-mm-dd]      (the Monday-to-Sunday week that holds the date)
                summary goal [--calories 2000] [--protein 50] [--carbs 275] [--fat 78] [--fibre 28] [--sodium 2300]
                  with no options it shows the daily goal; options left out keep their value""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "day", "" -> CommandResult.ok(facade.daySummary(date(args)).toString());
            case "week" -> week(args);
            case "goal" -> goal(args);
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    private CommandResult week(CliArgs args) {
        LocalDate weekStart = date(args).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<DayPlan> week = facade.getWeek(weekStart);
        long planned = week.stream().filter(d -> !d.activeEntries().isEmpty()).count();
        if (planned == 0) {
            return CommandResult.ok("Nutrition for the week of " + weekStart + "\nNo meals planned for this week.");
        }
        NutritionSummary totals = facade.weekSummary(weekStart);
        NutritionGoal weekGoal = facade.getProfile().getDefaultGoal().times(7);
        StringBuilder sb = new StringBuilder("Nutrition for the week of ").append(weekStart)
                .append(" (").append(planned).append(" of 7 days have meals)");
        sb.append("\nWeek totals compared with 7 x your daily goal:");
        List<String> misses = weekGoal.misses(totals, NutritionGoal.DAILY_TOLERANCE_PCT);
        DaySummary.goalLines(totals, weekGoal, misses).forEach(l -> sb.append("\n  ").append(l));
        sb.append(String.format("%nAverage per planned day: %.0f kcal, %.1f g protein, %.1f g carbs, %.1f g fat",
                totals.getCalories() / planned, totals.getProteinG() / planned, totals.getCarbsG() / planned,
                totals.getFatG() / planned));
        if (!totals.isComplete()) {
            sb.append("\n(incomplete: some nutrition data is missing, so the real totals are higher)");
        }
        return CommandResult.ok(sb.toString());
    }

    /** UC10 5a: execute(args) -> updateGoal(goal); invalid values are listed and nothing is saved. */
    private CommandResult goal(CliArgs args) {
        NutritionGoal current = facade.getProfile().getDefaultGoal();
        if (args.options.isEmpty()) {
            return CommandResult.ok("Daily goal: " + current);
        }
        NutritionGoal goal = new NutritionGoal(
                number(args, "calories", current.getCalories()),
                number(args, "protein", current.getProteinG()),
                number(args, "carbs", current.getCarbsG()),
                number(args, "fat", current.getFatG()),
                number(args, "fibre", number(args, "fiber", current.getFiberG())),
                number(args, "sodium", current.getSodiumMg()));
        ValidationReport report = facade.updateGoal(goal);
        if (!report.isValid()) {
            return CommandResult.error("Goal not saved: " + String.join("; ", report.getViolations()));
        }
        return CommandResult.ok("Daily goal saved: " + goal);
    }

    private static double number(CliArgs args, String key, double current) {
        String value = args.options.get(key);
        if (value == null) {
            return current;
        }
        double v;
        try {
            v = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("--" + key + " must be a number, not " + value);
        }
        if (v < 0) {
            // NutritionGoal cannot hold a negative value, so this is caught here with the same message as 0
            throw new InvalidInputException("Goal not saved: --" + key + " must be greater than 0");
        }
        return v;
    }

    /** The date as the first word after the sub-command, or --date; today if neither is given. */
    private LocalDate date(CliArgs args) {
        if (args.positional.isEmpty()) {
            return parseDate(args);
        }
        try {
            return LocalDate.parse(args.positional.get(0));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Date must look like 2026-10-20, not " + args.positional.get(0));
        }
    }
}