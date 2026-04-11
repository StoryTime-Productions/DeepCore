package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SessionTimingStateTest {

    @Test
    void beginRunBeginPauseResumeAndReset_updatesTimingFields() {
        SessionTimingState state = new SessionTimingState();

        state.beginRun(1_000L);
        state.beginPause(1_200L);
        state.resume(1_500L);

        assertEquals(1_000L, state.getRunStartMillis());
        assertEquals(0L, state.getPausedStartedMillis());
        assertEquals(300L, state.getAccumulatedPausedMillis());

        state.reset();

        assertEquals(0L, state.getRunStartMillis());
        assertEquals(0L, state.getPausedStartedMillis());
        assertEquals(0L, state.getAccumulatedPausedMillis());
    }

    @Test
    void resume_withoutActivePause_doesNotAccumulateNegativeOrAnyTime() {
        SessionTimingState state = new SessionTimingState();
        state.beginRun(2_000L);

        state.resume(1_500L);

        assertEquals(0L, state.getAccumulatedPausedMillis());
        assertEquals(0L, state.getPausedStartedMillis());
    }
}
