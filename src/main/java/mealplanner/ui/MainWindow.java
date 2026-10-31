package mealplanner.ui;

import java.util.ArrayList;
import java.util.List;
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
 * The ☰ button in the top bar hides and shows the menu. Theme decide how it look.
 */
public class MainWindow {

    private final BorderPane root = new BorderPane();
    private final VBox menu = new VBox();
    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> buttons = new ArrayList<>();

    public MainWindow(MealCalendarView calendarView, ChecklistView checklistView, PantryView pantryView) {
        Button toggle = new Button("☰");
        toggle.getStyleClass().add("menu-toggle");
        toggle.setOnAction(e -> toggleMenu());
        Label title = new Label("Meal Planner");
        title.getStyleClass().add("app-title");
        HBox topBar = new HBox(toggle, title);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(topBar);

        menu.getStyleClass().add("side-menu");
        menu.setPrefWidth(200);
        root.setLeft(menu);

        addPage("Meal Calendar", "Plan your meals for the week.", calendarView);
        addPage("Shopping", "Checklists, and shopping lists made from your meals.", checklistView);
        addPage("Pantry", "What you have at home right now.", pantryView);

        buttons.get(0).setSelected(true);
    }

    /** Hides the menu if it is showing, or shows it again if it is hidden. */
    private void toggleMenu() {
        root.setLeft(root.getLeft() == null ? menu : null);
    }

    /** Adds a menu button that shows the given screen, under a page title and short description. */
    private void addPage(String name, String description, Node content) {
        Label heading = new Label(name);
        heading.getStyleClass().add("page-title");
        Label subtitle = new Label(description);
        subtitle.getStyleClass().add("description");
        VBox header = new VBox(4, heading, subtitle);
        header.getStyleClass().add("page-header");
        BorderPane page = new BorderPane(content);
        page.setTop(header);
        page.getStyleClass().add("page");

        ToggleButton button = new ToggleButton(name);
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) {
                root.setCenter(page);
            } else if (group.getSelectedToggle() == null) {
                button.setSelected(true); // keep one page chosen
            }
        });
        buttons.add(button);
        menu.getChildren().add(button);
    }

    public void show(Stage stage) {
        Theme.install();
        stage.setTitle("Smart Recipe & Meal Planner");
        stage.setScene(new Scene(root, 1300, 820));
        stage.show();
    }
}