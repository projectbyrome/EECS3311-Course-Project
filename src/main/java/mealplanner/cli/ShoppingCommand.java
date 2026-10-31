package mealplanner.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.Checklist;
import mealplanner.domain.InvalidInputException;
import mealplanner.model.DraftItem;
import mealplanner.model.RecipeSelection;
import mealplanner.model.ShoppingDraft;

/** F03 from the command line: shopping generate, then approve the draft into a checklist. */
public class ShoppingCommand extends AbstractCliCommand {

    public ShoppingCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "shopping";
    }

    @Override
    public String usage() {
        return """
                shopping generate --recipes 3x4,5x2 [--list id] [--yes]
                    (recipe id x servings; adds to checklist id, or to a new checklist if --list is not given;
                     --yes approves without asking)""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        if (!args.subcommand.equals("generate")) {
            return CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        }
        List<RecipeSelection> selections = parseSelections(requireOption(args, "recipes"));
        ShoppingDraft draft = facade.draftShoppingList(selections);
        String shown = format(draft);
        if (draft.isEmpty()) {
            return CommandResult.ok(shown);
        }
        out.println(shown);
        if (!args.options.containsKey("yes") && !confirm("Add these items to a checklist?")) {
            return CommandResult.ok("Nothing was added."); // UC03 5a
        }
        String listOption = args.options.get("list");
        Long listId = listOption == null ? null : ChecklistCommand.parseLong(listOption, "list");
        Checklist checklist = facade.approveShoppingDraft(draft, listId);
        return CommandResult.ok("Added " + draft.items.size() + " item(s) to checklist #" + checklist.getId()
                + " " + checklist.getName() + ".");
    }

    /** "3x4,5x2" means recipe 3 for 4 servings and recipe 5 for 2 servings. */
    static List<RecipeSelection> parseSelections(String text) {
        List<RecipeSelection> selections = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] bits = part.trim().toLowerCase().split("x");
            if (bits.length != 2) {
                throw new InvalidInputException("Write recipes as id x servings, e.g. 3x4,5x2");
            }
            try {
                selections.add(new RecipeSelection(Long.parseLong(bits[0].trim()), Double.parseDouble(bits[1].trim())));
            } catch (NumberFormatException e) {
                throw new InvalidInputException("Write recipes as id x servings, e.g. 3x4,5x2");
            }
        }
        return selections;
    }

    static String format(ShoppingDraft draft) {
        StringBuilder sb = new StringBuilder();
        for (String w : draft.warnings) {
            sb.append("Note: ").append(w).append('\n');
        }
        for (Map.Entry<String, List<DraftItem>> group : draft.bySection().entrySet()) {
            sb.append(group.getKey()).append('\n');
            for (DraftItem item : group.getValue()) {
                sb.append("  - ").append(item).append('\n');
            }
        }
        return sb.toString().trim();
    }
}