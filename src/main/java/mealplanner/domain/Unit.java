package mealplanner.domain;


import java.util.Locale;

public enum Unit {
    G, KG, ML, L, TSP, TBSP, CUP, PIECE;

    public static Unit parse(String text){
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        switch(t) {
            case "gram", "grams" -> {return G;}
            case "kilogram", "kilograms" -> { return KG; }
            case "litre", "litres", "liter", "liters" -> { return L; }
            case "teaspoon", "teaspoons" -> { return TSP; }
            case "tablespoon", "tablespoons" -> { return TBSP; }
            case "pc", "pcs" -> { return PIECE; }
            default -> { }
        }
        for (String candidate : new String[] {t, t.endsWith("s") ? t.substring(0, t.length() - 1) : t}) {
            for (Unit u : values()) {
                if (u.name().equalsIgnoreCase(candidate)) return u;
            }
        }

        throw new InvalidInputException("Unknown unit: " + text + "'. Use one of: g, kg, ml, l, tsp, tbsp, cup, piece");
    }
}
