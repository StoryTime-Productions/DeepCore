package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.training.TrainingManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import dev.deepcore.records.RunRecordsService;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class ChallengeRuntimeInitializerTest {

    @Test
    void initialize_wiresServicesAndRegistersChallengeCommand() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);
        PluginCommand challengeCommandBinding = mock(PluginCommand.class);
        PluginCommand lobbyCommandBinding = mock(PluginCommand.class);

        when(plugin.getCommand("challenge")).thenReturn(challengeCommandBinding);
        when(plugin.getCommand("lobby")).thenReturn(lobbyCommandBinding);
        when(plugin.getDeepCoreLogger()).thenReturn(logger);

        try (MockedConstruction<ChallengeManager> managerConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeManager.class);
                MockedConstruction<ChallengeSessionManager> sessionConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeSessionManager.class);
                MockedConstruction<WorldResetManager> worldResetConstruction =
                        org.mockito.Mockito.mockConstruction(WorldResetManager.class);
                MockedConstruction<RunRecordsService> recordsConstruction =
                        org.mockito.Mockito.mockConstruction(RunRecordsService.class);
                MockedConstruction<TrainingManager> trainingConstruction =
                        org.mockito.Mockito.mockConstruction(TrainingManager.class);
                MockedConstruction<ChallengeCommand> commandConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeCommand.class)) {

            ChallengeRuntime runtime = new ChallengeRuntimeInitializer().initialize(plugin, logger);

            ChallengeManager manager = managerConstruction.constructed().get(0);
            ChallengeSessionManager sessionManager =
                    sessionConstruction.constructed().get(0);
            WorldResetManager worldResetManager =
                    worldResetConstruction.constructed().get(0);
            RunRecordsService recordsService = recordsConstruction.constructed().get(0);
            TrainingManager trainingManager = trainingConstruction.constructed().get(0);
            ChallengeCommand challengeCommand =
                    commandConstruction.constructed().get(0);

            verify(manager).loadFromConfig();
            verify(worldResetManager).ensureThreeWorldsLoaded();
            verify(worldResetManager).cleanupNonDefaultWorldsOnStartup();
            verify(sessionManager).setWorldResetManager(worldResetManager);
            verify(recordsService).initialize();
            verify(sessionManager).setRecordsService(recordsService);
            verify(trainingManager).initialize();
            verify(sessionManager).registerEventListeners();
            verify(sessionManager).initialize();
            verify(challengeCommandBinding).setExecutor(challengeCommand);
            verify(challengeCommandBinding).setTabCompleter(challengeCommand);
            verify(lobbyCommandBinding).setExecutor(any());
            verify(logger).debug("Challenge command registered.");
            verify(logger).debug("Lobby command registered.");

            assertSame(manager, runtime.getChallengeManager());
            assertSame(sessionManager, runtime.getChallengeSessionManager());
            assertSame(worldResetManager, runtime.getWorldResetManager());
            assertSame(recordsService, runtime.getRunRecordsService());
            assertSame(trainingManager, runtime.getTrainingManager());
        }
    }

    @Test
    void initialize_throwsWhenChallengeCommandMissing() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getCommand("challenge")).thenReturn(null);
        when(plugin.getCommand("lobby")).thenReturn(mock(PluginCommand.class));

        try (MockedConstruction<ChallengeManager> managerConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeManager.class);
                MockedConstruction<ChallengeSessionManager> sessionConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeSessionManager.class);
                MockedConstruction<WorldResetManager> worldResetConstruction =
                        org.mockito.Mockito.mockConstruction(WorldResetManager.class);
                MockedConstruction<RunRecordsService> recordsConstruction =
                        org.mockito.Mockito.mockConstruction(RunRecordsService.class);
                MockedConstruction<TrainingManager> trainingConstruction =
                        org.mockito.Mockito.mockConstruction(TrainingManager.class)) {

            assertThrows(
                    IllegalStateException.class, () -> new ChallengeRuntimeInitializer().initialize(plugin, logger));
        }
    }

    @Test
    void initialize_throwsWhenLobbyCommandMissing() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getCommand("challenge")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("lobby")).thenReturn(null);

        try (MockedConstruction<ChallengeManager> managerConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeManager.class);
                MockedConstruction<ChallengeSessionManager> sessionConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeSessionManager.class);
                MockedConstruction<WorldResetManager> worldResetConstruction =
                        org.mockito.Mockito.mockConstruction(WorldResetManager.class);
                MockedConstruction<RunRecordsService> recordsConstruction =
                        org.mockito.Mockito.mockConstruction(RunRecordsService.class);
                MockedConstruction<TrainingManager> trainingConstruction =
                        org.mockito.Mockito.mockConstruction(TrainingManager.class)) {

            assertThrows(
                    IllegalStateException.class, () -> new ChallengeRuntimeInitializer().initialize(plugin, logger));
        }
    }
}
