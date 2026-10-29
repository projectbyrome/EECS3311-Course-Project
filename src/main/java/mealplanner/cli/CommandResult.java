package mealplanner.cli;

/** What a command prints */
public class CommandResult {

    public final boolean success;
    public final String output;

    private CommandResult(boolean success, String output) {
        this.success = success;
        this.output = output;
    }

    public static CommandResult ok(String text) {
        return new CommandResult(true, text);
    }

    public static CommandResult error(String text) {
        return new CommandResult(false, "Error: " + text);
    }
}
