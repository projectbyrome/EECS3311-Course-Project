import java.io.*; import java.nio.file.*; import java.time.*; import java.util.*;
import mealplanner.main.*; import mealplanner.cli.*; import mealplanner.app.*; import mealplanner.domain.*; import mealplanner.model.*; import mealplanner.agent.llm.*;

/** Pantry staples (have / running low) and the Shopping page status summary. Fake Claude, no keys needed. */
public class StapleCheck {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "ok   " : "FAIL ") + what); if (!ok) fails++; }
    static boolean rejects(Runnable r, String text) { try { r.run(); return false; } catch (InvalidInputException e) { return e.getMessage().contains(text); } }
    public static void main(String[] a) throws Exception {
        LLMClient offline = r -> { throw new LLMUnavailableException("offline"); };
        AppConfig c = new AppConfig(); c.dbPath = Files.createTempFile("st", ".db").toString();
        AppContext ctx = AppContext.create(c, UiMode.CLI, offline, null);
        AppFacade f = ctx.facade();
        Map<String, Long> ids = new HashMap<>(); f.searchLibrary("").forEach(r -> ids.put(r.getName(), r.getId()));

        System.out.println("== Staples ==");
        PantryItem salt = f.addStaple("salt", StapleStatus.HAVE);
        PantryItem chili = f.addStaple("chili powder", StapleStatus.RUNNING_LOW);
        check(salt.isStaple() && salt.getStapleStatus() == StapleStatus.HAVE, "salt added as a staple (have)");
        PantryItem back = f.getPantry().stream().filter(p -> p.getId() == chili.getId()).findFirst().get();
        check(back.isStaple() && back.getStapleStatus() == StapleStatus.RUNNING_LOW, "staple status saved in SQLite and read back");
        check(rejects(() -> f.addPantryItem("salt", new Quantity(100, Unit.G)), "is a staple"), "adding an amount to a staple is refused");
        check(rejects(() -> f.updatePantryItem(salt.getId(), new Quantity(5, Unit.G)), "is a staple"), "setting a staple's amount is refused");
        PantryItem eggs = f.addPantryItem("eggs", new Quantity(15, Unit.PIECE));
        check(rejects(() -> f.addStaple("eggs", StapleStatus.HAVE), "tracked by amount"), "a tracked ingredient can't silently become a staple");
        check(rejects(() -> f.setStapleStatus(eggs.getId(), StapleStatus.HAVE), "not a staple"), "staple status only on staples");
        check(!mealplanner.service.PantryService.isOutOfStock(salt), "a staple is never 'out of stock'");
        f.setStapleStatus(salt.getId(), StapleStatus.RUNNING_LOW); f.addStaple("salt", StapleStatus.HAVE);
        check(f.getPantry().stream().filter(p -> p.getId() == salt.getId()).findFirst().get().getStapleStatus() == StapleStatus.HAVE, "status can be changed both ways");

        System.out.println("== Missing for a planned meal ==");
        f.addPantryItem("ground beef", new Quantity(200, Unit.G));      // not enough (needs 500 g)
        f.addPantryItem("kidney beans", new Quantity(400, Unit.G));     // enough
        PantryItem onion = f.addPantryItem("onion", new Quantity(1, Unit.PIECE));
        f.updatePantryItem(onion.getId(), new Quantity(0, Unit.PIECE)); // out of stock
        // canned tomatoes: not in the pantry; chili powder: staple, running low
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DayPlan day = f.addMealToDay(tomorrow, MealSlot.DINNER, ids.get("Chili"), 4);
        MealEntry chiliMeal = day.activeEntries().get(0);
        PantryStatusSummary s = f.pantryStatusSummary();
        check(s.missing.equals(List.of("canned tomatoes", "ground beef", "onion")), "Missing = not in pantry, not enough, out of stock: " + s.missing);
        check(s.runningLow.equals(List.of("chili powder")), "Running low = staples marked running low: " + s.runningLow);
        check(!s.missing.contains("chili powder") && !s.missing.contains("kidney beans") && !s.missing.contains("salt"), "staples and enough-stock items are not missing");
        check(chiliMeal.isMissingIngredients(), "the calendar still flags the meal (other ingredients missing)");
        f.addPantryItem("ground beef", new Quantity(300, Unit.G)); f.updatePantryItem(onion.getId(), new Quantity(1, Unit.PIECE));
        f.addPantryItem("canned tomatoes", new Quantity(400, Unit.G));
        boolean flagged = f.getDays(tomorrow, 1).get(0).activeEntries().get(0).isMissingIngredients();
        check(!flagged, "with only a running-low staple left, the meal is no longer flagged (staple counts as available)");
        check(f.pantryStatusSummary().missing.isEmpty() && f.pantryStatusSummary().runningLow.equals(List.of("chili powder")), "summary: nothing missing, chili powder still listed as running low");
        f.updatePantryItem(eggs.getId(), new Quantity(0, Unit.PIECE));
        check(f.pantryStatusSummary().missing.equals(List.of("eggs")), "an out-of-stock ingredient is missing even without a planned meal");

        System.out.println("== Shopping ==");
        Checklist list = f.createChecklist("Weekly");
        f.addChecklistItem(list.getId(), "chili powder", new Quantity(1, Unit.PIECE));
        f.addChecklistItem(list.getId(), "milk", new Quantity(1, Unit.L));
        int before = f.getChecklists().get(0).getItems().size();
        f.pantryStatusSummary();
        check(f.getChecklists().get(0).getItems().size() == before, "the summary does not change the shopping list");
        ChecklistItem chiliItem = f.getChecklists().get(0).getItems().stream().filter(i -> i.getName().equals("chili powder")).findFirst().get();
        check(chiliItem.isAlreadyInPantry() && chiliItem.getPantryQuantity() == null, "a staple on a checklist is flagged 'already in pantry' with no amount");
        f.setItemChecked(chiliItem.getId(), true);
        f.finishShopping(list.getId());
        check(f.getPantry().stream().filter(p -> p.getId() == chili.getId()).findFirst().get().getStapleStatus() == StapleStatus.HAVE, "buying a staple sets it back to have");
        ShoppingDraft draft = f.draftShoppingList(List.of(new RecipeSelection(ids.get("Chili"), 8)));
        check(!draft.items.isEmpty() && draft.items.stream().noneMatch(i -> i.name.equals("chili powder")), "a generated shopping list skips staples: " + draft.items.stream().map(i -> i.name).toList());

        System.out.println("== CLI ==");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CliApp cli = ctx.cliApp(new Scanner(""), new PrintStream(buf, true));
        cli.run("pantry staple olive oil low".split(" "));
        cli.run("pantry list".split(" "));
        cli.run("shopping status".split(" "));
        String out = buf.toString();
        check(out.contains("olive oil is a staple: running low.") && out.contains("olive oil: staple, running low") && out.contains("salt: staple, have"), "pantry staple + list");
        check(out.contains("Missing (1): eggs") && out.contains("Running low (1): olive oil"), "shopping status");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
    }
}