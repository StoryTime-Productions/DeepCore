package dev.deepcore.challenge.session;

/** Tracks run start and paused-duration timing values for a session. */
public final class SessionTimingState {
    private long runStartMillis;
    private long pausedStartedMillis;
    private long accumulatedPausedMillis;

    /**
     * Restores timing state from a persistent snapshot, advancing pause
     * accumulation to account for lobby time since the run was saved.
     *
     * @param runStartMs         original run start timestamp in milliseconds
     * @param accumulatedPausedMs total paused duration at the time the run was saved
     * @param savedAtMs          wall-clock timestamp when the run was saved
     */
    public void restore(long runStartMs, long accumulatedPausedMs, long savedAtMs) {
        this.runStartMillis = runStartMs;
        this.accumulatedPausedMillis = accumulatedPausedMs + Math.max(0L, System.currentTimeMillis() - savedAtMs);
        this.pausedStartedMillis = 0L;
    }

    /** Resets all tracked timing values to zero. */
    public void reset() {
        runStartMillis = 0L;
        pausedStartedMillis = 0L;
        accumulatedPausedMillis = 0L;
    }

    /**
     * Starts run timing at the provided millisecond timestamp.
     *
     * @param startedAtMillis timestamp in milliseconds when the run began
     */
    public void beginRun(long startedAtMillis) {
        runStartMillis = startedAtMillis;
        pausedStartedMillis = 0L;
        accumulatedPausedMillis = 0L;
    }

    /**
     * Marks the timestamp when pause started.
     *
     * @param pausedAtMillis timestamp in milliseconds when pause began
     */
    public void beginPause(long pausedAtMillis) {
        pausedStartedMillis = pausedAtMillis;
    }

    /**
     * Resumes run timing and accumulates elapsed paused duration.
     *
     * @param resumedAtMillis timestamp in milliseconds when run resumed
     */
    public void resume(long resumedAtMillis) {
        if (pausedStartedMillis > 0L) {
            accumulatedPausedMillis += Math.max(0L, resumedAtMillis - pausedStartedMillis);
        }
        pausedStartedMillis = 0L;
    }

    /**
     * Returns run start timestamp in milliseconds.
     *
     * @return run start epoch timestamp in milliseconds
     */
    public long getRunStartMillis() {
        return runStartMillis;
    }

    /**
     * Returns pause start timestamp in milliseconds.
     *
     * @return pause start epoch timestamp in milliseconds, or zero when not paused
     */
    public long getPausedStartedMillis() {
        return pausedStartedMillis;
    }

    /**
     * Returns total accumulated paused duration in milliseconds.
     *
     * @return total paused duration accumulated across all pause intervals
     */
    public long getAccumulatedPausedMillis() {
        return accumulatedPausedMillis;
    }
}
