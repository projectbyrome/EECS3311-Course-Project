package mealplanner.ui;

import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import mealplanner.app.AppFacade;
import mealplanner.domain.AppException;
import mealplanner.domain.MealSlot;
import mealplanner.model.DietGuideReply;

/**
 * Diet Guide page: a chat with Claude about types of diets and what nutrition coaches recommend.
 * Eat plenty of / Eat moderately / Eat rarely boxes, what coaches usually recommend, and a sample day whose
 */
public class DietGuideView extends BorderPane {

    private static final Color USER_BUBBLE = Color.web("#1F3460");
    private static final Color PLENTY = Color.web("#6FD8AE");
    private static final Color MODERATE = Color.web("#F7C163");
    private static final Color RARELY = Color.web("#F58A8F");
    private static final Color COACH_BACK = Color.web("#17366E");
    private static final List<String[]> TOPICS = List.of(
            new String[]{"Mediterranean diet", "What is the Mediterranean diet, and what would a nutrition coach recommend?"},
            new String[]{"High-protein", "What does a high-protein diet look like, and how much protein do coaches usually suggest?"},
            new String[]{"Fat loss", "What do nutrition coaches generally recommend for fat loss?"},
            new String[]{"Athletes", "How do athletes usually eat, and what do sports nutritionists recommend?"},
            new String[]{"Vegetarian & vegan", "How do vegetarian and vegan diets work, and what should people watch out for?"},
            new String[]{"Keto & low-carb", "What are keto and low-carb diets, and what are their pros and cons?"});

    private final AppFacade facade;
    private final VBox messages = new VBox(16);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = new Button("Send");
    private final FlowPane topics = new FlowPane(8, 8);
    private Consumer<String> cookHandler = dish -> { };

    public DietGuideView(AppFacade facade) {
        this.facade = facade;

        Label topicTitle = new Label("Pick a topic or ask your own question");
        topicTitle.getStyleClass().add("card-title");
        for (String[] topic : TOPICS) {
            Button b = new Button(topic[0]);
            b.setOnAction(e -> ask(topic[1]));
            topics.getChildren().add(b);
        }
        Label note = new Label("General information about diets, not personal or medical advice. "
                + "For a personal plan, talk to a registered dietitian or your doctor.");
        note.getStyleClass().add("description");
        note.setWrapText(true);
        VBox intro = new VBox(10, topicTitle, topics, note);
        intro.getStyleClass().add("card");

        messages.setPadding(new Insets(4, 14, 4, 0));
        scroll.setFitToWidth(true);
        messages.heightProperty().addListener((obs, was, now) -> scroll.setVvalue(1.0)); // follow the newest message
        showEmptyHint();

        input.setPromptText("Ask about a diet, e.g. \"Is intermittent fasting good for fat loss?\"");
        input.setOnAction(e -> ask(input.getText()));
        HBox.setHgrow(input, Priority.ALWAYS);
        send.getStyleClass().add("primary");
        send.setOnAction(e -> ask(input.getText()));
        Button newChat = new Button("New chat");
        newChat.setOnAction(e -> {
            facade.startNewDietChat();
            messages.getChildren().clear();
            showEmptyHint();
        });
        HBox inputRow = new HBox(8, input, send, newChat);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox chatCard = new VBox(12, scroll, inputRow);
        chatCard.getStyleClass().add("card");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        BorderPane.setMargin(chatCard, new Insets(12, 0, 0, 0));

        setTop(intro);
        setCenter(chatCard);
    }

    public void setCookHandler(Consumer<String> handler) {
        this.cookHandler = handler;
    }

    /** Shows the question, then asks Claude in the background and shows the answer when it arrives. */
    public void ask(String question) {
        if (question == null || question.isBlank() || send.isDisabled()) {
            return;
        }
        if (messages.getChildren().size() == 1 && messages.getChildren().get(0).getStyleClass().contains("empty")) {
            messages.getChildren().clear();
        }
        input.clear();
        messages.getChildren().add(bubble(question.trim(), true));
        Label thinking = new Label("Thinking…");
        thinking.getStyleClass().add("description");
        messages.getChildren().add(thinking);
        setBusy(true);
        Thread worker = new Thread(() -> {
            DietGuideReply reply;
            try {
                reply = facade.askDietGuide(question);
            } catch (AppException ex) {
                reply = new DietGuideReply(ex.getMessage(), List.of());
            }
            DietGuideReply shown = reply;
            Platform.runLater(() -> {
                messages.getChildren().remove(thinking);
                messages.getChildren().add(shown.isDietCard() ? dietCard(shown) : plainAnswer(shown));
                setBusy(false);
                input.requestFocus();
            });
        }, "diet-guide");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        send.setDisable(busy);
        topics.setDisable(busy);
    }

    private void showEmptyHint() {
        Label hint = new Label("No messages yet. Your conversation will appear here.");
        hint.getStyleClass().addAll("description", "empty");
        hint.setPadding(new Insets(8));
        messages.getChildren().add(hint);
    }

    /** A diet answer laid out like the mock-up: title, summary, three food boxes, coaches' advice, sample day. */
    private VBox dietCard(DietGuideReply r) {
        Text title = new Text(r.diet);
        title.setFont(Font.font("Inter SemiBold", 24));
        title.setFill(Theme.TEXT);
        VBox card = new VBox(14, title);
        if (has(r.summary)) {
            Label summary = wrapped(r.summary);
            summary.getStyleClass().add("description");
            card.getChildren().add(summary);
        }
        HBox foods = new HBox(10);
        addFoodBox(foods, "Eat plenty of", r.eatPlenty, PLENTY);
        addFoodBox(foods, "Eat moderately", r.eatModerately, MODERATE);
        addFoodBox(foods, "Eat rarely", r.eatRarely, RARELY);
        if (!foods.getChildren().isEmpty()) {
            card.getChildren().add(foods);
        }
        if (has(r.coachesRecommend)) {
            Label heading = new Label("What coaches usually recommend");
            heading.getStyleClass().add("card-title");
            VBox coach = new VBox(6, heading, wrapped(r.coachesRecommend));
            coach.setPadding(new Insets(14, 16, 14, 16));
            coach.setBackground(fill(COACH_BACK, 10));
            coach.setBorder(new Border(new BorderStroke(Theme.ACCENT, BorderStrokeStyle.SOLID, new CornerRadii(10),
                    BorderWidths.DEFAULT)));
            card.getChildren().add(coach);
        }
        if (has(r.text)) {
            card.getChildren().add(wrapped(DietGuideView.tidy(r.text)));
        }
        if (r.hasSampleDay()) {
            card.getChildren().add(sampleDay(r));
        }
        card.setPadding(new Insets(4, 0, 4, 0));
        return card;
    }

    private static void addFoodBox(HBox row, String heading, String foods, Color colour) {
        if (!has(foods)) {
            return;
        }
        Text title = new Text(heading);
        title.setFont(Font.font("Inter SemiBold", 13));
        title.setFill(colour);
        VBox box = new VBox(8, title, wrapped(foods));
        box.setPadding(new Insets(12, 14, 14, 14));
        box.setBackground(fill(Theme.PANEL, 10));
        box.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID, new CornerRadii(10),
                BorderWidths.DEFAULT)));
        box.setMinWidth(0);
        box.setPrefWidth(0); // the three boxes share the width equally
        HBox.setHgrow(box, Priority.ALWAYS);
        row.getChildren().add(box);
    }

    /** "A day on this diet": one row per dish, each with a button that looks up how to cook it. */
    private VBox sampleDay(DietGuideReply reply) {
        Label title = new Label("A day on this diet");
        title.getStyleClass().add("section-title");
        VBox box = new VBox(0, title);
        VBox.setMargin(title, new Insets(4, 0, 8, 0));
        for (DietGuideReply.SampleDish d : reply.sampleDay) {
            Label slot = new Label(d.slot.name().charAt(0) + d.slot.name().substring(1).toLowerCase());
            slot.getStyleClass().add("small-label");
            Label dish = new Label(d.dish);
            dish.getStyleClass().add("card-title");
            dish.setWrapText(true);
            VBox words = new VBox(2, slot, dish);
            HBox.setHgrow(words, Priority.ALWAYS);
            Circle dot = new Circle(4, dotColour(d.slot));
            Button cook = new Button("Check Recipe");
            cook.setOnAction(e -> cookHandler.accept(d.dish));
            HBox row = new HBox(12, dot, words, cook);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 0, 10, 2));
            row.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                    new BorderWidths(1, 0, 0, 0))));
            box.getChildren().add(row);
        }
        return box;
    }

    /** An ordinary answer: a bubble on the left. */
    private static HBox plainAnswer(DietGuideReply r) {
        return bubble(r.text, false);
    }

    /** A chat bubble: the user's on the right in blue, the Diet Guide's on the left in the panel colour. */
    private static HBox bubble(String text, boolean fromUser) {
        Label body = wrapped(tidy(text));
        VBox box = new VBox(4, body);
        if (!fromUser) {
            Label who = new Label("Diet Guide");
            who.getStyleClass().add("small-label");
            box.getChildren().add(0, who);
        }
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setMaxWidth(560);
        box.setBackground(fill(fromUser ? USER_BUBBLE : Theme.PANEL, 12));
        HBox row = new HBox(box);
        row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static Background fill(Color colour, double radius) {
        return new Background(new BackgroundFill(colour, new CornerRadii(radius), Insets.EMPTY));
    }

    /** Same slot colours as the Meal Calendar. */
    private static Color dotColour(MealSlot slot) {
        return switch (slot) {
            case BREAKFAST -> Color.web("#F5A524");
            case LUNCH -> Color.web("#34C38F");
            case DINNER -> Color.web("#3478F6");
            case SNACK -> Color.web("#A78BFA");
        };
    }

    /** Claude sometimes still uses Markdown: drop ** and # and turn "- " or "* " bullets into "• ". */
    public static String tidy(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.replace("**", "").split("\n", -1)) {
            String l = line.replaceFirst("^#+\\s*", "");
            if (l.startsWith("- ") || l.startsWith("* ")) {
                l = "• " + l.substring(2);
            }
            sb.append(l).append('\n');
        }
        return sb.toString().trim();
    }
}