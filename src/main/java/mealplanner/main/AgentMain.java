package mealplanner.main;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import mealplanner.agent.workflows.DietGuideChat;
import mealplanner.app.AppFacade;
import mealplanner.cli.ChatCommand;
import mealplanner.cli.CliApp;
import mealplanner.cli.RecommendCommand;
import mealplanner.domain.AppException;
import mealplanner.domain.NutritionGoal;
import mealplanner.domain.Recipe;
import mealplanner.model.DietGuideReply;
import mealplanner.model.Recommendation;
import mealplanner.model.RecommendationFilter;
import mealplanner.model.RecommendationResult;

/**
 * Standalone agent runner (Agent.jar). Reads a task from a text file and runs the agent on it, with no window:
 *
 *   java -jar Agent.jar -LLMAPI claude -input task.txt
 *
 * The task file is plain text. Optional "key: value" lines at the top (agent, category, minutes, goal) set the
 * filters; the rest is the task in your own words. A file of CLI commands (one per line) is also accepted.
 * The API key is never an option: it comes from ANTHROPIC_API_KEY (environment or the local .env file).
 */
public class AgentMain {

    static final String USAGE = """
            Usage: java -jar Agent.jar -LLMAPI <claude|none> -input <task file> [options]

            Options:
              -LLMAPI <claude|none>   LLM to use. claude (default) calls the Anthropic API with the key in
                                      ANTHROPIC_API_KEY. none runs offline with the app's fallbacks.
              -input <file>           Task file to run (required).
              -agent <name>           auto (default), recommend, guide or commands.
                                        recommend = recipe recommendation agent (F07)
                                        guide     = AI Diet Guide (F11)
                                        commands  = run each line of the file as a CLI command
              -model <id>             Claude model id (default: MEALPLANNER_MODEL or the app default).
              -db <file>              Database file (default: MEALPLANNER_DB or ~/.mealplanner/mealplanner.db).
              -approve <n>            recommend only: add recommendation n to the recipe library.
              -output <file>          Also write the result to this file.
              -help                   Show this help.

            Task file: optional header lines, then the task in plain words, e.g.
              agent: recommend
              category: Curry
              minutes: 30
              goal: high-protein
              Something mild I can make with chicken and rice.
            """;

    /** First words of the CLI commands; a task file made only of these lines is run as commands. */
    static final Set<String> CLI_VERBS = Set.of("calendar", "checklist", "shopping", "pantry", "macros", "profile",
            "recommend", "recipe", "fav", "summary", "chat", "help");

    /** Words that make "auto" pick the recipe agent instead of the Diet Guide. */
    static final List<String> RECIPE_WORDS = List.of("recipe", "cook", "dinner", "lunch", "breakfast", "snack",
            "meal", "dish", "recommend", "suggest", "minutes");

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs the agent and returns the exit code: 0 done, 1 the task failed, 2 wrong options or file. */
    static int run(String[] args) {
        Options options;
        TaskFile task;
        try {
            options = Options.parse(args);
            if (options.help) {
                System.out.println(USAGE);
                return 0;
            }
            task = TaskFile.read(options.input);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println(USAGE);
            return 2;
        }

        AppConfig config = AppConfig.fromEnvironment();
        if (options.model != null) config.model = options.model;
        if (options.db != null) config.dbPath = options.db;
        if (options.llm.equals("none")) {
            config.anthropicApiKey = null; // every Claude call falls back, as in the app without a key
        } else if (config.anthropicApiKey == null) {
            System.err.println("Error: -LLMAPI claude needs an API key. Set ANTHROPIC_API_KEY (or put it in .env), "
                    + "or use -LLMAPI none to run offline.");
            return 2;
        }

        String agent = options.agent != null ? options.agent : task.header("agent", "auto");
        if (agent.equals("auto")) agent = task.guessAgent();

        PrintStream originalOut = System.out;
        try (OutputStream file = options.output == null ? null : new FileOutputStream(options.output.toFile())) {
            if (file != null) System.setOut(new PrintStream(new Tee(originalOut, file), true, StandardCharsets.UTF_8));
            System.out.println("Agent: " + agent + " | LLM: " + options.llm
                    + (options.llm.equals("claude") ? " (" + config.model + ")" : "") + " | task: " + options.input);
            System.out.println();
            AppContext context = AppContext.create(config, UiMode.CLI);
            return switch (agent) {
                case "recommend" -> recommend(context.facade(), task, options);
                case "guide" -> guide(context.facade(), task);
                case "commands" -> commands(context, task);
                default -> {
                    System.err.println("Error: unknown agent '" + agent + "'. Use auto, recommend, guide or commands.");
                    yield 2;
                }
            };
        } catch (AppException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("Error: cannot write " + options.output + ": " + e.getMessage());
            return 2;
        } finally {
            System.out.flush();
            System.setOut(originalOut);
        }
    }

    /** F07: the recommendation agent (tool-use loop with the profile, library and catalogue tools). */
    static int recommend(AppFacade facade, TaskFile task, Options options) {
        RecommendationFilter filter = new RecommendationFilter();
        String category = task.header("category", null);
        if (category != null && !category.equalsIgnoreCase("any")) filter.category = category;
        String minutes = task.header("minutes", null);
        if (minutes != null) {
            try {
                filter.maxCookMinutes = Integer.parseInt(minutes);
            } catch (NumberFormatException e) {
                System.err.println("Error: minutes must be a whole number, not '" + minutes + "'");
                return 2;
            }
        }
        String goal = task.header("goal", null);
        if (goal != null) {
            filter.goal = goal.equalsIgnoreCase("profile") ? facade.getProfile().getDefaultGoal() : NutritionGoal.preset(goal);
        }
        if (!task.body.isBlank()) filter.request = task.body;

        System.out.println("Task: " + filter.describe());
        System.out.println();
        RecommendationResult result = facade.recommendRecipes(filter);
        if (result.message != null) System.out.println(result.message);
        if (result.isEmpty()) return 1;
        List<Recommendation> cards = result.recommendations;
        for (int i = 0; i < cards.size(); i++) {
            System.out.println(RecommendCommand.card(i + 1, cards.get(i)));
            System.out.println();
        }
        if (options.approve != null) {
            if (options.approve < 1 || options.approve > cards.size()) {
                System.err.println("Error: -approve must be from 1 to " + cards.size());
                return 1;
            }
            Recommendation chosen = cards.get(options.approve - 1);
            Recipe added = chosen.written ? facade.approveWrittenRecipe(chosen.recipe)
                    : facade.approveRecommendation(chosen.recipe.getCatalogId());
            System.out.println("Added to your recipe library: " + added.getName() + " (#" + added.getId() + ").");
        } else {
            System.out.println("To add one to your library, run again with -approve <number>.");
        }
        return 0;
    }

    /** F11: the AI Diet Guide answers the task as one question. */
    static int guide(AppFacade facade, TaskFile task) {
        if (task.body.isBlank()) {
            System.err.println("Error: the task file has no question for the Diet Guide.");
            return 2;
        }
        System.out.println("Question: " + task.body);
        System.out.println();
        DietGuideReply reply = facade.askDietGuide(task.body);
        System.out.println(ChatCommand.show(reply));
        return DietGuideChat.OFFLINE_REPLY.equals(reply.text) ? 1 : 0;
    }

    /** Runs each line as a CLI command (see "help"), in order. Questions such as "Add one?" get no answer. */
    static int commands(AppContext context, TaskFile task) {
        CliApp cli = context.cliApp(new Scanner(""), System.out);
        for (String line : task.lines) {
            System.out.println("> " + line);
            cli.run(line.trim().split("\\s+"));
            System.out.println();
        }
        return 0;
    }

    /** Command-line options. Accepts -name and --name. */
    static class Options {
        String llm = "claude";
        Path input;
        String agent;
        String model;
        String db;
        Integer approve;
        Path output;
        boolean help;

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String name = args[i].replaceFirst("^--?", "").toLowerCase(Locale.ROOT);
                boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("-");
                switch (name) {
                    case "help", "h", "?" -> o.help = true;
                    case "llmapi" -> {
                        // "-LLMAPI" alone means Claude
                        String value = hasValue ? args[++i].toLowerCase(Locale.ROOT) : "claude";
                        if (value.equals("offline")) value = "none";
                        if (!value.equals("claude") && !value.equals("anthropic") && !value.equals("none")) {
                            throw new IllegalArgumentException("-LLMAPI must be claude or none, not '" + value + "'");
                        }
                        o.llm = value.equals("anthropic") ? "claude" : value;
                    }
                    case "input" -> o.input = Path.of(value(args, ++i, "-input"));
                    case "agent" -> {
                        o.agent = value(args, ++i, "-agent").toLowerCase(Locale.ROOT);
                        if (!Set.of("auto", "recommend", "guide", "commands").contains(o.agent)) {
                            throw new IllegalArgumentException("-agent must be auto, recommend, guide or commands");
                        }
                    }
                    case "model" -> o.model = value(args, ++i, "-model");
                    case "db" -> o.db = value(args, ++i, "-db");
                    case "output" -> o.output = Path.of(value(args, ++i, "-output"));
                    case "approve" -> {
                        String v = value(args, ++i, "-approve");
                        try {
                            o.approve = Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("-approve needs a number, not '" + v + "'");
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown option '" + args[i] + "'");
                }
            }
            if (!o.help && o.input == null) {
                throw new IllegalArgumentException("-input <task file> is required");
            }
            return o;
        }

        private static String value(String[] args, int i, String option) {
            if (i >= args.length || args[i].startsWith("-")) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return args[i];
        }
    }

    /** The task file: "key: value" header lines at the top, then the task text. Lines starting with # are skipped. */
    static class TaskFile {
        static final Set<String> HEADER_KEYS = Set.of("agent", "category", "minutes", "goal");

        final java.util.Map<String, String> headers = new java.util.HashMap<>();
        final List<String> lines = new ArrayList<>(); // task lines, without headers and comments
        String body = "";

        static TaskFile read(Path file) throws IOException {
            if (!Files.isRegularFile(file)) {
                throw new IOException("Task file not found: " + file);
            }
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        }

        static TaskFile parse(List<String> raw) {
            TaskFile t = new TaskFile();
            boolean inHeader = true;
            for (String line : raw) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int colon = s.indexOf(':');
                if (inHeader && colon > 0) {
                    String key = s.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                    if (HEADER_KEYS.contains(key)) {
                        t.headers.put(key, s.substring(colon + 1).strip());
                        continue;
                    }
                }
                inHeader = false;
                t.lines.add(s);
            }
            t.body = String.join(" ", t.lines).strip();
            if (t.lines.isEmpty() && t.headers.isEmpty()) {
                throw new IllegalArgumentException("The task file is empty");
            }
            return t;
        }

        String header(String key, String fallback) {
            String v = headers.get(key);
            if (v == null || v.isBlank()) return fallback;
            return key.equals("agent") ? v.toLowerCase(Locale.ROOT) : v;
        }

        /** commands if every line is a CLI command; recommend if it talks about cooking or meals; else guide. */
        String guessAgent() {
            if (!lines.isEmpty() && lines.stream().allMatch(l ->
                    CLI_VERBS.contains(l.split("\\s+")[0].toLowerCase(Locale.ROOT)))) {
                return "commands";
            }
            if (body.isBlank()) return "recommend"; // only filters, e.g. category: Curry
            String lower = body.toLowerCase(Locale.ROOT);
            return RECIPE_WORDS.stream().anyMatch(lower::contains) ? "recommend" : "guide";
        }
    }

    /** Writes to the screen and the -output file at the same time. */
    static class Tee extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        Tee(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }
    }
}