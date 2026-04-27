package dev.deepcore.challenge.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class TrainingStatsStoreTest {

    @Test
    void recordCompletedAttempt_tracksBestAndKeepsLastFiveMostRecent() throws Exception {
        Path tempDir = Files.createTempDirectory("training-stats-record");
        JavaPlugin plugin = pluginWithDataFolder(tempDir.toFile());
        TrainingStatsStore store = new TrainingStatsStore(plugin);

        UUID player = UUID.randomUUID();
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 5000L);
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 4200L);
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 6100L);
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 3900L);
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 4500L);
        store.recordCompletedAttempt(player, TrainingChallengeType.PORTAL, 4700L);

        TrainingStatsStore.PlayerChallengeStats stats = store.getStats(player, TrainingChallengeType.PORTAL);
        assertEquals(3900L, stats.bestTimeMs());
        assertEquals(List.of(4200L, 4500L, 4700L, 5000L, 6100L), stats.lastAttemptsMs());
    }

    @Test
    void saveAndLoad_roundTripsStatsPerPlayerAndChallenge() throws Exception {
        Path tempDir = Files.createTempDirectory("training-stats-roundtrip");
        JavaPlugin plugin = pluginWithDataFolder(tempDir.toFile());

        TrainingStatsStore writer = new TrainingStatsStore(plugin);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        writer.recordCompletedAttempt(playerA, TrainingChallengeType.CRAFT, 10000L);
        writer.recordCompletedAttempt(playerA, TrainingChallengeType.CRAFT, 8500L);
        writer.recordCompletedAttempt(playerB, TrainingChallengeType.BRIDGE, 3000L);
        writer.save();

        TrainingStatsStore reader = new TrainingStatsStore(plugin);
        reader.load();

        TrainingStatsStore.PlayerChallengeStats aStats = reader.getStats(playerA, TrainingChallengeType.CRAFT);
        assertEquals(8500L, aStats.bestTimeMs());
        assertEquals(List.of(10000L), aStats.lastAttemptsMs());

        TrainingStatsStore.PlayerChallengeStats bStats = reader.getStats(playerB, TrainingChallengeType.BRIDGE);
        assertEquals(3000L, bStats.bestTimeMs());
        assertTrue(bStats.lastAttemptsMs().isEmpty());

        TrainingStatsStore.PlayerChallengeStats missing =
                reader.getStats(UUID.randomUUID(), TrainingChallengeType.CHEST);
        assertEquals(-1L, missing.bestTimeMs());
        assertTrue(missing.lastAttemptsMs().isEmpty());
    }

    @Test
    void load_ignoresInvalidPlayerIdsAndKeepsFirstFiveAttemptEntries() throws Exception {
        Path tempDir = Files.createTempDirectory("training-stats-load");
        File dataFolder = tempDir.toFile();
        JavaPlugin plugin = pluginWithDataFolder(dataFolder);
        String validPlayer = UUID.randomUUID().toString();

        Path yamlPath = dataFolder.toPath().resolve("training-stats.yml");
        String yaml = "players:\n"
                + "  not-a-uuid:\n"
                + "    portal:\n"
                + "      best_time_ms: 1\n"
                + "      attempts_ms: [1, 2]\n"
                + "  " + validPlayer + ":\n"
                + "    chest:\n"
                + "      best_time_ms: 2500\n"
                + "      attempts_ms: [10, 20, 30, 40, 50, 60, 70]\n";
        Files.writeString(yamlPath, yaml);

        TrainingStatsStore store = new TrainingStatsStore(plugin);
        store.load();

        TrainingStatsStore.PlayerChallengeStats stats =
                store.getStats(UUID.fromString(validPlayer), TrainingChallengeType.CHEST);
        assertEquals(2500L, stats.bestTimeMs());
        assertEquals(List.of(10L, 20L, 30L, 40L, 50L), stats.lastAttemptsMs());
    }

    @Test
    void playerChallengeStats_exposesImmutableAttemptView() {
        TrainingStatsStore.PlayerChallengeStats stats =
                new TrainingStatsStore.PlayerChallengeStats(100L, List.of(100L, 200L));

        assertThrows(UnsupportedOperationException.class, () -> stats.lastAttemptsMs()
                .add(300L));
    }

    private static JavaPlugin pluginWithDataFolder(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return plugin;
    }
}
