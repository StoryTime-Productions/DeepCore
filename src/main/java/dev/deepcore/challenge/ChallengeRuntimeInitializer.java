package dev.deepcore.challenge;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import dev.deepcore.records.RunRecordsService;
import org.bukkit.command.PluginCommand;

/**
 * Initializes and wires challenge runtime services during plugin startup.
 */
public final class ChallengeRuntimeInitializer {
    /**
     * Initializes challenge runtime services, registers listeners/commands, and
     * returns the assembled runtime container.
     *
     * @param plugin plugin instance used for initialization
     * @param logger logger used for registration diagnostics
     * @return fully initialized challenge runtime container
     */
    public ChallengeRuntime initialize(DeepCorePlugin plugin, DeepCoreLogger logger) {
        ChallengeManager challengeManager = new ChallengeManager(plugin);
        challengeManager.loadFromConfig();

        ChallengeSessionManager challengeSessionManager = new ChallengeSessionManager(plugin, challengeManager);

        WorldResetManager worldResetManager = new WorldResetManager(plugin, challengeSessionManager);
        worldResetManager.ensureThreeWorldsLoaded();
        worldResetManager.cleanupNonDefaultWorldsOnStartup();
        challengeSessionManager.setWorldResetManager(worldResetManager);

        RunRecordsService recordsService = new RunRecordsService(plugin);
        recordsService.initialize();
        challengeSessionManager.setRecordsService(recordsService);

        challengeSessionManager.registerEventListeners();
        challengeSessionManager.initialize();

        registerChallengeCommand(plugin, challengeManager, challengeSessionManager, worldResetManager, logger);

        return new ChallengeRuntime(challengeManager, challengeSessionManager, worldResetManager, recordsService);
    }

    private void registerChallengeCommand(
            DeepCorePlugin plugin,
            ChallengeManager challengeManager,
            ChallengeSessionManager challengeSessionManager,
            WorldResetManager worldResetManager,
            DeepCoreLogger logger) {
        PluginCommand command = plugin.getCommand("challenge");
        if (command == null) {
            throw new IllegalStateException("Command 'challenge' is not defined in plugin.yml.");
        }

        ChallengeCommand challengeCommand =
                new ChallengeCommand(plugin, challengeManager, challengeSessionManager, worldResetManager);
        command.setExecutor(challengeCommand);
        command.setTabCompleter(challengeCommand);
        logger.debug("Challenge command registered.");
    }
}
