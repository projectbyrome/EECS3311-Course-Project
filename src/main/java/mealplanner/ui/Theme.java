package mealplanner.ui;

import java.net.URL;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

/**
 * The app's dark look, written in plain Java (no CSS file).
 * The views only tag their nodes with names such as "primary" or "slot-cell". Theme watches every window
 * (the main one and each Alert or Dialog), visits every node in it, and sets its colours, borders and
 * fonts with JavaFX's Java API. It visits nodes again when they change state (hover, selected, focus),
 * and visits new nodes as they are added, so rebuilt calendar cells get the theme too.
 */
public final class Theme {

    // The palette. To change a colour, change it here.
    public static final Color BG = Color.web("#17191D");          // main workspace
    public static final Color PANEL = Color.web("#202329");       // top bar, sidebar, pop-ups
    public static final Color CARD = Color.web("#252932");        // cards, inputs, lists
    public static final Color BORDER = Color.web("#343943");      // dividers
    public static final Color TEXT = Color.web("#F1F3F5");        // main text
    public static final Color TEXT_MUTED = Color.web("#A9AFBB");  // descriptions, labels
    public static final Color ACCENT = Color.web("#3478F6");      // selected items, main buttons
    public static final Color WARNING = Color.web("#F2B84B");     // "already in pantry"
    public static final Color DANGER = Color.web("#F2545B");      // missing-ingredient flag

    // Lighter and darker steps of the palette, for hover, pressed and selected states.
    private static final Color CARD_HOVER = Color.web("#2F343E");
    private static final Color CARD_PRESSED = Color.web("#1F2229");
    private static final Color ACCENT_HOVER = Color.web("#4D8AF7");
    private static final Color NAV_SELECTED = Color.web("#17366E");
    private static final Color PAST_DAY = Color.web("#1E2025");
    private static final Color PROMPT = Color.web("#7A808C");

    // Inter: JavaFX names each weight as its own family, except Bold, which is "Inter" + FontWeight.BOLD.
    private static final String REGULAR = "Inter";
    private static final String MEDIUM = "Inter Medium";
    private static final String SEMIBOLD = "Inter SemiBold";
    private static final String[] FONT_FILES = {
            "Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Bold.ttf"};

    private static final String THEMED = "theme.visited";
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");

    private Theme() {
    }

    /** Call once, before the first window is shown. */
    public static void install() {
        for (String file : FONT_FILES) {
            URL url = Theme.class.getResource("/fonts/" + file);
            if (url != null) {
                Font.loadFont(url.toExternalForm(), 14);
            }
        }
        // Every new window (the main one, each Alert or Dialog, drop-down lists, right-click menus).
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    style(window.getScene());
                    window.sceneProperty().addListener((obs, old, scene) -> style(scene));
                }
            }
        });
    }

    private static void style(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.setFill(BG);
        visit(scene.getRoot());
        scene.rootProperty().addListener((obs, old, root) -> visit(root));
    }

    /** Styles a node and everything inside it, and keeps doing so when it changes. */
    private static void visit(Node node) {
        if (node == null || node.getProperties().containsKey(THEMED)) {
            return;
        }
        node.getProperties().put(THEMED, true);
        paint(node);
        // Hover, pressed, focused, selected... all show up as a change in the node's pseudo-class states.
        node.getPseudoClassStates().addListener((SetChangeListener<PseudoClass>) c -> paint(node));
        node.getStyleClass().addListener((ListChangeListener<String>) c -> paint(node));
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(Theme::visit);
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    change.getAddedSubList().forEach(Theme::visit);
                }
            });
        }
    }

    /** Picks the look for one node from its type and its style-class names. */
    private static void paint(Node node) {
        if (node instanceof TextField field) {
            paintTextField(field);
        } else if (node instanceof CheckBox box) {
            box.setTextFill(TEXT);
            box.setFont(font(REGULAR, 14));
            box.getChildrenUnmodifiable().stream()
                    .filter(n -> has(n, "box")).forEach(n -> paintRegion((Region) n)); // tick or untick the square
        } else if (node instanceof ButtonBase button) {
            paintButton(button);
        } else if (node instanceof TableRow<?> row) {
            paintTableRow(row);
        } else if (node instanceof TableCell<?, ?> cell) {
            cell.setPadding(new Insets(8, 6, 8, 6));
            cell.setBorder(Border.EMPTY);
            cell.setTextFill(TEXT);
            cell.setFont(font(REGULAR, 14));
        } else if (node instanceof Labeled labeled && has(node, "list-cell")) {
            paintListCell(labeled);
        } else if (node instanceof Label label) {
            paintLabel(label);
        } else if (node instanceof ListView<?> || node instanceof TableView<?>) {
            Region list = (Region) node;
            list.setBackground(fill(CARD, 10));
            list.setBorder(line(BORDER, 10));
            list.setPadding(new Insets(4));
        } else if (node instanceof ComboBoxBase<?> || node instanceof ChoiceBox<?> || node instanceof Spinner<?>) {
            Region input = (Region) node;
            input.setBackground(fill(CARD, 8));
            input.setBorder(line(node.isFocused() ? ACCENT : BORDER, 8));
        } else if (node instanceof DialogPane pane) {
            pane.setBackground(fill(PANEL, 0));
            if (pane.getScene() != null) {
                pane.getScene().setFill(PANEL);
            }
        } else if (node instanceof ScrollPane || node instanceof ScrollBar) {
            ((Region) node).setBackground(Background.EMPTY);
        } else if (node instanceof Region region) {
            paintRegion(region);
        }
    }

    private static void paintLabel(Label label) {
        label.setTextFill(TEXT);
        label.setFont(font(REGULAR, 14));
        if (has(label, "page-title")) {
            label.setFont(Font.font(REGULAR, FontWeight.BOLD, 26));
        } else if (has(label, "section-title")) {
            label.setFont(font(SEMIBOLD, 18));
        } else if (has(label, "card-title")) {
            label.setFont(font(SEMIBOLD, 15));
        } else if (has(label, "app-title")) {
            label.setFont(font(SEMIBOLD, 16));
        } else if (has(label, "description")) {
            muted(label, REGULAR, 13);
        } else if (has(label, "small-label")) {
            muted(label, MEDIUM, 12);
        } else if (has(label, "day-header")) {
            label.setFont(font(SEMIBOLD, 13));
            label.setTextFill(has(label, "today") ? ACCENT : TEXT);
        } else if (has(label, "slot-name")) {
            muted(label, MEDIUM, 12);
            label.setMinWidth(76);
        } else if (has(label, "meal")) {
            label.setFont(font(REGULAR, 13));
            label.setWrapText(true);
            label.setTextFill(has(label, "missing") ? DANGER : TEXT);
        } else if (has(label, "pantry-flag")) {
            label.setTextFill(WARNING);
            label.setFont(font(REGULAR, 12));
        } else if (has(label, "tooltip")) {
            label.setFont(font(REGULAR, 12));
            label.setBackground(fill(CARD, 6));
        } else if (inside(label, "column-header")) {
            muted(label, MEDIUM, 12); // table headings
            label.setAlignment(Pos.CENTER_LEFT);
        } else if (inside(label, "placeholder")) {
            muted(label, REGULAR, 14); // "No content in table"
        } else if (inside(label, "header-panel")) {
            label.setFont(font(SEMIBOLD, 15)); // pop-up heading
        } else if (has(label, "content")) {
            label.setPadding(new Insets(16)); // pop-up message
        }
    }

    private static void paintButton(ButtonBase button) {
        boolean hover = button.isHover();
        button.setCursor(Cursor.HAND);
        button.setFont(font(MEDIUM, 14));
        button.setTextFill(TEXT);
        if (has(button, "nav-button")) {
            boolean selected = button.getPseudoClassStates().contains(SELECTED);
            button.setTextFill(selected || hover ? TEXT : TEXT_MUTED);
            button.setPadding(new Insets(9, 12, 9, 12));
            button.setBackground(fill(selected ? NAV_SELECTED : hover ? CARD : Color.TRANSPARENT, 8));
            button.setBorder(selected
                    ? new Border(new BorderStroke(ACCENT, BorderStrokeStyle.SOLID,
                    new CornerRadii(8, 0, 0, 8, false), new BorderWidths(0, 0, 0, 3)))
                    : Border.EMPTY);
        } else if (has(button, "menu-toggle")) {
            button.setFont(font(REGULAR, 16));
            button.setTextFill(hover ? TEXT : TEXT_MUTED);
            button.setPadding(new Insets(4, 10, 4, 10));
            button.setBackground(fill(hover ? CARD : Color.TRANSPARENT, 8));
            button.setBorder(Border.EMPTY);
        } else if (has(button, "add-meal")) {
            button.setTextFill(hover ? ACCENT : TEXT_MUTED);
            button.setPadding(new Insets(0, 6, 0, 6));
            button.setBackground(fill(hover ? CARD_HOVER : Color.TRANSPARENT, 6));
            button.setBorder(Border.EMPTY);
        } else if (has(button, "primary") || has(button, "default")
                || (button instanceof javafx.scene.control.Button b && b.isDefaultButton())) {
            button.setTextFill(Color.WHITE);
            button.setPadding(new Insets(7, 14, 7, 14));
            button.setBackground(fill(hover ? ACCENT_HOVER : ACCENT, 8));
            button.setBorder(line(ACCENT, 8));
        } else if (inside(button, "arrow-button") || has(button, "arrow-button")) {
            button.setBackground(Background.EMPTY);
        } else {
            button.setPadding(new Insets(7, 14, 7, 14));
            button.setBackground(fill(button.isPressed() ? CARD_PRESSED : hover ? CARD_HOVER : CARD, 8));
            button.setBorder(line(button.isFocused() ? ACCENT : BORDER, 8));
        }
    }

    private static void paintTextField(TextField field) {
        // The text colour lives in the field's skin, so give the field our own skin that sets it.
        if (!(field.getSkin() instanceof DarkTextFieldSkin)) {
            field.setSkin(new DarkTextFieldSkin(field));
        }
        field.setFont(font(REGULAR, 14));
        if (field.getParent() != null && field.getParent() instanceof Spinner<?>
                || inside(field, "combo-box")) {
            field.setBackground(Background.EMPTY); // the box around it draws the border
            field.setBorder(Border.EMPTY);
        } else {
            field.setPadding(new Insets(7, 10, 7, 10));
            field.setBackground(fill(CARD, 8));
            field.setBorder(line(field.isFocused() ? ACCENT : BORDER, 8));
        }
    }

    private static void paintListCell(Labeled cell) {
        boolean selected = cell.getPseudoClassStates().contains(SELECTED);
        boolean empty = cell.getPseudoClassStates().contains(EMPTY);
        cell.setFont(font(REGULAR, 14));
        if (inside(cell, "combo-box") && !inside(cell, "list-view")) {
            cell.setTextFill(TEXT); // the chosen value shown in a closed drop-down
            cell.setBackground(Background.EMPTY);
            cell.setPadding(new Insets(5, 8, 5, 10));
            return;
        }
        cell.setPadding(new Insets(8, 10, 8, 10));
        cell.setTextFill(selected ? Color.WHITE : TEXT);
        Color back = selected && !empty ? ACCENT : cell.isHover() && !empty ? CARD_HOVER : Color.TRANSPARENT;
        cell.setBackground(fill(back, 6));
    }

    private static void paintTableRow(TableRow<?> row) {
        boolean empty = row.getPseudoClassStates().contains(EMPTY);
        boolean selected = row.getPseudoClassStates().contains(SELECTED);
        row.setBackground(fill(empty ? Color.TRANSPARENT
                : selected ? ACCENT : row.isHover() ? CARD_HOVER : Color.TRANSPARENT, 0));
        row.setBorder(empty ? Border.EMPTY : new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));
    }

    /** Plain containers and the small parts inside built-in controls. */
    private static void paintRegion(Region region) {
        if (has(region, "page")) {
            region.setBackground(fill(BG, 0));
            region.setPadding(new Insets(24, 28, 24, 28));
        } else if (has(region, "page-header")) {
            region.setPadding(new Insets(0, 0, 20, 0));
        } else if (has(region, "top-bar")) {
            region.setBackground(fill(PANEL, 0));
            region.setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                    CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));
            region.setPadding(new Insets(10, 16, 10, 12));
            if (region instanceof HBox box) {
                box.setSpacing(12);
            }
        } else if (has(region, "side-menu")) {
            region.setBackground(fill(PANEL, 0));
            region.setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                    CornerRadii.EMPTY, new BorderWidths(0, 1, 0, 0))));
            region.setPadding(new Insets(16, 12, 16, 12));
            if (region instanceof VBox box) {
                box.setSpacing(4);
            }
        } else if (has(region, "card")) {
            region.setBackground(fill(CARD, 10));
            region.setBorder(line(BORDER, 10));
            region.setPadding(new Insets(14));
        } else if (has(region, "slot-cell")) {
            boolean past = has(region, "past");
            region.setBackground(fill(past ? PAST_DAY : CARD, 8));
            region.setBorder(line(BORDER, 8));
            region.setPadding(new Insets(6));
            region.setMinSize(130, 64);
            region.setOpacity(past ? 0.6 : 1);
        } else if (has(region, "box")) {
            // the square of a check box
            boolean ticked = region.getParent() != null
                    && region.getParent().getPseudoClassStates().contains(SELECTED);
            region.setBackground(fill(ticked ? ACCENT : CARD, 4));
            region.setBorder(line(ticked ? ACCENT : TEXT_MUTED, 4));
            for (Node mark : region.getChildrenUnmodifiable()) {
                ((Region) mark).setBackground(fill(ticked ? Color.WHITE : Color.TRANSPARENT, 0)); // the tick
            }
        } else if (has(region, "mark")) {
            if (region.getParent() != null) {
                paintRegion((Region) region.getParent()); // the box decides whether the tick shows
            }
        } else if (has(region, "arrow") || has(region, "increment-arrow") || has(region, "decrement-arrow")) {
            region.setBackground(fill(TEXT_MUTED, 0)); // the little triangles in drop-downs and spinners
        } else if (has(region, "arrow-button") || has(region, "increment-arrow-button")
                || has(region, "decrement-arrow-button")) {
            region.setBackground(Background.EMPTY);
        } else if (has(region, "thumb")) {
            region.setBackground(fill(BORDER, 4)); // the part of a scroll bar you drag
        } else if (has(region, "increment-button") || has(region, "decrement-button")) {
            region.setBackground(Background.EMPTY);
            region.setPadding(Insets.EMPTY);
        } else if (has(region, "viewport") || has(region, "filler") || has(region, "column-header")
                || has(region, "nested-column-header") || has(region, "track")) {
            region.setBackground(Background.EMPTY);
            if (has(region, "column-header")) {
                region.setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                        CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));
                region.setPadding(new Insets(6, 4, 6, 4));
            }
        } else if (has(region, "column-header-background")) {
            region.setBackground(fill(CARD, 0));
        } else if (has(region, "header-panel")) {
            region.setBackground(fill(CARD, 0));
            region.setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                    CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));
        } else if (has(region, "context-menu")) {
            region.setBackground(fill(CARD, 8));
            region.setBorder(line(BORDER, 8));
        } else if (has(region, "menu-item")) {
            region.setBackground(fill(region.isFocused() ? ACCENT : Color.TRANSPARENT, 4));
        } else if (inside(region, "button-bar") && has(region, "container")) {
            region.setPadding(new Insets(10, 14, 14, 14));
        }
    }

    /** A TextField skin that can set the text colours, which are not reachable from the field itself. */
    private static final class DarkTextFieldSkin extends TextFieldSkin {
        DarkTextFieldSkin(TextField field) {
            super(field);
            setTextFill(TEXT);
            setPromptTextFill(PROMPT);
            setHighlightFill(ACCENT);
            setHighlightTextFill(Color.WHITE);
        }
    }

    // ---------- small helpers ----------

    private static boolean has(Node node, String styleClass) {
        return node.getStyleClass().contains(styleClass);
    }

    /** True if any parent of the node has the style class. */
    private static boolean inside(Node node, String styleClass) {
        for (Parent p = node.getParent(); p != null; p = p.getParent()) {
            if (p.getStyleClass().contains(styleClass)) {
                return true;
            }
        }
        return false;
    }

    private static void muted(Labeled label, String family, double size) {
        label.setTextFill(TEXT_MUTED);
        label.setFont(font(family, size));
    }

    private static Font font(String family, double size) {
        return Font.font(family, size);
    }

    private static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    private static Border line(Color color, double radius) {
        return new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, new CornerRadii(radius),
                BorderWidths.DEFAULT));
    }
}
