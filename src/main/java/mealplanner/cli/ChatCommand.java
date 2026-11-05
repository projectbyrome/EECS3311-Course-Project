package mealplanner.cli;

import java.io.PrintStream;
import java.util.Scanner;
import mealplanner.app.AppFacade;
import mealplanner.model.DietGuideReply;

/**
 * Diet Guide from the command line: "chat <question>" asks one question;
 * "chat" alone starts a conversation that ends with an empty line or "exit".
 */
public class ChatCommand extends AbstractCliCommand {

    public ChatCommand(AppFacade facade, Scanner in, PrintStream out) {
        super(facade, in, out);
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public String usage() {
        return """
                chat <question>        e.g. chat what is the Mediterranean diet
                chat                   talk until you type exit
                chat new               forget the conversation""";
    }

    @Override
    public CommandResult execute(CliArgs args) {
        if (args.subcommand.equals("new") && args.positional.isEmpty()) {
            facade.startNewDietChat();
            return CommandResult.ok("Started a new Diet Guide chat.");
        }
        String question = (args.subcommand + " " + String.join(" ", args.positional)).trim();
        if (!question.isEmpty()) {
            return CommandResult.ok(show(facade.askDietGuide(question)));
        }
        out.println("Diet Guide: ask about diets (general information, not personal advice). Type exit to stop.");
        while (true) {
            out.print("you> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty() || line.equalsIgnoreCase("exit")) break;
            out.println(show(facade.askDietGuide(line)));
            out.println();
        }
        return CommandResult.ok("Chat ended.");
    }

    /** The answer, then the sample day with the command that finds or writes each recipe. */
    static String show(DietGuideReply reply) {
        StringBuilder sb = new StringBuilder(reply.asText());
        if (reply.hasSampleDay()) {
            sb.append("\n\nA day on this diet:");
            for (DietGuideReply.SampleDish d : reply.sampleDay) {
                String slot = d.slot.name().charAt(0) + d.slot.name().substring(1).toLowerCase();
                sb.append("\n  ").append(slot).append(": ").append(d.dish);
            }
            DietGuideReply.SampleDish first = reply.sampleDay.get(0);
            sb.append("\nTo cook one: recommend any --ask ").append(DietGuideReply.cookRequest(first.dish));
        }
        return sb.toString();
    }
}