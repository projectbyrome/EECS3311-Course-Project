package mealplanner.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionSummary;
import mealplanner.model.IngredientPortion;
import mealplanner.model.NutritionSelection;
import mealplanner.model.RecipePortion;
import mealplanner.model.RecipeSelection;

/** F05 from the command line: macros recipe | ingredient (both can be mixed in one calculation). */
public class MacrosCommand extends AbstractCliCommand {

    public MacrosCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "macros";
    }

    @Override
    public String usage() {
        return """
                macros recipe     --recipes 2x1,3x0.5            (recipe id x servings)
                macros ingredient --items banana:120:G,eggs:2:PIECE
                  either form also takes the other option to mix recipes and ingredients""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        if (!args.subcommand.equals("recipe") && !args.subcommand.equals("ingredient")) {
            return CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        }
        List<NutritionSelection> selections = new ArrayList<>();
        String recipes = args.options.get("recipes");
        String items = args.options.get("items");
        if (args.subcommand.equals("recipe")) {
            recipes = requireOption(args, "recipes");
        } else {
            items = requireOption(args, "items");
        }
        if (recipes != null) {
            for (RecipeSelection s : ShoppingCommand.parseSelections(recipes)) {
                selections.add(new RecipePortion(s.recipeId, s.servings));
            }
        }
        if (items != null) {
            selections.addAll(parseItems(items));
        }
        NutritionSummary total = facade.generateMacros(selections);
        return CommandResult.ok("Nutrition for " + describe(selections) + ":\n" + total);
    }

    /** "banana:120:G,olive oil:1:TBSP" (spaces inside a name need quotes in the shell). */
    static List<NutritionSelection> parseItems(String text) {
        List<NutritionSelection> list = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] bits = part.replace("\"", "").split(":");
            if (bits.length != 3) {
                throw new InvalidInputException("Write ingredients as name:amount:unit, e.g. banana:120:G");
            }
            list.add(new IngredientPortion(bits[0].trim(), ChecklistCommand.parseQuantity(bits[1].trim(), bits[2].trim())));
        }
        return list;
    }

    private static String describe(List<NutritionSelection> selections) {
        return String.join(", ", selections.stream().map(NutritionSelection::describe).toList());
    }
}