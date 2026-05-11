package dev.deepcore.challenge.session;

import dev.deepcore.logging.DeepCoreLogger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Collects unanimous player votes to save the current speedrun to disk. */
public final class RunSaveVoteService {
    private static final int VOTE_TIMEOUT_SECONDS = 60;

    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final Supplier<List<Player>> onlineParticipantsSupplier;

    private final Set<UUID> votes = new HashSet<>();
    private BukkitTask timeoutTask;

    /**
     * Creates a run-save vote service.
     *
     * @param plugin                     plugin instance for scheduling tasks
     * @param log                        logger for vote progress and timeout messages
     * @param onlineParticipantsSupplier supplier for currently online participants
     */
    public RunSaveVoteService(
            JavaPlugin plugin, DeepCoreLogger log, Supplier<List<Player>> onlineParticipantsSupplier) {
        this.plugin = plugin;
        this.log = log;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
    }

    /**
     * Records a vote from the given player and triggers the save callback when
     * all online participants have voted.
     *
     * @param voter      player casting the vote
     * @param onAllVoted runnable executed when unanimous vote is reached
     * @return false if the player already voted or is not a participant
     */
    public boolean castVote(Player voter, Runnable onAllVoted) {
        List<Player> participants = onlineParticipantsSupplier.get();
        if (participants.isEmpty()) {
            log.sendError(voter, "No active run participants to vote with.");
            return false;
        }

        boolean isParticipant =
                participants.stream().anyMatch(p -> p.getUniqueId().equals(voter.getUniqueId()));
        if (!isParticipant) {
            log.sendError(voter, "You are not a participant in the current run.");
            return false;
        }

        if (votes.contains(voter.getUniqueId())) {
            log.sendWarn(voter, "You have already voted to save this run.");
            return false;
        }

        boolean firstVote = votes.isEmpty();
        votes.add(voter.getUniqueId());

        Set<UUID> participantIds = new HashSet<>();
        for (Player p : participants) {
            participantIds.add(p.getUniqueId());
        }

        broadcastVoteProgress(voter.getName(), votes.size(), participantIds.size());

        if (votes.containsAll(participantIds)) {
            clearVotes();
            onAllVoted.run();
            return true;
        }

        if (firstVote) {
            startTimeout();
        }

        return true;
    }

    /** Cancels any pending vote and clears all recorded votes. */
    public void clearVotes() {
        votes.clear();
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    private void broadcastVoteProgress(String voterName, int voteCount, int totalCount) {
        String msg = ChatColor.GOLD + "[DeepCore] " + ChatColor.YELLOW + voterName
                + ChatColor.WHITE + " voted to save the run ("
                + ChatColor.AQUA + voteCount + "/" + totalCount
                + ChatColor.WHITE + "). Use "
                + ChatColor.YELLOW + "/challenge saverun"
                + ChatColor.WHITE + " to vote.";
        Bukkit.broadcastMessage(msg);
    }

    private void startTimeout() {
        long ticks = VOTE_TIMEOUT_SECONDS * 20L;
        timeoutTask = Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (!votes.isEmpty()) {
                                votes.clear();
                                timeoutTask = null;
                                Bukkit.broadcastMessage(ChatColor.GOLD + "[DeepCore] " + ChatColor.RED
                                        + "Save vote expired — not all participants voted in time.");
                            }
                        },
                        ticks);
    }
}
