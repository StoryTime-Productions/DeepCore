package dev.deepcore.challenge;

import dev.deepcore.challenge.training.TrainingManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /lobby command, returning players to the DeepCore lobby from the training gym.
 */
public final class LobbyCommand implements CommandExecutor {
    private final TrainingManager trainingManager;
    private final ChallengeSessionManager challengeSessionManager;

    /**
     * Creates a lobby command handler.
     *
     * @param trainingManager          training manager for leave-training logic
     * @param challengeSessionManager  session manager for lobby teleport logic
     */
    public LobbyCommand(TrainingManager trainingManager, ChallengeSessionManager challengeSessionManager) {
        this.trainingManager = trainingManager;
        this.challengeSessionManager = challengeSessionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /lobby.");
            return true;
        }

        if (challengeSessionManager.isRunningPhase()) {
            player.sendMessage(ChatColor.RED + "You cannot use /lobby during an active challenge.");
            return true;
        }

        trainingManager.leaveTraining(player);
        return true;
    }
}
