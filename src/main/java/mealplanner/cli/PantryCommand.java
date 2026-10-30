package mealplanner.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.PantryItem;
import mealplanner.domain.Quantity;

/**
 * F04 from the command line (SD02): pantry list | add | set | remove.
 * Example: "pantry add eggs 12 PIECE" or "pantry add olive oil 500 ML" (the last two words are amount and unit).
 */
public class PantryCommand extends AbstractCliCommand {

    public PantryCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "pantry";
    }

    @Override
    public String usage() {
        return """
                pantry list
                pantry add    <ingredient> <amount> <unit>   e.g. pantry add eggs 12 PIECE
                pantry set    <item id> <amount> <unit>      (change the quantity)
                pantry remove <item id>""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "list", "" -> list();
            case "add" -> add(args.positional);
            case "set" -> set(args.positional);
            case "remove" -> {
                if (args.positional.size() != 1) {
                    throw new InvalidInputException("Usage: pantry remove <item id>");
                }
                facade.removePantryItem(ChecklistCommand.parseLong(args.positional.get(0), "item id"));
                yield CommandResult.ok("Removed pantry item #" + args.positional.get(0) + ".");
            }
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    private CommandResult list() {
        List<PantryItem> items = facade.getPantry();
        if (items.isEmpty()) {
            return CommandResult.ok("The pantry is empty. Add something with: pantry add eggs 12 PIECE");
        }
        StringBuilder sb = new StringBuilder();
        for (PantryItem item : items) {
            sb.append('#').append(item.getId()).append(' ').append(item.getIngredient().getName())
                    .append(": ").append(format(item.getQuantity())).append('\n');
        }
        return CommandResult.ok(sb.toString().trim());
    }

    /** SD02: execute(args) -> addPantryItem("eggs", 12 PIECE) -> CommandResult.ok("eggs: 12 PIECE in pantry"). */
    private CommandResult add(List<String> words) {
        if (words.size() < 3) {
            throw new InvalidInputException("Usage: pantry add <ingredient> <amount> <unit>");
        }
        String name = String.join(" ", words.subList(0, words.size() - 2)).replace("\"", "");
        Quantity q = ChecklistCommand.parseQuantity(words.get(words.size() - 2), words.get(words.size() - 1));
        PantryItem item = facade.addPantryItem(name, q);
        return CommandResult.ok(item.getIngredient().getName() + ": " + format(item.getQuantity()) + " in pantry");
    }

    private CommandResult set(List<String> words) {
        if (words.size() != 3) {
            throw new InvalidInputException("Usage: pantry set <item id> <amount> <unit>");
        }
        long id = ChecklistCommand.parseLong(words.get(0), "item id");
        Quantity q = parseQuantityAllowZero(words.get(1), words.get(2));
        facade.updatePantryItem(id, q);
        return CommandResult.ok("Pantry item #" + id + " is now " + format(q) + ".");
    }

    private static Quantity parseQuantityAllowZero(String amount, String unit) {
        if (amount.trim().equals("0")) {
            return ChecklistCommand.parseQuantity("1", unit).times(0);
        }
        return ChecklistCommand.parseQuantity(amount, unit);
    }

    /** 12.0 PIECE is shown as 12 PIECE. */
    static String format(Quantity q) {
        double a = Math.round(q.getAmount() * 100) / 100.0;
        String amount = a == Math.floor(a) ? String.valueOf((long) a) : String.valueOf(a);
        return amount + " " + q.getUnit();
    }
}