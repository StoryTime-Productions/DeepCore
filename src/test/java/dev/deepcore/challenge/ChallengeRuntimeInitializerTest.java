package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
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
        PluginCommand command = mock(PluginCommand.class);

        when(plugin.getCommand("challenge")).thenReturn(command);
        when(plugin.getDeepCoreLogger()).thenReturn(logger);

        try (MockedConstruction<ChallengeManager> managerConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeManager.class);
                MockedConstruction<ChallengeSessionManager> sessionConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeSessionManager.class);
                MockedConstruction<WorldResetManager> worldResetConstruction =
                        org.mockito.Mockito.mockConstruction(WorldResetManager.class);
                MockedConstruction<RunRecordsService> recordsConstruction =
                        org.mockito.Mockito.mockConstruction(RunRecordsService.class);
                MockedConstruction<ChallengeCommand> commandConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeCommand.class)) {

            ChallengeRuntime runtime = new ChallengeRuntimeInitializer().initialize(plugin, logger);

            ChallengeManager manager = managerConstruction.constructed().get(0);
            ChallengeSessionManager sessionManager =
                    sessionConstruction.constructed().get(0);
            WorldResetManager worldResetManager =
                    worldResetConstruction.constructed().get(0);
            RunRecordsService recordsService = recordsConstruction.constructed().get(0);
            ChallengeCommand challengeCommand =
                    commandConstruction.constructed().get(0);

            verify(manager).loadFromConfig();
            verify(worldResetManager).ensureThreeWorldsLoaded();
            verify(worldResetManager).cleanupNonDefaultWorldsOnStartup();
            verify(sessionManager).setWorldResetManager(worldResetManager);
            verify(recordsService).initialize();
            verify(sessionManager).setRecordsService(recordsService);
            verify(sessionManager).registerEventListeners();
            verify(sessionManager).initialize();
            verify(command).setExecutor(challengeCommand);
            verify(command).setTabCompleter(challengeCommand);
            verify(logger).debug("Challenge command registered.");

            assertSame(manager, runtime.getChallengeManager());
            assertSame(sessionManager, runtime.getChallengeSessionManager());
            assertSame(worldResetManager, runtime.getWorldResetManager());
            assertSame(recordsService, runtime.getRunRecordsService());
        }
    }

    @Test
    void initialize_throwsWhenChallengeCommandMissing() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger logger = mock(DeepCoreLogger.class);

        when(plugin.getCommand("challenge")).thenReturn(null);

        try (MockedConstruction<ChallengeManager> managerConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeManager.class);
                MockedConstruction<ChallengeSessionManager> sessionConstruction =
                        org.mockito.Mockito.mockConstruction(ChallengeSessionManager.class);
                MockedConstruction<WorldResetManager> worldResetConstruction =
                        org.mockito.Mockito.mockConstruction(WorldResetManager.class);
                MockedConstruction<RunRecordsService> recordsConstruction =
                        org.mockito.Mockito.mockConstruction(RunRecordsService.class)) {

            assertThrows(
                    IllegalStateException.class, () -> new ChallengeRuntimeInitializer().initialize(plugin, logger));
        }
    }
}
