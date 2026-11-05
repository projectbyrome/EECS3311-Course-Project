package mealplanner.agent.workflows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mealplanner.agent.llm.LLMClient;
import mealplanner.agent.llm.LLMRequest;
import mealplanner.agent.llm.LLMResponse;
import mealplanner.agent.llm.LLMUnavailableException;
import mealplanner.agent.llm.Message;
import mealplanner.agent.llm.Role;
import mealplanner.agent.llm.TextBlock;
import mealplanner.agent.llm.ToolResult;
import mealplanner.agent.llm.ToolUseBlock;
import mealplanner.agent.tools.ToolManager;
import mealplanner.domain.InvalidInputException;
import mealplanner.domain.MealSlot;
import mealplanner.model.DietGuideReply;

/**
 * Diet Guide (extra feature): a chat that teaches the user about types of diets (Mediterranean, high-protein,
 * fat loss, for athletes...) and what nutrition coaches generally recommend. It is general education, not a
 * personal nutritionist: the system prompt tells Claude not to give personal plans or medical advice.
 * Claude may search the recipe catalogue for example recipes (tool use, same ToolManager as F07).
 * When it explains a diet it ends with a sample day of dishes, which the page turns into "How to cook it" buttons
 * that open Recommendations. The conversation is kept in memory until "New chat".
 */
public class DietGuideChat {

    static final String SYSTEM = """
            You are the Diet Guide inside a meal planning app. You teach people about types of diets and eating \
            approaches, for example the Mediterranean diet, high-protein eating, eating for fat loss, eating for \
            athletes, vegetarian and vegan diets, keto and low-carb, DASH and intermittent fasting.
            For each topic you explain: the main idea, typical foods to eat more and less of, what nutrition \
            coaches and dietitians commonly recommend (general guidelines and typical ranges from public \
            guidance), the benefits and the common drawbacks or mistakes, and who it usually suits.
            Rules:
            - You are an educator, not a personal nutritionist. Do not build a personal meal plan, set personal \
            calorie or macro targets, or diagnose anything. If asked, explain the general approach and suggest \
            a registered dietitian or doctor for personal advice, especially for medical conditions, pregnancy, \
            eating disorders or children.
            - Stay on food, nutrition and diets. Politely decline other topics.
            - Be balanced: mention when evidence is mixed. Never promote extreme restriction.
            - When example meals would help, you may call search_recipe_catalog to find recipes from the app's \
            catalogue and name a few of them.
            - Plain text only: no tables, no headings, no bold, no Markdown.
            - When the question is about a specific diet or way of eating, reply ONLY in this exact format, one \
            item per line, keeping each part short (a sample day lists dish names only: no amounts, no \
            ingredient lists, no explanations):
            DIET: <name of the diet>
            SUMMARY: <one or two sentences>
            EAT PLENTY: <foods, comma separated>
            EAT MODERATELY: <foods, comma separated>
            EAT RARELY: <foods, comma separated>
            COACHES RECOMMEND: <one or two sentences of typical advice>
            SAMPLE DAY
            Breakfast: <dish>
            Lunch: <dish>
            Dinner: <dish>
            Snack: <dish>
            - For any other question, give a one-line summary and then a few bullet points starting with "- ".""";

    static final int MAX_TOKENS = 1200;
    static final int MAX_STEPS = 4;
    /** Older turns are dropped so each request stays small (about 10 questions and answers). */
    static final int MAX_HISTORY_MESSAGES = 20;
    static final String TOOL = "search_recipe_catalog";
    public static final String OFFLINE_REPLY = "The Diet Guide needs Claude, and Claude can't be reached right now. "
            + "Check your internet connection and your ANTHROPIC_API_KEY in .env, then try again.";

    private final LLMClient llm;
    private final ToolManager toolManager;
    private final List<Message> history = new ArrayList<>();

    public DietGuideChat(LLMClient llm, ToolManager toolManager) {
        this.llm = llm;
        this.toolManager = toolManager;
    }

    /**
     * Sends one question with the earlier conversation. Returns Claude's answer, split into the explanation
     * and the sample day of dishes (if there is one), or OFFLINE_REPLY when Claude can't be reached.
     */
    public synchronized DietGuideReply ask(String question) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) {
            throw new InvalidInputException("Type a question first");
        }
        LLMRequest request = new LLMRequest(SYSTEM, MAX_TOKENS);
        request.messages.addAll(history);
        request.addUserText(text);
        request.tools.addAll(toolManager.definitionsFor(List.of(TOOL)));
        String answer;
        try {
            answer = runToolLoop(request);
        } catch (LLMUnavailableException e) {
            return new DietGuideReply(OFFLINE_REPLY, List.of()); // not saved, so it can be asked again
        }
        history.add(new Message(Role.USER, List.of(new TextBlock(text))));
        history.add(new Message(Role.ASSISTANT, List.of(new TextBlock(answer))));
        while (history.size() > MAX_HISTORY_MESSAGES) {
            history.remove(0);
            history.remove(0);
        }
        return parse(answer);
    }

    /**
     * Reads Claude's answer. A diet answer has "KEY: value" lines (DIET, SUMMARY, EAT PLENTY, EAT MODERATELY,
     * EAT RARELY, COACHES RECOMMEND) and a "SAMPLE DAY" with one "Slot: dish" line per meal; anything else is
     * kept as plain text. Lines that don't fit are ignored, so a slightly different format still works.
     */
    static DietGuideReply parse(String answer) {
        Map<String, String> parts = new HashMap<>();
        List<DietGuideReply.SampleDish> day = new ArrayList<>();
        List<String> other = new ArrayList<>();
        boolean inSampleDay = false;
        for (String raw : answer.split("\n")) {
            String line = raw.replace("*", "").replace("#", "").replaceFirst("^[-•\\s]+", "").trim();
            int colon = line.indexOf(':');
            String key = colon > 0 ? line.substring(0, colon).trim().toUpperCase() : line.toUpperCase();
            String value = colon > 0 ? line.substring(colon + 1).trim() : "";
            if (key.equals("SAMPLE DAY") && value.isEmpty()) {
                inSampleDay = true;
            } else if (inSampleDay && colon > 0 && isSlot(key)) {
                if (!value.isEmpty()) day.add(new DietGuideReply.SampleDish(MealSlot.valueOf(key), value));
            } else if (colon > 0 && SECTIONS.contains(key)) {
                parts.put(key, value);
            } else if (!inSampleDay) {
                other.add(raw);
            }
        }
        String text = String.join("\n", other).trim();
        if (!parts.containsKey("DIET")) {
            return new DietGuideReply(text.isEmpty() ? answer.trim() : text, day);
        }
        return new DietGuideReply(text, parts.get("DIET"), parts.get("SUMMARY"), parts.get("EAT PLENTY"),
                parts.get("EAT MODERATELY"), parts.get("EAT RARELY"), parts.get("COACHES RECOMMEND"), day);
    }

    private static final List<String> SECTIONS =
            List.of("DIET", "SUMMARY", "EAT PLENTY", "EAT MODERATELY", "EAT RARELY", "COACHES RECOMMEND");

    private static boolean isSlot(String word) {
        for (MealSlot slot : MealSlot.values()) {
            if (slot.name().equals(word)) return true;
        }
        return false;
    }

    /** Forgets the conversation ("New chat"). */
    public synchronized void clear() {
        history.clear();
    }

    public synchronized int turns() {
        return history.size() / 2;
    }

    private String runToolLoop(LLMRequest request) {
        for (int step = 1; step <= MAX_STEPS; step++) {
            LLMResponse response = llm.send(request);
            if (!response.hasToolUses()) {
                String text = response.text().trim();
                return text.isEmpty() ? "Sorry, I don't have an answer for that. Try asking it another way." : text;
            }
            request.addAssistantMessage(response);
            for (ToolUseBlock call : response.toolUses()) {
                ToolResult result = TOOL.equals(call.name) ? toolManager.execute(call)
                        : ToolResult.error("Tool '" + call.name + "' is not allowed here");
                request.addToolResult(call, result);
            }
        }
        return "Sorry, that took too many steps. Try a shorter question.";
    }
}