package dev.deepcore.challenge.session;

import java.util.Locale;

/** Holds current challenge session phase and shared timing state. */
public final class SessionState {
    /** Represents the top-level lifecycle phases of a challenge session. */
    public enum Phase {
        PREP,
        COUNTDOWN,
        PAUSED,
        RUNNING
    }

    private Phase phase = Phase.PREP;
    private final SessionTimingState timing = new SessionTimingState();

    /**
     * Returns the current session phase.
     *
     * @return active lifecycle phase for the current challenge session
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * Sets the current session phase.
     *
     * @param phase lifecycle phase to set as current
     */
    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    /**
     * Returns whether the current session phase matches the supplied phase.
     *
     * @param phase lifecycle phase to compare against the current phase
     * @return true when the session is currently in the supplied phase
     */
    public boolean is(Phase phase) {
        return this.phase == phase;
    }

    /**
     * Returns the current phase name as a lowercase string.
     *
     * @return lowercase phase name for UI and logging output
     */
    public String phaseNameLowercase() {
        return phase.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns mutable timing state for the current session lifecycle.
     *
     * @return timing state object backing run and pause timestamps
     */
    public SessionTimingState timing() {
        return timing;
    }
}
