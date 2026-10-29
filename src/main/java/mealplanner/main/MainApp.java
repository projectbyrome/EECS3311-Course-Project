package mealplanner.main;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Starts the program. With "--cli" (optionally followed by one command, e.g. "--cli calendar show")
 * it runs the command line. The window comes in the next step.
 */
public class MainApp {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--cli")) {
            AppContext context = AppContext.create(AppConfig.fromEnvironment(), UiMode.CLI);
            context.facade().closePastDays(LocalDate.now()).forEach(w -> System.out.println("Note: " + w));
            context.cliApp().run(Arrays.copyOfRange(args, 1, args.length));
        } else {
            System.out.println("No window yet. Start program with --cli");
        }
    }
}