package mealplanner.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import mealplanner.agent.llm.ToolResult;
import mealplanner.domain.DietaryRestriction;
import mealplanner.domain.UserProfile;
import mealplanner.service.ProfileService;

/** get_profile: the user's restrictions, allergies, favourite categories and daily goal. */
public class GetProfileTool extends AbstractTool {

    private final ProfileService profileService;

    public GetProfileTool(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "get_profile";
    }

    @Override
    public String description() {
        return "Returns the user's dietary restrictions and allergies (strict rules), favourite recipe categories "
                + "(preferences) and daily nutrition goal.";
    }

    @Override
    public JsonNode inputSchema() {
        return schema("{\"type\": \"object\", \"properties\": {}}");
    }

    @Override
    protected ToolResult doExecute(JsonNode args) {
        UserProfile p = profileService.getProfile();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("restrictions", p.getRestrictions().stream().map(DietaryRestriction::label).sorted().toList());
        out.put("allergies", p.getAllergies().stream().sorted().toList());
        out.put("favouriteCategories", p.getFavouriteCategories().stream().sorted().toList());
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("calories", p.getDefaultGoal().getCalories());
        goal.put("proteinG", p.getDefaultGoal().getProteinG());
        goal.put("carbsG", p.getDefaultGoal().getCarbsG());
        goal.put("fatG", p.getDefaultGoal().getFatG());
        out.put("dailyGoal", goal);
        return ToolResult.ok(toJson(out));
    }
}