package dev.deepcore.logging;

import java.util.Locale;

/**
 * Severity levels used by the DeepCore logger.
 */
public enum DeepCoreLogLevel {
    /** Diagnostic-level details for troubleshooting. */
    DEBUG(10),
    /** Standard informational operational messages. */
    INFO(20),
    /** Recoverable warning conditions requiring attention. */
    WARN(30),
    /** Error conditions indicating failures. */
    ERROR(40);

    private final int weight;

    DeepCoreLogLevel(int weight) {
        this.weight = weight;
    }

    /**
     * Returns true when this level includes messages at the other level.
     *
     * @param other level being evaluated against this threshold
     * @return true when the other level is at or above this level's weight
     */
    public boolean includes(DeepCoreLogLevel other) {
        return other.weight >= this.weight;
    }

    /**
     * Parses a level string and returns fallback for null/blank/invalid values.
     *
     * @param raw      raw level name value to parse
     * @param fallback level to return when parsing fails
     * @return parsed log level, or fallback when input is missing or invalid
     */
    public static DeepCoreLogLevel fromString(String raw, DeepCoreLogLevel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return DeepCoreLogLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
