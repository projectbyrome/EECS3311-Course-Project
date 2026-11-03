package mealplanner.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.model.Recommendation;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationResult;

/**
 * F07 from the command line (SD06): "recommend Curry 30 --goal high-protein" shows up to 3 recipe cards,
 * then asks which one to add to the library. "recommend approve 4" adds catalogue recipe 4 directly.
 */
public class RecommendCommand extends AbstractCliCommand {

    public RecommendCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "recommend";
    }

    @Override
    public String usage() {
        return """
                recommend [category|any] [max minutes] [--goal high-protein|low-calorie|low-carb|profile]
                  (a category can be several words, e.g. recommend Rice Dishes 20)
                recommend approve <catalogue id>""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        if (args.subcommand.equals("approve")) {
            if (args.positional.size() != 1) {
                throw new InvalidInputException("Usage: recommend approve <catalogue id>");
            }
            return approve(ChecklistCommand.parseLong(args.positional.get(0), "catalogue id"));
        }
        RecommendationFilter filter = new RecommendationFilter();
        // the words are the category, except a last word that is a number: that is the cooking time
        List<String> words = new ArrayList<>();
        if (!args.subcommand.isEmpty()) words.add(args.subcommand);
        words.addAll(args.positional);
        if (!words.isEmpty() && words.get(words.size() - 1).matches("\\d+")) {
            filter.maxCookMinutes = (int) ChecklistCommand.parseLong(words.remove(words.size() - 1), "max minutes");
        }
        String category = String.join(" ", words);
        if (!category.isEmpty() && !category.equalsIgnoreCase("any")) {
            filter.category = category;
        }
        String goal = args.options.get("goal");
        if (goal != null) {
            filter.goal = goal.equalsIgnoreCase("profile") ? facade.getProfile().getDefaultGoal() : NutritionGoal.preset(goal);
        }

        RecommendationResult result = facade.recommendRecipes(filter);
        if (result.isEmpty()) {
            return CommandResult.ok(result.message);
        }
        StringBuilder sb = new StringBuilder();
        if (result.message != null) {
            sb.append(result.message).append("\n");
        }
        List<Recommendation> cards = result.recommendations;
        for (int i = 0; i < cards.size(); i++) {
            sb.append(card(i + 1, cards.get(i))).append("\n");
        }
        out.println(sb.toString().trim());
        out.print("Add one to your recipe library? Type its number, or press Enter to skip: ");
        String answer = in.hasNextLine() ? in.nextLine().trim() : "";
        if (answer.isEmpty()) {
            return CommandResult.ok("Nothing was added.");
        }
        int n = (int) ChecklistCommand.parseLong(answer, "card number");
        if (n < 1 || n > cards.size()) {
            throw new InvalidInputException("Choose a number from 1 to " + cards.size());
        }
        return approve(cards.get(n - 1).recipe.getCatalogId());
    }

    private CommandResult approve(long catalogId) {
        Recipe added = facade.approveRecommendation(catalogId);
        String warnings = added.getWarnings().isEmpty() ? "" : " Warnings: " + String.join("; ", added.getWarnings());
        return CommandResult.ok("Added to your recipe library: " + added.getName() + " (#" + added.getId() + ")." + warnings);
    }

    private static String card(int number, Recommendation r) {
        Recipe recipe = r.recipe;
        StringBuilder sb = new StringBuilder();
        sb.append(number).append(". ").append(recipe.getName()).append("  [catalogue #").append(recipe.getCatalogId())
                .append("]  ").append(recipe.getCookMinutes()).append(" min");
        NutritionSummary n = recipe.getNutritionPerServing();
        if (n != null) {
            sb.append(String.format(", %.0f kcal, %.0f g protein per serving", n.getCalories(), n.getProteinG()));
        }
        sb.append("\n   ").append(String.join(", ", recipe.getCategories().stream().map(RecipeCategory::getName).toList()));
        if (r.reason != null && !r.reason.isBlank()) {
            sb.append("\n   ").append(r.reason);
        }
        return sb.toString();
    }
}