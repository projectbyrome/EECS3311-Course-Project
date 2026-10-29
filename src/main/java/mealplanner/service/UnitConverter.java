package mealplanner.service;

import mealplanner.domain.IncompatibleUnitException;
import mealplanner.domain.Ingredient;
import mealplanner.domain.Quantity;
import mealplanner.domain.Unit;

/**
 * Converts quantities between units. Mass and volume units convert directly within their family;
 * across families (or to PIECE) using the ingredient's density or weight per piece.
 */
public class UnitConverter {

    public Quantity convert(Quantity q, Unit target, Ingredient ingredient) {
        Unit from = q.getUnit();
        if (from == target) {
            return q;
        }
        if (!canConvert(from, target, ingredient)) {
            throw new IncompatibleUnitException(
                    "Cannot convert " + from + " to " + target + " for " + ingredient.getName());
        }
        if (isMass(from) && isMass(target)) {
            return new Quantity(q.getAmount() * gramsIn(from) / gramsIn(target), target);
        }
        if (isVolume(from) && isVolume(target)) {
            return new Quantity(q.getAmount() * mlIn(from) / mlIn(target), target);
        }
        double grams = toGrams(q, ingredient);
        return new Quantity(fromGrams(grams, target, ingredient), target);
    }

    public boolean canConvert(Unit from, Unit to, Ingredient ingredient) {
        if (from == to) {
            return true;
        }
        if (isMass(from) && isMass(to)) {
            return true;
        }
        if (isVolume(from) && isVolume(to)) {
            return true;
        }
        return hasGramFactor(from, ingredient) && hasGramFactor(to, ingredient);
    }

    public double toGrams(Quantity q, Ingredient ingredient) {
        Unit u = q.getUnit();
        if (isMass(u)) {
            return q.getAmount() * gramsIn(u);
        }
        if (isVolume(u) && ingredient.getDensityGPerMl() > 0) {
            return q.getAmount() * mlIn(u) * ingredient.getDensityGPerMl();
        }
        if (u == Unit.PIECE && ingredient.getGramsPerPiece() > 0) {
            return q.getAmount() * ingredient.getGramsPerPiece();
        }
        throw new IncompatibleUnitException("No gram value for " + u + " of " + ingredient.getName());
    }

    private double fromGrams(double grams, Unit target, Ingredient ingredient) {
        if (isMass(target)) {
            return grams / gramsIn(target);
        }
        if (isVolume(target)) {
            return grams / ingredient.getDensityGPerMl() / mlIn(target);
        }
        return grams / ingredient.getGramsPerPiece(); // PIECE
    }

    private boolean hasGramFactor(Unit u, Ingredient ingredient) {
        if (isMass(u)) return true;
        if (isVolume(u)) return ingredient.getDensityGPerMl() > 0;
        return ingredient.getGramsPerPiece() > 0; // PIECE
    }

    private static boolean isMass(Unit u) {
        return u == Unit.G || u == Unit.KG;
    }

    private static boolean isVolume(Unit u) {
        return u == Unit.ML || u == Unit.L || u == Unit.TSP || u == Unit.TBSP || u == Unit.CUP;
    }

    private static double gramsIn(Unit u) {
        return u == Unit.KG ? 1000 : 1;
    }

    private static double mlIn(Unit u) {
        return switch (u) {
            case L -> 1000;
            case TSP -> 4.92892;
            case TBSP -> 14.7868;
            case CUP -> 240;
            default -> 1; // ML
        };
    }
}
