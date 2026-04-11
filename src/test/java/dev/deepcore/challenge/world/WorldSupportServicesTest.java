package dev.deepcore.challenge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.logging.DeepCoreLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WorldSupportServicesTest {

    @Test
    void isLobbyOrLimboWorld_returnsFalseForNullWorld() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        WorldClassificationService service = new WorldClassificationService(configView, () -> null);

        assertFalse(service.isLobbyOrLimboWorld(null));
    }

    @Test
    void isLobbyOrLimboWorld_returnsTrueWhenManagerMarksLobby() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        World world = mock(World.class);

        when(worldResetManager.isLobbyWorld(world)).thenReturn(true);

        WorldClassificationService service = new WorldClassificationService(configView, () -> worldResetManager);

        assertTrue(service.isLobbyOrLimboWorld(world));
    }

    @Test
    void isLobbyOrLimboWorld_matchesConfiguredNamesCaseInsensitive() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        World world = mock(World.class);

        when(world.getName()).thenReturn("DeEpCoRe_LiMbO");
        when(configView.limboWorldName()).thenReturn("deepcore_limbo");
        when(configView.lobbyOverworldWorldName()).thenReturn("deepcore_lobby_overworld");
        when(configView.lobbyNetherWorldName()).thenReturn("deepcore_lobby_nether");

        WorldClassificationService service = new WorldClassificationService(configView, () -> null);

        assertTrue(service.isLobbyOrLimboWorld(world));
    }

    @Test
    void isLobbyOrLimboWorld_returnsFalseForUnmatchedWorldName() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        World world = mock(World.class);

        when(world.getName()).thenReturn("run_world");
        when(configView.limboWorldName()).thenReturn("deepcore_limbo");
        when(configView.lobbyOverworldWorldName()).thenReturn("deepcore_lobby_overworld");
        when(configView.lobbyNetherWorldName()).thenReturn("deepcore_lobby_nether");

        WorldClassificationService service = new WorldClassificationService(configView, () -> null);

        assertFalse(service.isLobbyOrLimboWorld(world));
    }

    @Test
    void ensureWorldStorageDirectories_createsExpectedSubdirectories() throws Exception {
        Path worldRoot = Files.createTempDirectory("world-storage-service-");
        Path worldFolder = worldRoot.resolve("sample_world");

        DeepCoreLogger log = mock(DeepCoreLogger.class);
        World world = mock(World.class);
        when(world.getWorldFolder()).thenReturn(worldFolder.toFile());

        WorldStorageService service = new WorldStorageService(log);
        service.ensureWorldStorageDirectories(world);

        assertTrue(Files.isDirectory(worldFolder));
        assertTrue(Files.isDirectory(worldFolder.resolve("data")));
        assertTrue(Files.isDirectory(worldFolder.resolve("playerdata")));
        assertTrue(Files.isDirectory(worldFolder.resolve("poi")));
        assertTrue(Files.isDirectory(worldFolder.resolve("stats")));
        assertTrue(Files.isDirectory(worldFolder.resolve("advancements")));
        verify(log, never()).warn(contains("Could not ensure storage dirs"));
    }

    @Test
    void ensureAllWorldStorageDirectories_appliesToEveryLoadedWorld() throws Exception {
        Path worldRoot = Files.createTempDirectory("world-storage-service-all-");
        Path worldA = worldRoot.resolve("world_a");
        Path worldB = worldRoot.resolve("world_b");

        DeepCoreLogger log = mock(DeepCoreLogger.class);
        World first = mock(World.class);
        World second = mock(World.class);
        when(first.getWorldFolder()).thenReturn(worldA.toFile());
        when(second.getWorldFolder()).thenReturn(worldB.toFile());

        WorldStorageService service = new WorldStorageService(log);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(first, second));
            service.ensureAllWorldStorageDirectories();
        }

        assertTrue(Files.isDirectory(worldA.resolve("data")));
        assertTrue(Files.isDirectory(worldB.resolve("data")));
    }
}
