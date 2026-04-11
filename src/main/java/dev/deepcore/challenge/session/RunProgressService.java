package dev.deepcore.challenge.session;

import java.util.List;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks run milestone progress and computes split durations for record
 * storage.
 */
public final class RunProgressService {
    private boolean reachedNether;
    private long netherReachedMillis;
    private boolean reachedBlazeObjective;
    private long blazeObjectiveReachedMillis;
    private boolean reachedEnd;
    private long endReachedMillis;
    private boolean dragonKilled;
    private long dragonKilledMillis;

    /** Resets all tracked run milestones. */
    public void reset() {
        reachedNether = false;
        netherReachedMillis = 0L;
        reachedBlazeObjective = false;
        blazeObjectiveReachedMillis = 0L;
        reachedEnd = false;
        endReachedMillis = 0L;
        dragonKilled = false;
        dragonKilledMillis = 0L;
    }

    /**
     * Returns whether any participant has reached the Nether.
     *
     * @return true when the Nether milestone has been recorded
     */
    public boolean hasReachedNether() {
        return reachedNether;
    }

    /**
     * Returns whether any participant has reached the End.
     *
     * @return true when the End milestone has been recorded
     */
    public boolean hasReachedEnd() {
        return reachedEnd;
    }

    /**
     * Returns whether the dragon kill milestone has been reached.
     *
     * @return true when the dragon kill milestone has been recorded
     */
    public boolean isDragonKilled() {
        return dragonKilled;
    }

    /**
     * Resolves the timestamp to use for elapsed-time rendering.
     *
     * @param currentTimeMillis current wall-clock timestamp in milliseconds
     * @return timestamp to use for elapsed-time calculations
     */
    public long resolveElapsedReferenceTime(long currentTimeMillis) {
        if (dragonKilled && dragonKilledMillis > 0L) {
            return dragonKilledMillis;
        }
        return currentTimeMillis;
    }

    /**
     * Marks the Nether milestone if it has not already been recorded.
     *
     * @param timestampMillis timestamp in milliseconds when the milestone was
     *                        reached
     * @return true when the milestone was newly recorded
     */
    public boolean markNetherReached(long timestampMillis) {
        if (reachedNether) {
            return false;
        }
        reachedNether = true;
        netherReachedMillis = timestampMillis;
        return true;
    }

    /**
     * Marks the End milestone if it has not already been recorded.
     *
     * @param timestampMillis timestamp in milliseconds when the milestone was
     *                        reached
     * @return true when the milestone was newly recorded
     */
    public boolean markEndReached(long timestampMillis) {
        if (reachedEnd) {
            return false;
        }
        reachedEnd = true;
        endReachedMillis = timestampMillis;
        return true;
    }

    /**
     * Marks the dragon kill milestone timestamp.
     *
     * @param timestampMillis timestamp in milliseconds when the dragon was killed
     */
    public void markDragonKilled(long timestampMillis) {
        dragonKilled = true;
        dragonKilledMillis = timestampMillis;
    }

    /**
     * Marks the blaze-rod milestone once the threshold is met during a run.
     *
     * @param runningPhase      whether the challenge is currently in the running
     *                          phase
     * @param teamBlazeRodCount total blaze rods currently held across participants
     * @param timestampMillis   timestamp in milliseconds for this progress sample
     * @return split duration from Nether entry to blaze objective completion when
     *         newly reached
     */
    public OptionalLong maybeMarkBlazeObjectiveReached(
            boolean runningPhase, int teamBlazeRodCount, long timestampMillis) {
        if (!runningPhase || reachedBlazeObjective || !reachedNether) {
            return OptionalLong.empty();
        }
        if (teamBlazeRodCount < 6) {
            return OptionalLong.empty();
        }

        reachedBlazeObjective = true;
        blazeObjectiveReachedMillis = timestampMillis;
        return OptionalLong.of(Math.max(0L, blazeObjectiveReachedMillis - netherReachedMillis));
    }

    /**
     * Builds the current objective label for run HUD/status output.
     *
     * @param teamBlazeRodCount total blaze rods currently held across participants
     * @return objective text representing the next run milestone
     */
    public String currentObjectiveText(int teamBlazeRodCount) {
        if (dragonKilled) {
            return "Challenge Complete";
        }
        if (reachedEnd) {
            return "Kill the Ender Dragon";
        }
        if (!reachedBlazeObjective) {
            if (!reachedNether) {
                return "Enter the Nether";
            }
            return "Collect Blaze Rods (" + teamBlazeRodCount + "/6)";
        }
        return "Enter the End";
    }

    /**
     * Captures a lightweight immutable progress snapshot for UI rendering.
     *
     * @param teamBlazeRodCount total blaze rods currently held across participants
     * @return immutable progress snapshot for action-bar and sidebar rendering
     */
    public RunProgressSnapshot snapshotForDisplay(int teamBlazeRodCount) {
        return new RunProgressSnapshot(
                currentObjectiveText(teamBlazeRodCount),
                reachedNether,
                reachedBlazeObjective,
                reachedEnd,
                dragonKilled);
    }

    /**
     * Counts total blaze rods across the given participant inventories.
     *
     * @param participants participants whose inventories should be counted
     * @return total blaze rod count across all participant inventories
     */
    public int countTeamBlazeRods(List<Player> participants) {
        int total = 0;
        for (Player player : participants) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == Material.BLAZE_ROD) {
                    total += stack.getAmount();
                }
            }
        }
        return total;
    }

    /**
     * Updates Nether/End milestones by inspecting participant world locations.
     *
     * @param participants    participants whose world locations should be inspected
     * @param timestampMillis timestamp in milliseconds for newly reached milestones
     */
    public void updateMilestonesFromParticipants(List<Player> participants, long timestampMillis) {
        if (hasReachedNether() && hasReachedEnd()) {
            return;
        }

        for (Player participant : participants) {
            World.Environment environment = participant.getWorld().getEnvironment();
            if (environment == World.Environment.NETHER) {
                markNetherReached(timestampMillis);
            }
            if (environment == World.Environment.THE_END) {
                markEndReached(timestampMillis);
            }
            if (hasReachedNether() && hasReachedEnd()) {
                return;
            }
        }
    }

    /**
     * Calculates each run split duration, falling back to overall duration when a
     * milestone was not reached.
     *
     * @param runStartMillis         run start timestamp in milliseconds
     * @param dragonDeathTime        dragon death timestamp in milliseconds
     * @param fallbackDurationMillis fallback duration used when a milestone is
     *                               missing
     * @return calculated run section durations for persistence and display
     */
    public SectionDurations calculateSectionDurations(
            long runStartMillis, long dragonDeathTime, long fallbackDurationMillis) {
        long overworldToNetherMs = reachedNether && netherReachedMillis > 0
                ? netherReachedMillis - runStartMillis
                : fallbackDurationMillis;

        long netherToBlazeRodsMs =
                reachedBlazeObjective && blazeObjectiveReachedMillis > 0 && reachedNether && netherReachedMillis > 0
                        ? blazeObjectiveReachedMillis - netherReachedMillis
                        : fallbackDurationMillis;

        long blazeRodsToEndMs =
                reachedEnd && endReachedMillis > 0 && reachedBlazeObjective && blazeObjectiveReachedMillis > 0
                        ? endReachedMillis - blazeObjectiveReachedMillis
                        : fallbackDurationMillis;

        long netherToEndMs = reachedEnd && endReachedMillis > 0 && reachedNether && netherReachedMillis > 0
                ? endReachedMillis - netherReachedMillis
                : fallbackDurationMillis;

        long endToDragonMs =
                reachedEnd && endReachedMillis > 0 ? dragonDeathTime - endReachedMillis : fallbackDurationMillis;

        return new SectionDurations(
                overworldToNetherMs, netherToBlazeRodsMs, blazeRodsToEndMs, netherToEndMs, endToDragonMs);
    }

    /** Immutable split-duration values used for persisted run records. */
    public static final class SectionDurations {
        private final long overworldToNetherMs;
        private final long netherToBlazeRodsMs;
        private final long blazeRodsToEndMs;
        private final long netherToEndMs;
        private final long endToDragonMs;

        SectionDurations(
                long overworldToNetherMs,
                long netherToBlazeRodsMs,
                long blazeRodsToEndMs,
                long netherToEndMs,
                long endToDragonMs) {
            this.overworldToNetherMs = overworldToNetherMs;
            this.netherToBlazeRodsMs = netherToBlazeRodsMs;
            this.blazeRodsToEndMs = blazeRodsToEndMs;
            this.netherToEndMs = netherToEndMs;
            this.endToDragonMs = endToDragonMs;
        }

        /**
         * Returns duration from run start to first Nether entry.
         *
         * @return elapsed duration in milliseconds from start to Nether entry
         */
        public long overworldToNetherMs() {
            return overworldToNetherMs;
        }

        /**
         * Returns duration from Nether entry to blaze objective completion.
         *
         * @return elapsed duration in milliseconds from Nether entry to blaze milestone
         */
        public long netherToBlazeRodsMs() {
            return netherToBlazeRodsMs;
        }

        /**
         * Returns duration from blaze objective completion to End entry.
         *
         * @return elapsed duration in milliseconds from blaze milestone to End entry
         */
        public long blazeRodsToEndMs() {
            return blazeRodsToEndMs;
        }

        /**
         * Returns duration from Nether entry to End entry.
         *
         * @return elapsed duration in milliseconds from Nether entry to End entry
         */
        public long netherToEndMs() {
            return netherToEndMs;
        }

        /**
         * Returns duration from End entry to dragon kill.
         *
         * @return elapsed duration in milliseconds from End entry to dragon kill
         */
        public long endToDragonMs() {
            return endToDragonMs;
        }
    }

    /** Immutable run-progress view model for action-bar/status rendering. */
    public static final class RunProgressSnapshot {
        private final String objectiveText;
        private final boolean reachedNether;
        private final boolean reachedBlazeObjective;
        private final boolean reachedEnd;
        private final boolean dragonKilled;

        RunProgressSnapshot(
                String objectiveText,
                boolean reachedNether,
                boolean reachedBlazeObjective,
                boolean reachedEnd,
                boolean dragonKilled) {
            this.objectiveText = objectiveText;
            this.reachedNether = reachedNether;
            this.reachedBlazeObjective = reachedBlazeObjective;
            this.reachedEnd = reachedEnd;
            this.dragonKilled = dragonKilled;
        }

        /**
         * Returns the current objective text.
         *
         * @return objective label describing the next progress goal
         */
        public String objectiveText() {
            return objectiveText;
        }

        /**
         * Returns whether Nether milestone has been reached.
         *
         * @return true when the Nether milestone is complete
         */
        public boolean reachedNether() {
            return reachedNether;
        }

        /**
         * Returns whether blaze milestone has been reached.
         *
         * @return true when the blaze objective milestone is complete
         */
        public boolean reachedBlazeObjective() {
            return reachedBlazeObjective;
        }

        /**
         * Returns whether End milestone has been reached.
         *
         * @return true when the End milestone is complete
         */
        public boolean reachedEnd() {
            return reachedEnd;
        }

        /**
         * Returns whether dragon kill milestone has been reached.
         *
         * @return true when the dragon kill milestone is complete
         */
        public boolean dragonKilled() {
            return dragonKilled;
        }
    }
}
