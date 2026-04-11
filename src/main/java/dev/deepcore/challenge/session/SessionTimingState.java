package dev.deepcore.challenge.session;

/** Tracks run start and paused-duration timing values for a session. */
public final class SessionTimingState {
    private long runStartMillis;
    private long pausedStartedMillis;
    private long accumulatedPausedMillis;

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
