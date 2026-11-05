import java.io.*; import java.nio.file.*; import java.util.*;
import mealplanner.main.*; import mealplanner.cli.*; import mealplanner.app.*; import mealplanner.domain.*; import mealplanner.model.*; import mealplanner.agent.llm.*;

/** New ingredients (e.g. ketchup) can be added to the ingredient list and then to the pantry. */
public class IngredientAddCheck {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "ok   " : "FAIL ") + what); if (!ok) fails++; }
    static boolean rejects(Runnable r, String text) { try { r.run(); return false; } catch (InvalidInputException e) { return e.getMessage().contains(text); } }
    public static void main(String[] a) throws Exception {
        LLMClient offline = r -> { throw new LLMUnavailableException("offline"); };
        AppConfig c = new AppConfig(); c.dbPath = Files.createTempFile("ing", ".db").toString();
        AppContext ctx = AppContext.create(c, UiMode.CLI, offline, null);
        AppFacade f = ctx.facade();

        System.out.println("== Facade ==");
        check(!f.isKnownIngredient("ketchup"), "ketchup is not known at first");
        check(f.isKnownIngredient("onion") && f.isKnownIngredient("  Onion "), "onion is known (any case, spaces trimmed)");
        check(rejects(() -> f.addPantryItem("ketchup", new Quantity(1, Unit.PIECE)), ""), "the pantry still refuses an unknown name");
        Ingredient k = f.addIngredient("  Ketchup ");
        check(k.getName().equals("ketchup") && f.isKnownIngredient("ketchup"), "addIngredient saves 'ketchup' (trimmed, lower case)");
        check(f.addIngredient("KETCHUP").getId() == k.getId(), "adding it again returns the same ingredient");
        check(f.addIngredient("onion").getName().equals("onion"), "an existing ingredient is returned, not duplicated");
        check(rejects(() -> f.addIngredient("   "), "Type an ingredient name"), "a blank name is refused");
        PantryItem item = f.addPantryItem("ketchup", new Quantity(500, Unit.ML));
        check(item.getIngredient().getName().equals("ketchup"), "ketchup can now go in the pantry");
        AppContext again = AppContext.create(c, UiMode.CLI, offline, null);
        check(again.facade().isKnownIngredient("ketchup"), "the new ingredient is saved in SQLite (still there after a restart)");

        System.out.println("== CLI ==");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ctx.cliApp(new Scanner("n\n"), new PrintStream(buf, true)).run("pantry add mustard 1 piece".split(" "));
        String out = buf.toString();
        check(out.contains("isn't a known ingredient yet") && out.contains("Nothing was added.") && !f.isKnownIngredient("mustard"), "answer n: nothing added");
        buf.reset();
        ctx.cliApp(new Scanner("y\n"), new PrintStream(buf, true)).run("pantry add mustard 1 piece".split(" "));
        out = buf.toString();
        check(f.isKnownIngredient("mustard") && out.contains("mustard: 1 PIECE in pantry"), "answer y: mustard added to the ingredients and the pantry: " + out.trim());
        buf.reset();
        ctx.cliApp(new Scanner("y\n"), new PrintStream(buf, true)).run("pantry staple soy sauce have".split(" "));
        out = buf.toString();
        check(out.contains("soy sauce is a staple: have."), "answer y: a new staple works too: " + out.trim());
        buf.reset();
        ctx.cliApp(new Scanner(""), new PrintStream(buf, true)).run("pantry add onion 2 piece".split(" "));
        check(!buf.toString().contains("isn't a known"), "a known ingredient is not asked about");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
    }
}