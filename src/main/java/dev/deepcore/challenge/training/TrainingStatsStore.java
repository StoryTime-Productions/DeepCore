package dev.deepcore.challenge.training;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists per-player training challenge times and recent attempt history.
 */
public final class TrainingStatsStore {
    private static final int MAX_ATTEMPT_HISTORY = 5;

    private final File file;
    private final Map<UUID, Map<TrainingChallengeType, PlayerChallengeStats>> statsByPlayer;

    /**
     * Creates a stats store rooted in the plugin data directory.
     *
     * @param plugin plugin instance owning this store
     */
    public TrainingStatsStore(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "training-stats.yml");
        this.statsByPlayer = new java.util.HashMap<>();
    }

    /** Loads all persisted stats into memory. */
    public void load() {
        statsByPlayer.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidKey : playersSection.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidKey);
            if (playerSection == null) {
                continue;
            }

            Map<TrainingChallengeType, PlayerChallengeStats> byChallenge = new EnumMap<>(TrainingChallengeType.class);
            for (TrainingChallengeType challengeType : TrainingChallengeType.values()) {
                ConfigurationSection challengeSection = playerSection.getConfigurationSection(challengeType.key());
                if (challengeSection == null) {
                    continue;
                }

                long bestTimeMs = challengeSection.getLong("best_time_ms", -1L);
                List<Long> attempts = new ArrayList<>();
                for (Long value : challengeSection.getLongList("attempts_ms")) {
                    attempts.add(value);
                    if (attempts.size() >= MAX_ATTEMPT_HISTORY) {
                        break;
                    }
                }

                byChallenge.put(challengeType, new PlayerChallengeStats(bestTimeMs, attempts));
            }

            if (!byChallenge.isEmpty()) {
                statsByPlayer.put(playerId, byChallenge);
            }
        }
    }

    /** Saves the in-memory stats map to disk. */
    public void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Map<TrainingChallengeType, PlayerChallengeStats>> playerEntry : statsByPlayer.entrySet()) {
            String basePath = "players." + playerEntry.getKey();
            for (Map.Entry<TrainingChallengeType, PlayerChallengeStats> challengeEntry :
                    playerEntry.getValue().entrySet()) {
                String challengePath = basePath + "." + challengeEntry.getKey().key();
                PlayerChallengeStats stats = challengeEntry.getValue();
                configuration.set(challengePath + ".best_time_ms", stats.bestTimeMs());
                configuration.set(challengePath + ".attempts_ms", stats.lastAttemptsMs());
            }
        }

        try {
            configuration.save(file);
        } catch (IOException ignored) {
            // Caller handles operational logging; save failures should not crash plugin
            // runtime.
        }
    }

    /**
     * Records a completed attempt time for one player and challenge type.
     *
     * @param playerId  player UUID
     * @param type      challenge type
     * @param elapsedMs completed elapsed time in milliseconds
     */
    public void recordCompletedAttempt(UUID playerId, TrainingChallengeType type, long elapsedMs) {
        Map<TrainingChallengeType, PlayerChallengeStats> byChallenge =
                statsByPlayer.computeIfAbsent(playerId, ignored -> new EnumMap<>(TrainingChallengeType.class));

        PlayerChallengeStats current = byChallenge.get(type);

        List<Long> allTimes = new ArrayList<>();
        if (current != null && current.bestTimeMs() >= 0L) {
            allTimes.add(current.bestTimeMs());
        }
        if (current != null) {
            allTimes.addAll(current.lastAttemptsMs());
        }
        allTimes.add(elapsedMs);
        Collections.sort(allTimes);

        long bestTime = allTimes.get(0);
        List<Long> top5After = new ArrayList<>(allTimes.subList(1, Math.min(1 + MAX_ATTEMPT_HISTORY, allTimes.size())));
        byChallenge.put(type, new PlayerChallengeStats(bestTime, top5After));
    }

    /**
     * Returns immutable stats snapshot for one player/challenge.
     *
     * @param playerId player UUID
     * @param type     challenge type
     * @return stats snapshot; empty values when no attempts exist
     */
    public PlayerChallengeStats getStats(UUID playerId, TrainingChallengeType type) {
        Map<TrainingChallengeType, PlayerChallengeStats> byChallenge = statsByPlayer.get(playerId);
        if (byChallenge == null) {
            return new PlayerChallengeStats(-1L, List.of());
        }
        PlayerChallengeStats stats = byChallenge.get(type);
        return stats == null ? new PlayerChallengeStats(-1L, List.of()) : stats;
    }

    /**
     * Immutable per-player stats for one challenge.
     *
     * @param bestTimeMs     best completed attempt in milliseconds; -1 when unknown
     * @param lastAttemptsMs 2nd through 6th best times sorted ascending (Try 1–5)
     */
    public record PlayerChallengeStats(long bestTimeMs, List<Long> lastAttemptsMs) {
        public PlayerChallengeStats {
            lastAttemptsMs = Collections.unmodifiableList(new ArrayList<>(lastAttemptsMs));
        }
    }
}
