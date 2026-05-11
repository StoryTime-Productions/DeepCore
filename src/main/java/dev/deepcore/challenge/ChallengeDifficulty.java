package dev.deepcore.challenge;

import java.util.Optional;

/** Represents the selectable difficulty level for a challenge run. */
public enum ChallengeDifficulty {
    EASY("easy", "Easy"),
    NORMAL("normal", "Normal"),
    HARD("hard", "Hard");

    private final String key;
    private final String displayName;

    ChallengeDifficulty(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Returns the next difficulty in the cycle, wrapping from HARD back to EASY.
     *
     * @return next difficulty in the cycle
     */
    public ChallengeDifficulty next() {
        ChallengeDifficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static Optional<ChallengeDifficulty> fromKey(String key) {
        for (ChallengeDifficulty d : values()) {
            if (d.key.equalsIgnoreCase(key)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }
}
