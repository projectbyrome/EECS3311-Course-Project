package mealplanner.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The main window: a menu down the left side, and the chosen feature's screen on the right.
 * The ☰ button in the top bar hides and shows the menu.
 */
public class MainWindow {

    private final BorderPane root = new BorderPane();
    private final VBox menu = new VBox(4);
    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> buttons = new ArrayList<>();

    public MainWindow(MealCalendarView calendarView, ChecklistView checklistView) {
        Button toggle = new Button("☰");
        toggle.setOnAction(e -> toggleMenu());
        Label title = new Label("Meal Planner");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox topBar = new HBox(10, toggle, title);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(6, 12, 6, 12));
        topBar.setStyle("-fx-background-color: #e8ebef; -fx-border-color: #cfd4da; -fx-border-width: 0 0 1 0;");
        root.setTop(topBar);

        menu.setPadding(new Insets(12));
        menu.setPrefWidth(180);
        menu.setStyle("-fx-background-color: #e8ebef; -fx-border-color: #cfd4da; -fx-border-width: 0 1 0 0;");
        root.setLeft(menu);

        addPage("Meal Calendar", calendarView);
        addPage("Shopping", checklistView);

        buttons.get(0).setSelected(true);
    }

    /** Hides the menu if it is showing, or shows it again if it is hidden. */
    private void toggleMenu() {
        root.setLeft(root.getLeft() == null ? menu : null);
    }

    /** Adds a menu button that shows the given screen when clicked. */
    private void addPage(String name, Node content) {
        ToggleButton button = new ToggleButton(name);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) {
                root.setCenter(content);
            } else if (group.getSelectedToggle() == null) {
                button.setSelected(true); // keep one page chosen
            }
        });
        buttons.add(button);
        menu.getChildren().add(button);
    }

    public void show(Stage stage) {
        stage.setTitle("Smart Recipe & Meal Planner");
        stage.setScene(new Scene(root, 1300, 820));
        stage.show();
    }
}