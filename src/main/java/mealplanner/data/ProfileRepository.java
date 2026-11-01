package mealplanner.data;

import java.util.Optional;
import mealplanner.domain.UserProfile;

/** Stores the one user profile (F06) with its daily goal (F10). */
public interface ProfileRepository {

    Optional<UserProfile> load();

    void save(UserProfile profile);
}