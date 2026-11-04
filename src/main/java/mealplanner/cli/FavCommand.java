package mealplanner.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.Recipe;

/** F09 from the command line (same structure as SD02): fav toggle <id> | fav list. */
public class FavCommand extends AbstractCliCommand {

    public FavCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "fav";
    }

    @Override
    public String usage() {
        return """
                fav toggle <recipe id>
                fav list""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "toggle" -> toggle(args);
            case "list", "" -> list();
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    /** execute(args) -> toggleFavourite(id); a failed save leaves the flag as it was and prints the error. */
    private CommandResult toggle(CliArgs args) {
        if (args.positional.size() != 1) {
            throw new InvalidInputException("Usage: fav toggle <recipe id>");
        }
        long id = ChecklistCommand.parseLong(args.positional.get(0), "recipe id");
        String name = facade.getRecipe(id).getName();
        boolean now = facade.toggleFavourite(id);
        return CommandResult.ok(now ? name + " added to favourites (*)." : name + " removed from favourites.");
    }

    private CommandResult list() {
        List<Recipe> favourites = facade.getFavourites();
        if (favourites.isEmpty()) {
            return CommandResult.ok("No favourite recipes yet. Use 'fav toggle <recipe id>'.");
        }
        StringBuilder sb = new StringBuilder("Favourites:");
        favourites.forEach(r -> sb.append("\n").append(RecipeCommand.line(r)));
        return CommandResult.ok(sb.toString());
    }
}
