package mealplanner.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The main window: a menu down the left side (Profile first: it is also the home page), and the chosen feature's screen on the right.
 * The ☰ button in the top bar hides and shows the menu. Colours and fonts come from Theme.
 */
public class MainWindow {

    private final BorderPane root = new BorderPane();
    private final VBox menu = new VBox();
    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> buttons = new ArrayList<>();
    /** F10: the Nutrition Summary window, made the first time it is opened. */
    private Stage summaryStage;

    public MainWindow(MealCalendarView calendarView, ChecklistView checklistView, PantryView pantryView,
                      MacroGeneratorView macroGeneratorView, RecipeLibraryView recipeLibraryView, ProfileView profileView,
                      RecommendationView recommendationView, NutritionSummaryView nutritionSummaryView,
                      HomeView homeView) {
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

        homeView.setPageOpener(this::showPage);
        homeView.setSummaryHandler(nutritionSummaryView::onShowDay);
        addPage("Profile", "What are we eating today? Your meals, your diet and your goals in one place.",
                profileHomePage(homeView, profileView));
        addPage("Meal Calendar", "Plan your meals for the month, and see their nutrition.", calendarView);
        connectSummaryWindow(nutritionSummaryView);
        addPage("Shopping", "Checklists, and shopping lists made from your meals.", checklistView);
        addPage("Pantry", "What you have at home right now.", pantryView);
        addPage("Macro Generator", "Calories, protein, carbs, fat and sugar for recipes and ingredients.", macroGeneratorView);
        addPage("Recipe Library", "Your own recipes: add, edit, remove and mark favourites.", recipeLibraryView);
        addPage("Recommendations", "Recipe ideas from the catalogue that fit your profile.", recommendationView);

        buttons.get(0).setSelected(true); // open on the Profile page (the home page)
    }

    /**
     * F10: the Nutrition Summary opens in its own window next to the main one, so the month calendar keeps
     * its full size. "Day nutrition" / "Week nutrition" in the calendar open it; its Close button hides it.
     */
    private void connectSummaryWindow(NutritionSummaryView summaryView) {
        summaryView.setOpenCloseHandlers(() -> {
            if (summaryStage == null) {
                Label heading = new Label("Nutrition Summary");
                heading.getStyleClass().add("page-title");
                Label subtitle = new Label("Your planned meals compared with your daily goals.");
                subtitle.getStyleClass().add("description");
                VBox header = new VBox(4, heading, subtitle);
                header.getStyleClass().add("page-header");
                BorderPane page = new BorderPane(summaryView);
                page.setTop(header);
                page.getStyleClass().add("page");
                summaryStage = new Stage();
                summaryStage.initOwner(root.getScene().getWindow());
                summaryStage.setTitle("Nutrition Summary");
                summaryStage.setScene(new Scene(page, 1000, 620));
            }
            summaryStage.show();
            summaryStage.toFront();
        }, () -> {
            if (summaryStage != null) summaryStage.hide();
        });
    }

    /**
     * The Profile page is also the home page: today's meals and the feature tiles on top, then the profile
     * cards (restrictions, allergies, favourite categories, daily goals). The whole page scrolls as one.
     */
    private static ScrollPane profileHomePage(HomeView homeView, ProfileView profileView) {
        homeView.setFitToHeight(false);
        homeView.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // the outer page scrolls instead
        homeView.setMinHeight(Region.USE_PREF_SIZE);
        Label yours = new Label("Your profile");
        yours.getStyleClass().add("section-title");
        VBox both = new VBox(18, homeView, yours, profileView);
        both.setPadding(new Insets(0, 8, 0, 0));
        ScrollPane page = new ScrollPane(both);
        page.setFitToWidth(true);
        return page;
    }

    /** Opens the page with this menu name, e.g. "Pantry" (used by the Home page tiles). */
    public void showPage(String name) {
        for (ToggleButton button : buttons) {
            if (button.getText().equals(name)) {
                button.setSelected(true);
            }
        }
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