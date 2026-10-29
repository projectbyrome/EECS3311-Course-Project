package mealplanner.cli;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import mealplanner.domain.AppException;

/** Invoker of the Command pattern: finds the command by its verb and runs it. */
public class CliApp {

    private final Map<String, CliCommand> commands = new LinkedHashMap<>();
    private final Scanner in;
    private final PrintStream out;

    public CliApp(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void register(CliCommand command) {
        commands.put(command.name(), command);
    }

    /** Runs one command from the program arguments, or starts the interactive prompt if there are none. */
    public void run(String[] args) {
        if (args.length == 0) {
            repl();
        } else {
            print(dispatch(String.join(" ", args)));
        }
    }

    public void repl() {
        out.println("Type 'help' for commands, 'exit' to quit.");
        while (true) {
            out.print("> ");
            if (!in.hasNextLine()) return;
            String line = in.nextLine().trim();
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) return;
            if (!line.isEmpty()) {
                print(dispatch(line));
            }
        }
    }

    private CommandResult dispatch(String line) {
        CliArgs args = CliArgs.parse(line.trim().split("\\s+"));
        if (args.verb.equals("help")) {
            StringBuilder sb = new StringBuilder();
            commands.values().forEach(c -> sb.append(c.usage()).append("\n\n"));
            return CommandResult.ok(sb.toString().trim());
        }
        CliCommand command = commands.get(args.verb);
        if (command == null) {
            return CommandResult.error("Unknown command '" + args.verb + "'. Type 'help'.");
        }
        try {
            return command.execute(args);
        } catch (AppException e) {
            return CommandResult.error(e.getMessage());
        }
    }

    private void print(CommandResult result) {
        (result.success ? out : System.err).println(result.output);
    }
}
