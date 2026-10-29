package mealplanner.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.Checklist;
import mealplanner.domain.ChecklistItem;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;
import mealplanner.model.FinishResult;

/** F02 from the command line: checklist list | new | add | check | finish. */
public class ChecklistCommand extends AbstractCliCommand {

    public ChecklistCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "checklist";
    }

    @Override
    public String usage() {
        return """
                checklist list
                checklist new    --name "Weekly shop"
                checklist add    --list id --item eggs --amount 12 --unit PIECE
                checklist check  --item id [--off]   (tick or untick an item)
                checklist finish --list id           (moves ticked items into the pantry)""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "list", "" -> list();
            case "new" -> {
                Checklist c = facade.createChecklist(joined(args, "name"));
                yield CommandResult.ok("Created checklist #" + c.getId() + " " + c.getName() + ".");
            }
            case "add" -> add(args);
            case "check" -> {
                boolean on = !args.options.containsKey("off");
                facade.setItemChecked(parseLong(requireOption(args, "item"), "item"), on);
                yield CommandResult.ok(on ? "Item ticked." : "Item unticked.");
            }
            case "finish" -> {
                FinishResult r = facade.finishShopping(parseLong(requireOption(args, "list"), "list"));
                yield CommandResult.ok(r.toString());
            }
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    private CommandResult list() {
        List<Checklist> all = facade.getChecklists();
        if (all.isEmpty()) {
            return CommandResult.ok("No checklists yet. Create one with: checklist new --name \"Weekly shop\"");
        }
        StringBuilder sb = new StringBuilder();
        for (Checklist c : all) {
            sb.append('#').append(c.getId()).append(' ').append(c.getName()).append('\n');
            if (c.getItems().isEmpty()) sb.append("  (empty)\n");
            for (ChecklistItem i : c.getItems()) {
                sb.append("  #").append(i.getId()).append(' ').append(i).append('\n');
            }
        }
        return CommandResult.ok(sb.toString().trim());
    }

    private CommandResult add(CliArgs args) {
        long listId = parseLong(requireOption(args, "list"), "list");
        Quantity q = parseQuantity(requireOption(args, "amount"), requireOption(args, "unit"));
        ChecklistItem item = facade.addChecklistItem(listId, joined(args, "item"), q);
        return CommandResult.ok("Added #" + item.getId() + " " + item);
    }

    /** Names may contain spaces ("olive oil"); CliArgs puts the extra words in positional, so join them back. */
    private String joined(CliArgs args, String key) {
        String first = requireOption(args, key);
        return args.positional.isEmpty() ? strip(first) : strip(first + " " + String.join(" ", args.positional));
    }

    static Quantity parseQuantity(String amount, String unit) {
        double value;
        try {
            value = Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Amount must be a number, not " + amount);
        }
        if (value <= 0) {
            throw new InvalidInputException("Amount must be greater than 0");
        }
        try {
            return new Quantity(value, Unit.valueOf(unit.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unit must be one of G, KG, ML, L, TSP, TBSP, CUP, PIECE");
        }
    }

    static long parseLong(String value, String what) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("--" + what + " must be a whole number");
        }
    }

    private static String strip(String s) {
        return s.replace("\"", "").trim();
    }
}