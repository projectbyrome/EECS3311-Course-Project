package mealplanner.cli;

/** Command interface  */
public interface CliCommand {

    String name();

    String usage();

    CommandResult execute(CliArgs args);
}
