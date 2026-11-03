package mealplanner.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.CategoryGroup;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Quantity;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.domain.Unit;

/**
 * F08 from the command line (SD03): recipe list | show | add | edit | remove.
 * "recipe add" with no options asks for each field; with options it runs in one line, for example
 * recipe add --name Tomato_Pasta --servings 2 --minutes 20 --categories Pasta,Savory,Flavor:Tangy
 *            --ingredients pasta:200:g,tomato:3:piece --steps Boil_the_pasta|Add_the_tomatoes
 * Underscores stand for spaces. A category is a name (Pasta) or Group:Name (Flavor:Spicy).
 * "recipe edit 3 --minutes 25" changes only the options given.
 */
public class RecipeCommand extends AbstractCliCommand {

    public RecipeCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "recipe";
    }

    @Override
    public String usage() {
        return """
                recipe list [text] [--favourites]       (* marks a favourite)       (* marks a favourite)
                recipe show <id>
                recipe add                      (asks for each field)
                recipe add --name Tomato_Pasta --servings 2 [--minutes 20] [--categories Pasta,Savory]
                           --ingredients pasta:200:g,tomato:3:piece [--steps Boil_the_pasta|Add_the_tomatoes]
                recipe edit <id> [same options; the ones left out keep their value]
                recipe remove <id> [--yes]""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "list", "" -> list(args);
            case "show" -> CommandResult.ok(describe(facade.getRecipe(id(args))));
            case "add" -> add(args);
            case "edit" -> edit(args);
            case "remove" -> remove(args);
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    private CommandResult list(CliArgs args) {
        List<Recipe> recipes = args.options.containsKey("favourites")
                ? facade.getFavourites()
                : facade.searchLibrary(String.join(" ", args.positional));
        if (recipes.isEmpty()) {
            return CommandResult.ok(args.options.containsKey("favourites") ? "No favourite recipes yet." : "No recipes found.");
        }
        StringBuilder sb = new StringBuilder();
        for (Recipe r : recipes) {
            sb.append(line(r)).append("\n");
        }
        return CommandResult.ok(sb.toString().trim());
    }

    /** SD03: onSave -> saveRecipe(r). Problems come back as InvalidRecipeException and are printed by CliApp. */
    private CommandResult add(CliArgs args) {
        Recipe recipe = args.options.containsKey("name") ? fromOptions(args, null) : ask();
        Recipe saved = facade.saveRecipe(recipe);
        return CommandResult.ok("Saved recipe " + saved.getId() + ".\n" + describe(saved));
    }

    private CommandResult edit(CliArgs args) {
        Recipe current = facade.getRecipe(id(args));
        if (args.options.isEmpty()) {
            return CommandResult.error("Nothing to change.\n" + usage());
        }
        Recipe saved = facade.saveRecipe(fromOptions(args, current));
        return CommandResult.ok("Saved.\n" + describe(saved));
    }

    private CommandResult remove(CliArgs args) {
        Recipe recipe = facade.getRecipe(id(args));
        if (!args.options.containsKey("yes")
                && !confirm("Remove " + recipe.getName() + " from the library? Planned meals keep it.")) {
            return CommandResult.ok("Nothing removed.");
        }
        facade.removeRecipe(recipe.getId());
        return CommandResult.ok("Removed " + recipe.getName() + " from the library.");
    }

    /** Builds the recipe from the options; for an edit, options that are left out keep the current value. */
    private Recipe fromOptions(CliArgs args, Recipe current) {
        String name = option(args, "name", current == null ? null : current.getName());
        int servings = (int) number(args, "servings", current == null ? 1 : current.getServings());
        Recipe recipe = new Recipe(current == null ? 0 : current.getId(), name == null ? "" : name, servings);
        if (args.options.containsKey("categories")) {
            recipe.setCategories(parseCategories(args.options.get("categories")));
        } else if (current != null) {
            recipe.setCategories(current.getCategories());
        }
        recipe.setCookMinutes((int) number(args, "minutes", current == null ? 0 : current.getCookMinutes()));
        if (args.options.containsKey("ingredients")) {
            for (String spec : args.options.get("ingredients").split(",")) {
                recipe.addIngredient(parseSpec(spec));
            }
        } else if (current != null) {
            current.ingredients().forEach(recipe::addIngredient);
        }
        if (args.options.containsKey("steps")) {
            for (String step : args.options.get("steps").split("\\|")) {
                if (!step.isBlank()) recipe.getInstructions().add(step.replace('_', ' ').trim());
            }
        } else if (current != null) {
            recipe.getInstructions().addAll(current.getInstructions());
        }
        return recipe;
    }

    /** Interactive "recipe add": one question per field, then ingredient and step lines until an empty line. */
    private Recipe ask() {
        String name = prompt("Name: ");
        int servings = (int) parseNumber(prompt("Servings [1]: "), 1, "servings");
        Recipe recipe = new Recipe(0, name, servings);
        String categories = prompt("Categories, separated by commas (optional, e.g. Pasta, Savory, Flavor:Spicy): ");
        recipe.setCategories(parseCategories(categories));
        recipe.setCookMinutes((int) parseNumber(prompt("Cooking time in minutes [0]: "), 0, "cooking time"));
        out.println("Ingredients, one per line, like '200 g chicken breast' (empty line to finish):");
        for (String line = prompt("  "); !line.isEmpty(); line = prompt("  ")) {
            recipe.addIngredient(parseLine(line));
        }
        out.println("Cooking steps, one per line (empty line to finish):");
        for (String line = prompt("  "); !line.isEmpty(); line = prompt("  ")) {
            recipe.getInstructions().add(line);
        }
        return recipe;
    }

    /**
     * "200 g chicken breast" -> 200 G of chicken breast. A line without an amount or unit keeps only the
     * name, so saveRecipe() flags it (UC08 4a) instead of guessing.
     */
    public static RecipeIngredient parseLine(String line) {
        String[] t = line.trim().split("\\s+");
        Double amount = t.length > 1 ? tryNumber(t[0]) : null;
        if (amount == null) {
            return RecipeIngredient.unchecked(line.trim(), null);
        }
        if (t.length > 2) {
            try {
                Unit unit = Unit.parse(t[1]);
                return RecipeIngredient.unchecked(join(t, 2), new Quantity(amount, unit));
            } catch (InvalidInputException notAUnit) {
                // "2 eggs": an amount but no unit
            }
        }
        return RecipeIngredient.unchecked(join(t, 1), null);
    }

    /** "pasta:200:g" (underscores for spaces). "pasta:200" or "pasta" have no unit and are flagged on save. */
    static RecipeIngredient parseSpec(String spec) {
        String[] p = spec.split(":");
        String name = p[0].replace('_', ' ').trim();
        if (p.length < 3 || p[2].isBlank()) {
            return RecipeIngredient.unchecked(name, null);
        }
        Double amount = tryNumber(p[1]);
        if (amount == null || amount < 0) {
            return RecipeIngredient.unchecked(name, null);
        }
        return RecipeIngredient.unchecked(name, new Quantity(amount, Unit.parse(p[2])));
    }

    /** Groups tried, in this order, when a category is typed without a group ("Pasta" is a Dish Type). */
    private static final List<CategoryGroup> LOOKUP_ORDER = List.of(CategoryGroup.DISH_TYPE,
            CategoryGroup.MAIN_INGREDIENT, CategoryGroup.FLAVOR, CategoryGroup.COOKING_METHOD);

    /**
     * "Pasta,Savory,Flavor:Spicy,Rice_Dishes" -> categories. A name without a group goes in the first group
     * that suggests it, or in Other if no group does (the user's own category).
     */
    static List<RecipeCategory> parseCategories(String text) {
        List<RecipeCategory> list = new ArrayList<>();
        if (text == null) {
            return list;
        }
        for (String part : text.split(",")) {
            String item = part.replace('_', ' ').trim();
            if (!item.isEmpty()) {
                list.add(parseCategory(item));
            }
        }
        return list;
    }

    static RecipeCategory parseCategory(String item) {
        int colon = item.indexOf(':');
        if (colon >= 0) {
            return new RecipeCategory(group(item.substring(0, colon).trim()), item.substring(colon + 1).trim());
        }
        for (CategoryGroup g : LOOKUP_ORDER) {
            if (g.find(item).isPresent()) {
                return new RecipeCategory(g, item);
            }
        }
        return new RecipeCategory(CategoryGroup.OTHER, item);
    }

    /** "Flavor", "dish type" or "DISH_TYPE" -> the group. */
    private static CategoryGroup group(String typed) {
        for (CategoryGroup g : CategoryGroup.values()) {
            if (g.getLabel().equalsIgnoreCase(typed) || g.name().equalsIgnoreCase(typed.replace(' ', '_'))) {
                return g;
            }
        }
        throw new InvalidInputException("Unknown category group '" + typed + "'. Use one of: Dish Type, "
                + "Main Ingredient, Flavor, Cooking Method, Other");
    }

    static String line(Recipe r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%3d  %s %s", r.getId(), r.isFavourite() ? "*" : " ", r.getName()));
        List<String> details = new ArrayList<>();
        if (!r.getCategories().isEmpty()) {
            details.add(String.join(", ", r.getCategories().stream().map(RecipeCategory::getName).toList()));
        }
        if (r.getCookMinutes() > 0) details.add(r.getCookMinutes() + " min");
        details.add(r.getServings() + (r.getServings() == 1 ? " serving" : " servings"));
        sb.append(" (").append(String.join(", ", details)).append(")");
        if (!r.getWarnings().isEmpty()) sb.append("  ! ").append(String.join("; ", r.getWarnings()));
        return sb.toString();
    }

    static String describe(Recipe r) {
        StringBuilder sb = new StringBuilder(line(r)).append("\nIngredients:");
        r.ingredients().forEach(ri -> sb.append("\n  - ").append(ri));
        if (!r.getInstructions().isEmpty()) {
            sb.append("\nSteps:");
            for (int i = 0; i < r.getInstructions().size(); i++) {
                sb.append("\n  ").append(i + 1).append(". ").append(r.getInstructions().get(i));
            }
        }
        sb.append("\nWarnings: ").append(r.getWarnings().isEmpty() ? "none" : String.join("; ", r.getWarnings()));
        sb.append("\n").append(nutritionLine(r.getNutritionPerServing()));
        return sb.toString();
    }

    /** UC08 step 5 / 5a: the nutrition summary per serving, marked incomplete when data is missing. */
    static String nutritionLine(NutritionSummary n) {
        if (n == null) {
            return "Nutrition per serving: not calculated yet (edit and save the recipe to calculate it)";
        }
        return "Nutrition per serving: " + n.shortLine();
    }

    private long id(CliArgs args) {
        if (args.positional.size() != 1) {
            throw new InvalidInputException("Give the recipe id. Usage:\n" + usage());
        }
        return ChecklistCommand.parseLong(args.positional.get(0), "recipe id");
    }

    private String prompt(String text) {
        out.print(text);
        return in.hasNextLine() ? in.nextLine().trim() : "";
    }

    private static String option(CliArgs args, String key, String fallback) {
        String v = args.options.get(key);
        return v == null ? fallback : v.replace('_', ' ').trim();
    }

    private static double number(CliArgs args, String key, double fallback) {
        return args.options.containsKey(key) ? parseNumber(args.options.get(key), fallback, key) : fallback;
    }

    private static double parseNumber(String text, double fallback, String what) {
        if (text == null || text.isBlank()) return fallback;
        Double d = tryNumber(text);
        if (d == null) throw new InvalidInputException("The " + what + " must be a number, not " + text);
        return d;
    }

    private static Double tryNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String join(String[] tokens, int from) {
        return String.join(" ", Arrays.copyOfRange(tokens, from, tokens.length));
    }
}
