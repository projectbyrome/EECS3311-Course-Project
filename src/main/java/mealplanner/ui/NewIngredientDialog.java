package mealplanner.ui;

import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.model.IngredientCheck;

/**
 * Extra, shared by the Pantry and the Macro Generator: when a typed name isn't in the ingredient list
 * (e.g. ketchup), Claude is asked about it in the background, then the user decides whether to add it.
 * Claude may fix the name ("mayo" becomes "mayonnaise") and gives the category and allergens.
 * Without Claude, the name can still be added as it was typed (category Other, no allergen tags).
 */
public final class NewIngredientDialog {

    private NewIngredientDialog() {
    }

    /**
     * Calls onReady with the ingredient name to use: right away for a known name, otherwise after Claude's
     * check and the user's "Yes". Nothing happens if the user says no. A blank name goes straight through
     * so the page shows its usual error. Problems are passed to onError.
     */
    public static void ensureKnown(AppFacade facade, String typed, Node owner,
                                   Consumer<String> onReady, Consumer<String> onError) {
        ensureKnown(facade, typed, owner, onReady, onError, () -> { });
    }

    /** Same, plus onSkip, which runs when the user says No or Claude says it isn't a food (Shopping uses it). */
    public static void ensureKnown(AppFacade facade, String typed, Node owner,
                                   Consumer<String> onReady, Consumer<String> onError, Runnable onSkip) {
        if (typed == null || typed.isBlank() || facade.isKnownIngredient(typed)) {
            onReady.accept(typed);
            return;
        }
        String name = typed.trim();
        Task<IngredientCheck> task = new Task<>() {
            @Override
            protected IngredientCheck call() {
                return facade.checkIngredient(name);
            }
        };
        owner.setDisable(true);
        if (owner.getScene() != null) owner.getScene().setCursor(Cursor.WAIT);
        Runnable done = () -> {
            owner.setDisable(false);
            if (owner.getScene() != null) owner.getScene().setCursor(Cursor.DEFAULT);
        };
        task.setOnSucceeded(e -> {
            done.run();
            decide(facade, name, task.getValue(), onReady, onError, onSkip);
        });
        task.setOnFailed(e -> {
            done.run();
            decide(facade, name, IngredientCheck.unchecked(name), onReady, onError, onSkip);
        });
        Thread thread = new Thread(task, "ingredient-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void decide(AppFacade facade, String typed, IngredientCheck check,
                               Consumer<String> onReady, Consumer<String> onError, Runnable onSkip) {
        try {
            if (!check.food) {
                Alert no = new Alert(Alert.AlertType.WARNING, "Claude doesn't think \"" + typed + "\" is a food"
                        + (check.note.isBlank() ? "." : ": " + check.note) + "\nNothing was added.");
                no.setHeaderText("Not an ingredient");
                no.showAndWait();
                onSkip.run();
                return;
            }
            if (!check.name.equals(typed.toLowerCase()) && facade.isKnownIngredient(check.name)) {
                if (ask("Did you mean \"" + check.name + "\"? It's already in the ingredient list.", "Did you mean...")) {
                    onReady.accept(check.name);
                } else {
                    onSkip.run();
                }
                return;
            }
            StringBuilder text = new StringBuilder("\"" + typed + "\" isn't in the ingredient list yet.\n\n");
            if (check.checkedByClaude) {
                if (!check.name.equals(typed.toLowerCase())) {
                    text.append("Claude suggests the name \"").append(check.name).append("\".\n");
                }
                if (!check.note.isBlank()) text.append(check.note).append('\n');
                text.append("Category: ").append(check.category).append('\n');
                text.append("Allergens: ").append(check.allergens.isEmpty() ? "none" : String.join(", ", check.allergens));
            } else {
                text.append("Claude couldn't be reached, so it will be added with category Other and no allergen tags.");
            }
            text.append("\n\nAdd \"").append(check.name).append("\" to the ingredient list?");
            if (ask(text.toString(), "New ingredient")) {
                onReady.accept(facade.addIngredient(check).getName());
            } else {
                onSkip.run();
            }
        } catch (AppException ex) {
            onError.accept(ex.getMessage());
        }
    }

    private static boolean ask(String text, String header) {
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        ask.setHeaderText(header);
        return ask.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }
}