package dev.deepcore.challenge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.config.ChallengeConfigView;
import java.util.function.Supplier;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class WorldClassificationServiceTest {

    @Test
    void isLobbyOrLimboWorld_handlesNullAndResetManagerLobby() {
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        WorldResetManager resetManager = mock(WorldResetManager.class);
        WorldClassificationService service = new WorldClassificationService(config, () -> resetManager);

        assertFalse(service.isLobbyOrLimboWorld(null));

        World world = mock(World.class);
        when(resetManager.isLobbyWorld(world)).thenReturn(true);
        assertTrue(service.isLobbyOrLimboWorld(world));
    }

    @Test
    void isLobbyOrLimboWorld_fallsBackToConfiguredNames() {
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.limboWorldName()).thenReturn("deepcore_limbo");
        when(config.lobbyOverworldWorldName()).thenReturn("deepcore_lobby_overworld");
        when(config.lobbyNetherWorldName()).thenReturn("deepcore_lobby_nether");

        Supplier<WorldResetManager> noResetManager = () -> null;
        WorldClassificationService service = new WorldClassificationService(config, noResetManager);

        World limbo = mock(World.class);
        when(limbo.getName()).thenReturn("DEEPCORE_LIMBO");
        assertTrue(service.isLobbyOrLimboWorld(limbo));

        World lobbyOverworld = mock(World.class);
        when(lobbyOverworld.getName()).thenReturn("deepcore_lobby_overworld");
        assertTrue(service.isLobbyOrLimboWorld(lobbyOverworld));

        World lobbyNether = mock(World.class);
        when(lobbyNether.getName()).thenReturn("deepcore_lobby_nether");
        assertTrue(service.isLobbyOrLimboWorld(lobbyNether));

        World runWorld = mock(World.class);
        when(runWorld.getName()).thenReturn("deepcore_run");
        assertFalse(service.isLobbyOrLimboWorld(runWorld));
    }

    @Test
    void trainingAndPrepBorderExemption_coverBranches() {
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.trainingWorldName()).thenReturn("deepcore_gym");
        when(config.limboWorldName()).thenReturn("deepcore_limbo");
        when(config.lobbyOverworldWorldName()).thenReturn("deepcore_lobby_overworld");
        when(config.lobbyNetherWorldName()).thenReturn("deepcore_lobby_nether");

        WorldResetManager resetManager = mock(WorldResetManager.class);
        WorldClassificationService service = new WorldClassificationService(config, () -> resetManager);

        World training = mock(World.class);
        when(training.getName()).thenReturn("DEEPCORE_GYM");
        when(resetManager.isLobbyWorld(training)).thenReturn(false);

        World runWorld = mock(World.class);
        when(runWorld.getName()).thenReturn("deepcore_run");
        when(resetManager.isLobbyWorld(runWorld)).thenReturn(false);

        assertTrue(service.isTrainingWorld(training));
        assertFalse(service.isTrainingWorld(null));

        assertTrue(service.isPrepBorderExemptWorld(training));
        assertFalse(service.isPrepBorderExemptWorld(runWorld));
        assertFalse(service.isPrepBorderExemptWorld(null));
    }
}
