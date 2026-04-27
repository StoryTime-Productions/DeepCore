package dev.deepcore.challenge.training;

import java.util.Arrays;
import java.util.Optional;

/**
 * Supported training mini-challenge types.
 */
public enum TrainingChallengeType {
    PORTAL("portal", "Portal"),
    CRAFT("craft", "Craft"),
    CHEST("chest", "Chest"),
    BRIDGE("bridge", "Bridge");

    private final String key;
    private final String displayName;

    TrainingChallengeType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * Returns the stable key used in config and command args.
     *
     * @return lowercase challenge key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the user-facing challenge display name.
     *
     * @return display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Parses a challenge type from a configured or typed key.
     *
     * @param key key to resolve
     * @return matching challenge type when found
     */
    public static Optional<TrainingChallengeType> fromKey(String key) {
        return Arrays.stream(values())
                .filter(value -> value.key.equalsIgnoreCase(key))
                .findFirst();
    }
}
