package dev.deepcore.challenge.session;

import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Coordinates run progress sampling and action-bar status rendering.
 */
public final class RunStatusService {
    private final RunProgressService runProgressService;
    private final RunUiFormattingService runUiFormattingService;
    private final DeepCoreLogger log;
    private int lastObservedTeamBlazeRodCount;

    /**
     * Creates a run status service.
     *
     * @param runProgressService     run milestone progress tracker
     * @param runUiFormattingService run time/label formatting service
     * @param log                    challenge logger for player/admin messaging
     */
    public RunStatusService(
            RunProgressService runProgressService, RunUiFormattingService runUiFormattingService, DeepCoreLogger log) {
        this.runProgressService = runProgressService;
        this.runUiFormattingService = runUiFormattingService;
        this.log = log;
    }

    /** Resets run-status transient tracking values. */
    public void reset() {
        lastObservedTeamBlazeRodCount = 0;
    }

    /**
     * Updates run progress from a participant world-change event.
     *
     * @param environment        world environment entered by the participant
     * @param onlineParticipants currently online challenge participants
     * @param now                current timestamp in milliseconds
     * @param runningPhase       whether the session is actively running
     */
    public void onParticipantWorldChanged(
            World.Environment environment, List<Player> onlineParticipants, long now, boolean runningPhase) {
        if (environment == World.Environment.NETHER) {
            runProgressService.markNetherReached(now);
        }
        if (environment == World.Environment.THE_END) {
            runProgressService.markEndReached(now);
        }

        lastObservedTeamBlazeRodCount = runProgressService.countTeamBlazeRods(onlineParticipants);
        maybeMarkBlazeObjectiveReached(now, runningPhase);
    }

    /**
     * Samples participant progress and updates milestone tracking for the current
     * tick.
     *
     * @param onlineParticipants currently online challenge participants
     * @param now                current timestamp in milliseconds
     * @param runningPhase       whether the session is actively running
     */
    public void tickProgressFromParticipants(List<Player> onlineParticipants, long now, boolean runningPhase) {
        lastObservedTeamBlazeRodCount = runProgressService.countTeamBlazeRods(onlineParticipants);
        runProgressService.updateMilestonesFromParticipants(onlineParticipants, now);
        maybeMarkBlazeObjectiveReached(now, runningPhase);
    }

    /**
     * Builds the current run action-bar message for participants.
     *
     * @param runStartMillis          run start timestamp in milliseconds
     * @param accumulatedPausedMillis total paused duration in milliseconds
     * @param pausedPhase             whether the session is currently paused
     * @param pausedStartedMillis     current pause start timestamp in milliseconds
     * @return action-bar component with objective and elapsed time text
     */
    public Component buildRunActionBarMessage(
            long runStartMillis, long accumulatedPausedMillis, boolean pausedPhase, long pausedStartedMillis) {
        RunProgressService.RunProgressSnapshot snapshot =
                runProgressService.snapshotForDisplay(lastObservedTeamBlazeRodCount);

        long now = runProgressService.resolveElapsedReferenceTime(System.currentTimeMillis());
        String elapsed = runUiFormattingService.formatElapsedTime(
                runStartMillis, now, accumulatedPausedMillis, pausedPhase, pausedStartedMillis);

        return runUiFormattingService.buildRunActionBarMessage(snapshot.objectiveText(), elapsed);
    }

    private void maybeMarkBlazeObjectiveReached(long timestampMillis, boolean runningPhase) {
        runProgressService
                .maybeMarkBlazeObjectiveReached(runningPhase, lastObservedTeamBlazeRodCount, timestampMillis)
                .ifPresent(splitMs -> log.info("Objective complete: Collect 6 Blaze Rods (split: "
                        + runUiFormattingService.formatSplitDuration(splitMs)
                        + ")"));
    }
}
