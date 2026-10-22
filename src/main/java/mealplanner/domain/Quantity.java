package mealplanner.domain;

import java.util.Objects;

public class Quantity {

    private final double amount;
    private final Unit unit;

    public Quantity(double amount, Unit unit) {
        if (amount < 0) {
            throw new InvalidInputException("Quantity can't be negative: " + amount);
        }
        this.amount = amount;
        this.unit = Objects.requireNonNull(unit, "Unit");
    }

    public double getAmount() { return amount; }
    public Unit getUnit() { return unit; }

    public Quantity plus(Quantity other) {
        if (other.unit != unit) {
            throw new IncompatibleUnitException("can't add " + other.unit + " to " + unit);
        }
        return new Quantity(amount + other.amount, unit);
    }

    public Quantity times(double factor) {
        return new Quantity(amount * factor, unit);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Quantity)) {
            return false;
        }
        Quantity q = (Quantity) o;
        return Double.compare(q.amount, amount) == 0 && q.unit == unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, unit);
    }

    @Override
    public String toString() {
        return amount + " " + unit;
    }
}

