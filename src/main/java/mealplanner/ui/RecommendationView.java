package mealplanner.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.NutritionSummary;
import mealplanner.domain.Recipe;
import mealplanner.domain.RecipeCategory;
import mealplanner.domain.RecipeIngredient;
import mealplanner.model.Recommendation;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationResult;

/**
 * F07 GUI (Recommendations menu): an optional description for the assistant, a filter form (category,
 * cooking time, nutrition goal), Search, and recipe cards with an "Add to library" button.
 */
public class RecommendationView extends BorderPane {

    private static final String ANY = "Any";

    private final AppFacade facade;
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField minutesField = new TextField();
    private final ComboBox<String> goalBox = new ComboBox<>();
    private final TextField requestField = new TextField();
    private final Button search = new Button("Search");
    private final VBox cards = new VBox(12);
    private final Label message = new Label();

    public RecommendationView(AppFacade facade) {
        this.facade = facade;
        loadCategories();
        categoryBox.setOnShowing(e -> loadCategories()); // the list is read again each time it opens
        minutesField.setPromptText("Max minutes");
        minutesField.setPrefWidth(110);
        goalBox.getItems().addAll("No goal", "My daily goal", "High protein", "Low calorie", "Low carb");
        goalBox.setValue("No goal");
        search.getStyleClass().add("primary");
        search.setOnAction(e -> onSearch());
        requestField.setPromptText("Optional, e.g. something Japanese, or anything I can pair with rice");
        requestField.setOnAction(e -> onSearch()); // Enter searches too

        HBox form = new HBox(12, field("Category", categoryBox), field("Cooking time", minutesField),
                field("Nutrition goal", goalBox), search);
        form.setAlignment(Pos.BOTTOM_LEFT);
        Label title = new Label("What would you like to eat?");
        title.getStyleClass().add("card-title");
        Label hint = new Label("Your dietary restrictions and allergies from the Profile are always applied.");
        hint.getStyleClass().add("description");
        VBox searchCard = new VBox(10, title, hint, field("Describe what you want (the assistant reads this)", requestField), form);
        searchCard.getStyleClass().add("card");

        message.getStyleClass().add("description");
        message.setWrapText(true);
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);

        VBox top = new VBox(12, searchCard, message);
        BorderPane.setMargin(top, new Insets(0, 0, 12, 0));
        setTop(top);
        setCenter(scroll);
    }

    /** SD06: recommendRecipes(filter) in the background, then one card per recommendation. */
    public void onSearch() {
        RecommendationFilter filter;
        try {
            filter = readFilter();
        } catch (AppException ex) {
            showError(ex.getMessage());
            return;
        }
        Task<RecommendationResult> task = new Task<>() {
            @Override
            protected RecommendationResult call() {
                return facade.recommendRecipes(filter);
            }
        };
        search.setDisable(true);
        cards.getChildren().clear();
        message.setText("Searching... the assistant may take a few seconds.");
        task.setOnSucceeded(e -> {
            search.setDisable(false);
            show(task.getValue());
        });
        task.setOnFailed(e -> {
            search.setDisable(false);
            message.setText("");
            showError(task.getException().getMessage());
        });
        Thread thread = new Thread(task, "recipe-recommender");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * UC07 step 6
     */
    public void onApprove(Recommendation recommendation, Button button) {
        try {
            Recipe added = recommendation.written ? facade.approveWrittenRecipe(recommendation.recipe)
                    : facade.approveRecommendation(recommendation.recipe.getCatalogId());
            message.setText("Added to your recipe library: " + added.getName()
                    + (added.getWarnings().isEmpty() ? "" : ". Warnings: " + String.join("; ", added.getWarnings())));
            button.setText("Added");
            button.setDisable(true);
        } catch (AppException ex) {
            showError(ex.getMessage());
        }
    }

    private RecommendationFilter readFilter() {
        RecommendationFilter filter = new RecommendationFilter();
        String category = categoryBox.getValue();
        filter.category = category == null || category.equals(ANY) ? null : category;
        String minutes = minutesField.getText().trim();
        if (!minutes.isEmpty()) {
            try {
                filter.maxCookMinutes = Integer.parseInt(minutes);
            } catch (NumberFormatException e) {
                throw new InvalidInputException("Cooking time must be a whole number of minutes");
            }
        }
        filter.goal = switch (goalBox.getValue()) {
            case "My daily goal" -> facade.getProfile().getDefaultGoal();
            case "High protein" -> NutritionGoal.preset("high-protein");
            case "Low calorie" -> NutritionGoal.preset("low-calorie");
            case "Low carb" -> NutritionGoal.preset("low-carb");
            default -> null;
        };
        filter.request = requestField.getText().isBlank() ? null : requestField.getText().trim();
        return filter;
    }

    private void show(RecommendationResult result) {
        cards.getChildren().clear();
        message.setText(result.message == null ? "" : result.message);
        for (Recommendation r : result.recommendations) {
            cards.getChildren().add(card(r));
        }
    }

    /** One recipe card: name, categories and time, nutrition, ingredients, the assistant's reason, Add. */
    private VBox card(Recommendation r) {
        Recipe recipe = r.recipe;
        Label title = new Label(recipe.getName());
        title.getStyleClass().add("card-title");
        Label details = new Label(String.join(" · ", recipe.getCategories().stream()
                .map(RecipeCategory::getName).toList()) + "  ·  " + recipe.getCookMinutes() + " min");
        details.getStyleClass().add("small-label");
        details.setWrapText(true);
        NutritionSummary n = recipe.getNutritionPerServing();
        Label nutrition = new Label(n == null ? "" : String.format(
                (r.written ? "About" : "Per serving:") + " %.0f kcal, %.0f g protein, %.0f g carbs, %.0f g fat"
                        + (r.written ? " per serving (Claude's estimate)" : ""),
                n.getCalories(), n.getProteinG(), n.getCarbsG(), n.getFatG()));
        Label ingredients = new Label("Ingredients: " + String.join(", ",
                recipe.ingredients().stream().map(RecipeIngredient::toString).toList()));
        ingredients.getStyleClass().add("description");
        ingredients.setWrapText(true);
        VBox card = new VBox(6, title, details, nutrition, ingredients);
        if (r.written) {
            Label written = new Label("New recipe written by Claude (not from the catalogue)");
            written.setStyle("-fx-text-fill: #F5A524;"); // amber, so it stands out from catalogue recipes
            card.getChildren().add(1, written);
            if (!recipe.getInstructions().isEmpty()) {
                StringBuilder steps = new StringBuilder("Steps:");
                for (int i = 0; i < recipe.getInstructions().size(); i++) {
                    steps.append("\n").append(i + 1).append(". ").append(recipe.getInstructions().get(i));
                }
                Label stepsLabel = new Label(steps.toString());
                stepsLabel.getStyleClass().add("description");
                stepsLabel.setWrapText(true);
                card.getChildren().add(stepsLabel);
            }
        }
        if (r.reason != null && !r.reason.isBlank()) {
            Label reason = new Label("Why: " + r.reason);
            reason.setWrapText(true);
            card.getChildren().add(reason);
        }
        Button add = new Button("Add to library");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> onApprove(r, add));
        card.getChildren().add(add);
        card.getStyleClass().add("card");
        return card;
    }

    private void loadCategories() {
        String chosen = categoryBox.getValue();
        categoryBox.getItems().setAll(ANY);
        categoryBox.getItems().addAll(facade.catalogCategories());
        categoryBox.setValue(chosen == null ? ANY : chosen);
    }

    private static VBox field(String name, Node input) {
        Label label = new Label(name);
        label.getStyleClass().add("small-label");
        return new VBox(6, label, input);
    }

    private void showError(String text) {
        new Alert(Alert.AlertType.ERROR, text).showAndWait();
    }
}