package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.session.RunPauseResumeService;
import dev.deepcore.challenge.session.SessionRulesCoordinatorService;
import dev.deepcore.challenge.session.SessionState;
import dev.deepcore.challenge.session.SessionTransitionOrchestratorService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.lang.reflect.Field;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ChallengeSessionManagerSmokeTest {

    @Test
    void constructor_andBasicPhaseQueries_workWithMockedPlugin() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        try (MockedConstruction<NamespacedKey> ignored = org.mockito.Mockito.mockConstruction(NamespacedKey.class)) {
            ChallengeSessionManager manager = new ChallengeSessionManager(plugin, challengeManager);

            assertTrue(manager.canEditSettings());
            assertTrue(manager.isPrepPhase());
            assertEquals("prep", manager.getPhaseName());
            assertEquals(0, manager.getReadyCount());
        }
    }

    @Test
    void delegatingMethods_invokeBackedServices() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        try (MockedConstruction<NamespacedKey> ignored = org.mockito.Mockito.mockConstruction(NamespacedKey.class)) {
            ChallengeSessionManager manager = new ChallengeSessionManager(plugin, challengeManager);

            SessionTransitionOrchestratorService transition = mock(SessionTransitionOrchestratorService.class);
            SessionRulesCoordinatorService rules = mock(SessionRulesCoordinatorService.class);
            RunPauseResumeService pauseResume = mock(RunPauseResumeService.class);
            dev.deepcore.challenge.events.ChallengeEventRegistrar registrar =
                    mock(dev.deepcore.challenge.events.ChallengeEventRegistrar.class);
            dev.deepcore.challenge.preview.PreviewOrchestratorService preview =
                    mock(dev.deepcore.challenge.preview.PreviewOrchestratorService.class);
            dev.deepcore.challenge.preview.PreviewAnchorService previewAnchor =
                    mock(dev.deepcore.challenge.preview.PreviewAnchorService.class);

            setField(manager, "sessionTransitionOrchestratorService", transition);
            setField(manager, "sessionRulesCoordinatorService", rules);
            setField(manager, "runPauseResumeService", pauseResume);
            setField(manager, "challengeEventRegistrar", registrar);
            setField(manager, "previewOrchestratorService", preview);
            setField(manager, "previewAnchorService", previewAnchor);

            WorldResetManager worldResetManager = mock(WorldResetManager.class);
            manager.setWorldResetManager(worldResetManager);
            manager.setRecordsService(mock(dev.deepcore.records.RunRecordsService.class));

            manager.initialize();
            manager.registerEventListeners();
            manager.syncWorldRules();
            manager.applySharedVitalsIfEnabled();
            manager.refreshLobbyPreview();
            manager.shutdown();
            manager.resetForNewRun();
            manager.endChallengeAndReturnToPrep();
            manager.ensurePrepBook(mock(org.bukkit.entity.Player.class));

            CommandSender sender = mock(CommandSender.class);
            when(pauseResume.pause(sender, true)).thenReturn(true);
            when(pauseResume.resume(sender)).thenReturn(true);
            assertTrue(manager.pauseChallenge(sender));
            assertTrue(manager.resumeChallenge(sender));

            Location location = mock(Location.class);
            when(previewAnchor.getPreferredLobbyTeleportLocation()).thenReturn(location);
            assertSame(location, manager.getPreferredLobbyTeleportLocation());

            verify(transition).initialize();
            verify(registrar).registerAll(plugin);
            verify(rules).syncWorldRules();
            verify(rules).applySharedVitalsIfEnabled();
            verify(preview).refreshLobbyPreview();
            verify(transition).shutdown();
            verify(transition).resetForNewRun();
            verify(transition).endChallengeAndReturnToPrep();
            verify(transition).ensurePrepBook(org.mockito.ArgumentMatchers.any());
            verify(pauseResume).pause(sender, true);
            verify(pauseResume).resume(sender);
        }
    }

    @Test
    void phaseQueries_reflectUnderlyingSessionState() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        try (MockedConstruction<NamespacedKey> ignored = org.mockito.Mockito.mockConstruction(NamespacedKey.class)) {
            ChallengeSessionManager manager = new ChallengeSessionManager(plugin, challengeManager);
            SessionState sessionState = (SessionState) getField(manager, "sessionState");
            assertNotNull(sessionState);

            sessionState.setPhase(SessionState.Phase.RUNNING);
            assertTrue(manager.isRunningPhase());
            assertFalse(manager.isPrepPhase());
            assertFalse(manager.isPausedPhase());
            assertFalse(manager.canEditSettings());
            assertEquals("running", manager.getPhaseName());

            sessionState.setPhase(SessionState.Phase.PAUSED);
            assertTrue(manager.isPausedPhase());
            assertFalse(manager.isRunningPhase());
            assertEquals("paused", manager.getPhaseName());
        }
    }

    @Test
    void getReadyTargetCount_usesCurrentOnlinePlayerCount() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        try (MockedConstruction<NamespacedKey> ignored = org.mockito.Mockito.mockConstruction(NamespacedKey.class);
                MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            ChallengeSessionManager manager = new ChallengeSessionManager(plugin, challengeManager);
            bukkit.when(Bukkit::getOnlinePlayers)
                    .thenReturn(List.of(
                            mock(org.bukkit.entity.Player.class),
                            mock(org.bukkit.entity.Player.class),
                            mock(org.bukkit.entity.Player.class)));

            assertEquals(3, manager.getReadyTargetCount());
        }
    }

    @Test
    void setters_storeInjectedServices() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        try (MockedConstruction<NamespacedKey> ignored = org.mockito.Mockito.mockConstruction(NamespacedKey.class)) {
            ChallengeSessionManager manager = new ChallengeSessionManager(plugin, challengeManager);
            WorldResetManager worldResetManager = mock(WorldResetManager.class);
            dev.deepcore.records.RunRecordsService recordsService = mock(dev.deepcore.records.RunRecordsService.class);

            manager.setWorldResetManager(worldResetManager);
            manager.setRecordsService(recordsService);

            assertSame(worldResetManager, getField(manager, "worldResetManager"));
            assertSame(recordsService, getField(manager, "recordsService"));
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
