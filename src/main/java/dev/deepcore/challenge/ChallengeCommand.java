package dev.deepcore.challenge;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.training.TrainingManager;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Handles the /challenge command and tab completion.
 */
public final class ChallengeCommand implements CommandExecutor, TabCompleter {
    private final ChallengeCoreCommandHandler coreCommandHandler;
    private final ChallengeLogsCommandHandler logsCommandHandler;

    /**
     * Creates a command handler backed by challenge and world-reset services.
     *
     * @param plugin                  plugin root instance for logger/service access
     * @param challengeManager        challenge settings and component manager
     * @param challengeSessionManager challenge session orchestration manager
     * @param worldResetManager       world reset and world lifecycle manager
     * @param trainingManager         training gym manager
     */
    public ChallengeCommand(
            DeepCorePlugin plugin,
            ChallengeManager challengeManager,
            ChallengeSessionManager challengeSessionManager,
            WorldResetManager worldResetManager,
            TrainingManager trainingManager) {
        ChallengeAdminFacade adminFacade = new ChallengeAdminFacade(
                plugin,
                challengeManager,
                challengeSessionManager,
                worldResetManager,
                trainingManager,
                plugin.getDeepCoreLogger());
        this.coreCommandHandler =
                new ChallengeCoreCommandHandler(adminFacade, trainingManager, plugin.getDeepCoreLogger());
        this.logsCommandHandler = new ChallengeLogsCommandHandler(plugin.getDeepCoreLogger());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("logs")) {
            return logsCommandHandler.handle(sender, args);
        }

        return coreCommandHandler.handle(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("logs")) {
            return logsCommandHandler.tabComplete(sender, args);
        }

        return coreCommandHandler.tabComplete(args);
    }
}
