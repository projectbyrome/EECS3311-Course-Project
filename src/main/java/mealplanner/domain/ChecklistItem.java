package mealplanner.domain;

/**
 * One grocery item on a checklist, with a quantity and an "already in pantry" flag (F02).
 * A staple (ketchup, salt...) has no amount: its quantity is null and Finish Shopping marks it "Have".
 */
public class ChecklistItem {

    /** 0 until the ChecklistRepository saves the item. */
    private long id;
    private final String name;
    private final Quantity quantity;
    private boolean checked;
    private boolean alreadyInPantry;
    /** How much the pantry has, so the flag can say "already in pantry: 6 PIECE". Null if not in the pantry. */
    private Quantity pantryQuantity;

    public ChecklistItem(String name, Quantity quantity) {
        this(name, quantity, false);
    }

    /** A staple item: no amount needed. */
    public static ChecklistItem staple(String name) {
        return new ChecklistItem(name, null, true);
    }

    private ChecklistItem(String name, Quantity quantity, boolean staple) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("An item needs a name");
        }
        if (!staple && (quantity == null || quantity.getAmount() <= 0)) {
            throw new InvalidInputException("Quantity of " + name + " must be greater than 0");
        }
        this.name = name.trim();
        this.quantity = staple ? null : quantity;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    /** Sets the flag from the pantry amount; null means the item is not in the pantry. */
    public void setPantryQuantity(Quantity have) {
        this.pantryQuantity = have;
        this.alreadyInPantry = have != null;
    }

    /** Used when loading from the database, where only the flag is stored. */
    public void setAlreadyInPantry(boolean flag) {
        this.alreadyInPantry = flag;
        if (!flag) this.pantryQuantity = null;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    /** Null for a staple. */
    public Quantity getQuantity() {
        return quantity;
    }
    public boolean isStaple() {
        return quantity == null;
    }
    /** "12 PIECE", or "staple" for a staple. */
    public String amountText() {
        return isStaple() ? "staple" : quantity.toString();
    }
    public boolean isChecked() {
        return checked;
    }
    public boolean isAlreadyInPantry() {
        return alreadyInPantry;
    }
    public Quantity getPantryQuantity() {
        return pantryQuantity;
    }

    @Override
    public String toString() {
        String text = (checked ? "[x] " : "[ ] ") + name + " " + amountText();
        if (alreadyInPantry) {
            text += "  (already in pantry" + (pantryQuantity != null ? ": " + pantryQuantity : "") + ")";
        }
        return text;
    }
}