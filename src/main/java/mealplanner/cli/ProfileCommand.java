package mealplanner.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import mealplanner.app.AppFacade;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;

/**
 * F06 from the command line (same save-and-event structure as SD02): profile show | set.
 * Options that are left out keep their saved value; "none" clears a list.
 */
public class ProfileCommand extends AbstractCliCommand {

    public ProfileCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public String usage() {
        return """
                profile show
                profile set [--restrictions vegetarian,gluten-free] [--allergies peanut,milk] [--categories Soup,Spicy,Poultry]
                  restrictions: vegetarian, vegan, pescatarian, gluten-free, dairy-free, halal ("none" clears)
                  categories: any recipe category, e.g. Soup, Stir-Fried, Spicy, Poultry""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        return switch (args.subcommand) {
            case "show", "" -> CommandResult.ok(facade.getProfile().toString());
            case "set" -> set(args);
            default -> CommandResult.error("Unknown sub-command '" + args.subcommand + "'.\n" + usage());
        };
    }

    /** SD02 structure: execute(args) -> updateProfile(profile) -> ok, or the list of problems. */
    private CommandResult set(CliArgs args) {
        String restrictions = args.options.get("restrictions");
        String allergies = args.options.get("allergies");
        String categories = args.options.get("categories");
        if (restrictions == null && allergies == null && categories == null) {
            return CommandResult.error("Nothing to change.\n" + usage());
        }
        UserProfile profile = facade.getProfile().copy();
        if (restrictions != null) {
            Set<DietaryRestriction> set = new LinkedHashSet<>();
            for (String r : list(restrictions)) {
                set.add(DietaryRestriction.parse(r)); // unknown -> InvalidInputException with the choices
            }
            profile.setRestrictions(set);
        }
        if (allergies != null) {
            profile.setAllergies(new LinkedHashSet<>(list(allergies)));
        }
        if (categories != null) {
            profile.setFavouriteCategories(new LinkedHashSet<>(list(categories)));
        }
        ValidationReport report = facade.updateProfile(profile);
        if (!report.isValid()) {
            return CommandResult.error("Profile not saved: " + String.join("; ", report.getViolations()));
        }
        return CommandResult.ok("Profile saved.\n" + facade.getProfile());
    }

    /** "a,b" -> [a, b]; "none" -> []; underscores stand for spaces ("tree_nut"). */
    private static List<String> list(String csv) {
        if (csv.equalsIgnoreCase("none")) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(s -> s.replace('_', ' ').replace("\"", "").trim()).toList();
    }
}