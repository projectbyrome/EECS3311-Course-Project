package mealplanner.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mealplanner.data.IngredientRepository;
import mealplanner.data.ProfileRepository;
import mealplanner.domain.CategoryGroup;
import mealplanner.domain.Ingredient;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.UserProfile;
import mealplanner.domain.ValidationReport;
import mealplanner.events.EventBus;
import mealplanner.events.ProfileChanged;

/**
 * F06 (UC06): validates and saves the profile, then publishes ProfileChanged so recipe warnings are
 * re-checked. Also keeps the daily nutrition goal (updateGoal is used by F10).
 */
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final IngredientRepository ingredientRepository;
    private final EventBus eventBus;

    public ProfileService(ProfileRepository profileRepository, IngredientRepository ingredientRepository,
                          EventBus eventBus) {
        this.profileRepository = profileRepository;
        this.ingredientRepository = ingredientRepository;
        this.eventBus = eventBus;
    }

    /** The saved profile, or an empty one with the default goal if nothing was saved yet. */
    public UserProfile getProfile() {
        return profileRepository.load().orElseGet(UserProfile::new);
    }

    /**
     * UC06 steps 3-5. Allergy names must be an allergen tag ("peanut") or a known ingredient ("banana");
     * an unknown name gets close suggestions (3a). If anything is wrong, nothing is saved.
     */
    public ValidationReport update(UserProfile profile) {
        ValidationReport report = new ValidationReport();
        if (profile == null) {
            report.add("No profile given");
            return report;
        }
        Set<String> known = knownAllergyNames();
        Set<String> allergies = new TreeSet<>();
        for (String raw : profile.getAllergies()) {
            String a = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (a.isEmpty()) {
                report.add("An allergy is empty");
            } else if (!known.contains(a)) {
                List<String> close = suggestions(a, known);
                report.add("Unknown allergy '" + raw.trim() + "'"
                        + (close.isEmpty() ? "" : ". Did you mean: " + String.join(", ", close) + "?"));
            } else {
                allergies.add(a);
            }
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String raw : profile.getFavouriteCategories()) {
            if (raw == null || raw.isBlank()) {
                report.add("A favourite category is empty");
            } else if (raw.contains(":") || raw.contains("\n")) {
                report.add("A category name cannot contain ':' or a line break: '" + raw.trim() + "'");
            } else {
                categories.add(categoryName(raw.trim()));
            }
        }
        if (!report.isValid()) {
            return report;
        }
        UserProfile clean = new UserProfile(profile.getRestrictions(), allergies, categories,
                profile.getDefaultGoal() == null ? getProfile().getDefaultGoal() : profile.getDefaultGoal());
        profileRepository.save(clean);
        eventBus.publish(new ProfileChanged());
        return report;
    }

    /** F10 (UC10 5a): every value must be greater than 0. Valid goals are saved in the profile. */
    public ValidationReport updateGoal(NutritionGoal goal) {
        ValidationReport report = new ValidationReport();
        if (goal == null) {
            report.add("No goal given");
            return report;
        }
        if (!(goal.getCalories() > 0)) report.add("Calories must be greater than 0");
        if (!(goal.getProteinG() > 0)) report.add("Protein must be greater than 0");
        if (!(goal.getCarbsG() > 0)) report.add("Carbohydrates must be greater than 0");
        if (!(goal.getFatG() > 0)) report.add("Fat must be greater than 0");
        if (report.isValid()) {
            UserProfile profile = getProfile();
            profile.setDefaultGoal(goal);
            profileRepository.save(profile);
            eventBus.publish(new ProfileChanged());
        }
        return report;
    }

    /** Every allergen tag and every ingredient name, lower case. */
    public Set<String> knownAllergyNames() {
        Set<String> names = new TreeSet<>();
        for (Ingredient i : ingredientRepository.search("")) {
            names.add(i.getName().toLowerCase(Locale.ROOT));
            i.getAllergenTags().forEach(t -> names.add(t.toLowerCase(Locale.ROOT)));
        }
        return names;
    }

    /** Known names that contain the typed text without a plural "s" (or the other way round), or are at most 2 edits away. */
    static List<String> suggestions(String typed, Set<String> known) {
        List<String> close = new ArrayList<>();
        String stem = typed.endsWith("s") ? typed.substring(0, typed.length() - 1) : typed; // "peanuts" -> "peanut"
        for (String k : known) {
            boolean contains = stem.length() >= 3 && (k.contains(stem) || stem.contains(k));
            if (contains || editDistance(typed, k) <= 2) {
                close.add(k);
            }
        }
        return close.size() > 5 ? close.subList(0, 5) : close;
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[b.length()];
    }

    /**
     * A suggested category keeps its usual spelling ("pan-fried" becomes "Pan-Fried"), in whichever group
     * it belongs to. Anything else is the user's own category and is kept as typed.
     */
    static String categoryName(String typed) {
        for (CategoryGroup group : CategoryGroup.values()) {
            Optional<String> known = group.find(typed);
            if (known.isPresent()) {
                return known.get();
            }
        }
        return typed;
    }
}