package mealplanner.ui;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;

/**
 * F06 GUI (Profile menu): tick dietary restrictions, type allergies and favourite categories, then Save.
 * Rough draft: the layout is built in code; it can move to an .fxml file later.
 */
public class ProfileView extends VBox {

    private final AppFacade facade;
    private final Map<DietaryRestriction, CheckBox> restrictionBoxes = new EnumMap<>(DietaryRestriction.class);
    private final TextField allergiesField = new TextField();
    private final TextField categoriesField = new TextField();
    private final Label status = new Label();

    public ProfileView(AppFacade facade) {
        this.facade = facade;
        setSpacing(16);

        FlowPane restrictions = new FlowPane(16, 10);
        for (DietaryRestriction r : DietaryRestriction.values()) {
            CheckBox box = new CheckBox(r.label());
            restrictionBoxes.put(r, box);
            restrictions.getChildren().add(box);
        }
        allergiesField.setPromptText("e.g. peanut, milk, shellfish (comma separated)");
        categoriesField.setPromptText("e.g. Soup, Spicy, Poultry, Stir-Fried (comma separated)");

        Button save = new Button("Save profile");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> onSave());
        status.getStyleClass().add("description");

        getChildren().addAll(
                card("Dietary restrictions", "Strict: recipes that break these are left out.", restrictions),
                card("Allergies", "Strict: an allergen such as peanut, or an ingredient name.", allergiesField),
                card("Favourite categories", "Preferences: the assistant suggests these first. "
                        + "Any category works, from any group (Cooking Method, Dish Type, Flavor, Main Ingredient) "
                        + "or one of your own.", categoriesField),
                save, status);
        load();
    }

    /** UC06: updateProfile(profile); on problems nothing is saved and they are shown with suggestions. */
    public void onSave() {
        UserProfile profile = facade.getProfile().copy();
        Set<DietaryRestriction> chosen = new LinkedHashSet<>();
        restrictionBoxes.forEach((r, box) -> { if (box.isSelected()) chosen.add(r); });
        profile.setRestrictions(chosen);
        profile.setAllergies(split(allergiesField.getText()));
        profile.setFavouriteCategories(split(categoriesField.getText()));
        try {
            ValidationReport report = facade.updateProfile(profile);
            if (report.isValid()) {
                status.setText("Profile saved.");
                load();
            } else {
                status.setText("");
                new Alert(Alert.AlertType.ERROR, "Nothing was saved:\n" + String.join("\n", report.getViolations()))
                        .showAndWait();
            }
        } catch (AppException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    /** A titled card with a short explanation above its content. */
    private static VBox card(String title, String hint, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");
        Label explanation = new Label(hint);
        explanation.getStyleClass().add("description");
        explanation.setWrapText(true);
        VBox card = new VBox(8, heading, explanation, content);
        card.getStyleClass().add("card");
        return card;
    }

    private void load() {
        UserProfile p = facade.getProfile();
        restrictionBoxes.forEach((r, box) -> box.setSelected(p.getRestrictions().contains(r)));
        allergiesField.setText(String.join(", ", p.getAllergies()));
        categoriesField.setText(String.join(", ", p.getFavouriteCategories()));
    }

    private static Set<String> split(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return set;
        Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        return set;
    }
}