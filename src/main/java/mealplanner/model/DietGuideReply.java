package mealplanner.model;

import java.util.List;
import mealplanner.domain.MealSlot;

/**
 * One Diet Guide answer. A diet answer has a title, a summary, what to eat plenty of / moderately / rarely,
 * what coaches usually recommend and a sample day of dishes; any other answer only has text.
 */
public class DietGuideReply {

    /** One dish of the sample day, e.g. LUNCH "Lentil soup with lemon". */
    public static class SampleDish {
        public final MealSlot slot;
        public final String dish;

        public SampleDish(MealSlot slot, String dish) {
            this.slot = slot;
            this.dish = dish;
        }
    }

    /** Plain text: the whole answer for an ordinary question, or any extra lines around a diet answer. */
    public final String text;
    /** The diet's name, e.g. "Mediterranean diet"; null when the answer isn't about one diet. */
    public final String diet;
    public final String summary;
    public final String eatPlenty;
    public final String eatModerately;
    public final String eatRarely;
    public final String coachesRecommend;
    public final List<SampleDish> sampleDay;

    /** An ordinary answer (no diet sections). */
    public DietGuideReply(String text, List<SampleDish> sampleDay) {
        this(text, null, null, null, null, null, null, sampleDay);
    }

    public DietGuideReply(String text, String diet, String summary, String eatPlenty, String eatModerately,
                          String eatRarely, String coachesRecommend, List<SampleDish> sampleDay) {
        this.text = text == null ? "" : text;
        this.diet = diet;
        this.summary = summary;
        this.eatPlenty = eatPlenty;
        this.eatModerately = eatModerately;
        this.eatRarely = eatRarely;
        this.coachesRecommend = coachesRecommend;
        this.sampleDay = List.copyOf(sampleDay);
    }

    public boolean isDietCard() {
        return diet != null && !diet.isBlank();
    }

    public boolean hasSampleDay() {
        return !sampleDay.isEmpty();
    }

    /** The answer as plain text (used by the CLI). */
    public String asText() {
        if (!isDietCard()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(diet.toUpperCase());
        add(sb, "", summary);
        add(sb, "Eat plenty of: ", eatPlenty);
        add(sb, "Eat moderately: ", eatModerately);
        add(sb, "Eat rarely: ", eatRarely);
        add(sb, "What coaches usually recommend: ", coachesRecommend);
        add(sb, "", text);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("\n").append(label).append(value);
        }
    }

    /** The wish sent to Recommendations for "How to cook it", so Claude finds the dish or writes its recipe. */
    public static String cookRequest(String dish) {
        return "How do I cook \"" + dish + "\"? Write the recipe if the catalogue doesn't have it.";
    }
}