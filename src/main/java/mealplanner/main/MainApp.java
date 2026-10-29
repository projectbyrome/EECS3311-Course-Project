package mealplanner.main;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Starts the program. With no arguments it opens the window; with "--cli" it runs the command line.
 * MainApp itself does not extend Application, so the CLI can start without JavaFX. The nested Fx class does it.
 */
public class MainApp {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--cli")) {
            AppContext context = AppContext.create(AppConfig.fromEnvironment(), UiMode.CLI);
            context.facade().closePastDays(LocalDate.now()).forEach(w -> System.out.println("Note: " + w));
            context.cliApp().run(Arrays.copyOfRange(args, 1, args.length));
        } else {
            Application.launch(Fx.class, args);
        }
    }

    /** JavaFX entry point; only forwards to MainApp.start(). */
    public static class Fx extends Application {
        @Override
        public void start(Stage stage) {
            new MainApp().start(stage);
        }
    }

    public void start(Stage stage) {
        AppContext context = AppContext.create(AppConfig.fromEnvironment(), UiMode.GUI);
        context.mainWindow().show(stage);
        closePastDays(context);
        scheduleMidnightUpdate(context);
    }

    /** SD08: the Clock actor. Runs once at start-up, then every midnight. */
    private void scheduleMidnightUpdate(AppContext context) {
        ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "midnight-clock");
            t.setDaemon(true);
            return t;
        });
        LocalDateTime now = LocalDateTime.now();
        long untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
        clock.scheduleAtFixedRate(() -> Platform.runLater(() -> closePastDays(context)),
                untilMidnight + 1000, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    private static void closePastDays(AppContext context) {
        List<String> warnings = context.facade().closePastDays(LocalDate.now());
        if (!warnings.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, String.join("\n", warnings)).show();
        }
    }
}