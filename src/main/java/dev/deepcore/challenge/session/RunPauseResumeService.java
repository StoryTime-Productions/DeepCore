package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Coordinates pause/resume lifecycle transitions for an active run.
 */
public final class RunPauseResumeService {
    private final SessionState sessionState;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final DeepCoreLogger log;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Runnable clearPausedSnapshots;
    private final Consumer<Player> stashRunStateForLobby;
    private final PausedRunStateService pausedRunStateService;
    private final ActionBarTickerService actionBarTickerService;
    private final TaskGroup taskGroup;
    private final String degradingTaskKey;
    private final Runnable clearActionBar;
    private final PrepAreaService prepAreaService;
    private final ChallengeManager challengeManager;
    private final Runnable startActionBarTask;
    private final Runnable resumeDegradingInventoryTask;

    /**
     * Creates a run pause/resume service.
     *
     * @param sessionState                 session phase and timing state
     * @param worldResetManagerSupplier    supplier for world reset manager
     * @param log                          logger used for pause/resume feedback
     * @param onlineParticipantsSupplier   supplier for online run participants
     * @param clearPausedSnapshots         action to clear paused-run snapshots
     * @param stashRunStateForLobby        action that snapshots a player's run
     *                                     state for lobby transfer
     * @param pausedRunStateService        paused-run snapshot state service
     * @param actionBarTickerService       action-bar ticker service
     * @param taskGroup                    task group managing scheduled run tasks
     * @param degradingTaskKey             task key for degrading inventory ticker
     * @param clearActionBar               action to clear all action bars
     * @param prepAreaService              prep border service
     * @param challengeManager             challenge manager for component checks
     * @param startActionBarTask           action that starts action-bar ticking
     * @param resumeDegradingInventoryTask action that restarts degrading inventory
     *                                     ticker
     */
    public RunPauseResumeService(
            SessionState sessionState,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            DeepCoreLogger log,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Runnable clearPausedSnapshots,
            Consumer<Player> stashRunStateForLobby,
            PausedRunStateService pausedRunStateService,
            ActionBarTickerService actionBarTickerService,
            TaskGroup taskGroup,
            String degradingTaskKey,
            Runnable clearActionBar,
            PrepAreaService prepAreaService,
            ChallengeManager challengeManager,
            Runnable startActionBarTask,
            Runnable resumeDegradingInventoryTask) {
        this.sessionState = sessionState;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.log = log;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.clearPausedSnapshots = clearPausedSnapshots;
        this.stashRunStateForLobby = stashRunStateForLobby;
        this.pausedRunStateService = pausedRunStateService;
        this.actionBarTickerService = actionBarTickerService;
        this.taskGroup = taskGroup;
        this.degradingTaskKey = degradingTaskKey;
        this.clearActionBar = clearActionBar;
        this.prepAreaService = prepAreaService;
        this.challengeManager = challengeManager;
        this.startActionBarTask = startActionBarTask;
        this.resumeDegradingInventoryTask = resumeDegradingInventoryTask;
    }

    /**
     * Pauses the active run and snapshots participant state when allowed.
     *
     * @param sender            command sender requesting pause
     * @param announceBroadcast true to log and broadcast pause initiation
     * @return true when pause transition was applied
     */
    public boolean pause(CommandSender sender, boolean announceBroadcast) {
        if (!canPause(sender)) {
            return false;
        }

        snapshotParticipantsForPause();
        transitionToPausedPhase();
        if (announceBroadcast) {
            log.info("Run paused by " + sender.getName());
        }
        return true;
    }

    /**
     * Resumes a paused run and restores participant state and tasks.
     *
     * @param sender command sender requesting resume
     * @return true when resume transition was applied
     */
    public boolean resume(CommandSender sender) {
        if (!sessionState.is(SessionState.Phase.PAUSED)) {
            return false;
        }

        transitionToRunningFromPause();
        restoreParticipantSnapshotsAfterResume();
        restartRunTasksAfterResume();

        log.info("Run resumed by " + sender.getName());
        return true;
    }

    private boolean canPause(CommandSender sender) {
        if (!sessionState.is(SessionState.Phase.RUNNING)) {
            return false;
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            log.sendError(sender, "Pause failed: world reset manager is unavailable.");
            return false;
        }

        Location lobbySpawn = worldResetManager.getConfiguredLimboSpawn();
        if (lobbySpawn == null) {
            log.sendError(sender, "Pause failed: lobby spawn is not available.");
            return false;
        }

        return true;
    }

    private void snapshotParticipantsForPause() {
        clearPausedSnapshots.run();
        for (Player participant : onlineParticipantsSupplier.get()) {
            stashRunStateForLobby.accept(participant);
            log.sendInfo(participant, "Challenge paused. Your run state has been snapshotted.");
        }
    }

    private void transitionToPausedPhase() {
        sessionState.setPhase(SessionState.Phase.PAUSED);
        sessionState.timing().beginPause(System.currentTimeMillis());
        actionBarTickerService.stop();
        taskGroup.cancel(degradingTaskKey);
        clearActionBar.run();
        prepAreaService.clearBorders();
    }

    private void transitionToRunningFromPause() {
        long now = System.currentTimeMillis();
        sessionState.timing().resume(now);
        sessionState.setPhase(SessionState.Phase.RUNNING);
    }

    private void restoreParticipantSnapshotsAfterResume() {
        for (Player participant : onlineParticipantsSupplier.get()) {
            pausedRunStateService.applySnapshotIfPresent(participant);
            log.sendInfo(participant, "Challenge resumed.");
        }

        clearPausedSnapshots.run();
    }

    private void restartRunTasksAfterResume() {
        startActionBarTask.run();
        if (challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
            resumeDegradingInventoryTask.run();
        }
    }
}
