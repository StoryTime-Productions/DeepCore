package dev.deepcore.challenge;

import java.util.Arrays;
import java.util.Optional;

/**
 * Individual challenge mechanic toggles that can be enabled or disabled.
 */
public enum ChallengeComponent {
    /** Keep inventory contents after player death. */
    KEEP_INVENTORY("keep_inventory", "Keep inventory on death"),
    /** Enable hardcore life mode semantics. */
    HARDCORE("hardcore", "Life mode: Hardcore (OFF means unlimited deaths)"),
    /** Allow natural health regeneration. */
    HEALTH_REFILL("health_refill", "Health naturally refills"),
    /** Share inventory state across participants. */
    SHARED_INVENTORY("shared_inventory", "Shared inventory"),
    /** Share health pool across participants. */
    SHARED_HEALTH("shared_health", "Shared health pool"),
    /** Start each life at half a heart. */
    INITIAL_HALF_HEART("initial_half_heart", "Start each life at half a heart"),
    /** Shrink available inventory capacity over time. */
    DEGRADING_INVENTORY("degrading_inventory", "Inventory gradually shrinks");

    private final String key;
    private final String displayName;

    ChallengeComponent(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<ChallengeComponent> fromKey(String key) {
        return Arrays.stream(values())
                .filter(component -> component.key.equalsIgnoreCase(key))
                .findFirst();
    }
}
