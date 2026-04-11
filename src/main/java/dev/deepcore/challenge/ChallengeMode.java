package dev.deepcore.challenge;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Preset challenge configurations that map to default component toggles.
 */
public enum ChallengeMode {
    /** Keep inventory with non-hardcore deaths allowed. */
    KEEP_INVENTORY_UNLIMITED_DEATHS(
            "keep_inventory_non_hardcore",
            "Keep inventory, non-hardcore",
            EnumSet.of(ChallengeComponent.KEEP_INVENTORY, ChallengeComponent.HEALTH_REFILL)),
    /** Lose inventory with non-hardcore deaths allowed. */
    LOSE_INVENTORY_UNLIMITED_DEATHS(
            "lose_inventory_non_hardcore",
            "Lose inventory, non-hardcore",
            EnumSet.of(ChallengeComponent.HEALTH_REFILL)),
    /** Lose inventory and share inventory in non-hardcore mode. */
    LOSE_INVENTORY_UNLIMITED_DEATHS_SHARED_INVENTORY(
            "lose_inventory_non_hardcore_shared_inventory",
            "Lose inventory, non-hardcore, shared inventory",
            EnumSet.of(ChallengeComponent.HEALTH_REFILL, ChallengeComponent.SHARED_INVENTORY)),
    /** Hardcore mode with natural health refill enabled. */
    HARDCORE_HEALTH_REFILL(
            "hardcore_health_refill",
            "Hardcore, health can refill",
            EnumSet.of(ChallengeComponent.HARDCORE, ChallengeComponent.HEALTH_REFILL)),
    /** Hardcore mode with no natural health refill. */
    HARDCORE_NO_REFILL(
            "hardcore_no_refill", "Hardcore, health does not refill", EnumSet.of(ChallengeComponent.HARDCORE)),
    /** Hardcore mode with no refill and shared inventory. */
    HARDCORE_NO_REFILL_SHARED_INVENTORY(
            "hardcore_no_refill_shared_inventory",
            "Hardcore, health does not refill, shared inventory",
            EnumSet.of(ChallengeComponent.HARDCORE, ChallengeComponent.SHARED_INVENTORY)),
    /** Hardcore mode with shared health and health refill. */
    HARDCORE_SHARED_HEALTH(
            "hardcore_shared_health",
            "Hardcore, everyone shares the same health",
            EnumSet.of(
                    ChallengeComponent.HARDCORE, ChallengeComponent.HEALTH_REFILL, ChallengeComponent.SHARED_HEALTH)),
    /** Hardcore mode with shared health and no refill. */
    HARDCORE_SHARED_HEALTH_NO_REFILL(
            "hardcore_shared_health_no_refill",
            "Hardcore, everyone shares the same health and does not refill",
            EnumSet.of(ChallengeComponent.HARDCORE, ChallengeComponent.SHARED_HEALTH)),
    /** Hardcore shared health/no-refill plus shared inventory. */
    HARDCORE_SHARED_HEALTH_NO_REFILL_SHARED_INVENTORY(
            "hardcore_shared_health_no_refill_shared_inventory",
            "Hardcore, shared health, no refill, and shared inventory",
            EnumSet.of(
                    ChallengeComponent.HARDCORE,
                    ChallengeComponent.SHARED_HEALTH,
                    ChallengeComponent.SHARED_INVENTORY)),
    /** Hardcore shared health/no-refill plus degrading inventory. */
    HARDCORE_SHARED_HEALTH_NO_REFILL_DEGRADING_INVENTORY(
            "hardcore_shared_health_no_refill_degrading_inventory",
            "Hardcore, shared health, no refill, and degrading inventory",
            EnumSet.of(
                    ChallengeComponent.HARDCORE,
                    ChallengeComponent.SHARED_HEALTH,
                    ChallengeComponent.DEGRADING_INVENTORY)),
    /**
     * Hardcore shared health/no-refill with degrading inventory and half-heart
     * start.
     */
    HARDCORE_SHARED_HEALTH_NO_REFILL_DEGRADING_INVENTORY_INITIAL_HALF_HEART(
            "hardcore_shared_health_no_refill_degrading_inventory_initial_half_heart",
            "Hardcore, shared health, no refill, degrading inventory, initial half-heart",
            EnumSet.of(
                    ChallengeComponent.HARDCORE,
                    ChallengeComponent.SHARED_HEALTH,
                    ChallengeComponent.DEGRADING_INVENTORY,
                    ChallengeComponent.INITIAL_HALF_HEART));

    private final String key;
    private final String displayName;
    private final Set<ChallengeComponent> defaultComponents;

    ChallengeMode(String key, String displayName, Set<ChallengeComponent> defaultComponents) {
        this.key = key;
        this.displayName = displayName;
        this.defaultComponents = EnumSet.copyOf(defaultComponents);
    }

    /**
     * Returns the stable key for this challenge mode.
     *
     * @return configuration key for this mode
     */
    public String key() {
        return key;
    }

    /**
     * Returns the display name for this challenge mode.
     *
     * @return user-facing display label for this mode
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns a defensive copy of default components enabled by this mode.
     *
     * @return copy of default components enabled for this challenge mode
     */
    public Set<ChallengeComponent> defaultComponents() {
        return EnumSet.copyOf(defaultComponents);
    }

    /**
     * Resolves a challenge mode from a configuration key.
     *
     * @param key mode key to resolve
     * @return optional matching challenge mode
     */
    public static Optional<ChallengeMode> fromKey(String key) {
        String normalized = normalizeLegacyKey(key);
        return Arrays.stream(values())
                .filter(mode -> mode.key.equalsIgnoreCase(normalized))
                .findFirst();
    }

    private static String normalizeLegacyKey(String key) {
        if (key == null) {
            return "";
        }

        return switch (key.toLowerCase()) {
            case "keep_inventory_unlimited_deaths" -> "keep_inventory_non_hardcore";
            case "lose_inventory_unlimited_deaths" -> "lose_inventory_non_hardcore";
            case "lose_inventory_unlimited_deaths_shared_inventory" -> "lose_inventory_non_hardcore_shared_inventory";
            default -> key;
        };
    }
}
