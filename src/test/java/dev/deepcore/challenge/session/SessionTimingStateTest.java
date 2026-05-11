package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionTimingStateTest {

    @Test
    void restoreAndResume_accumulatesPausedMillis() throws InterruptedException {
        SessionTimingState state = new SessionTimingState();

        long now = System.currentTimeMillis();
        long runStartMs = now - 5000L;
        long accumulatedPausedMs = 200L;
        long savedAtMs = now - 1000L;

        state.restore(runStartMs, accumulatedPausedMs, savedAtMs);

        assertEquals(runStartMs, state.getRunStartMillis());
        assertTrue(state.getAccumulatedPausedMillis() >= accumulatedPausedMs + 1000L - 20L);

        state.beginPause(System.currentTimeMillis());
        Thread.sleep(5);
        long resumeAt = System.currentTimeMillis();
        state.resume(resumeAt);
        assertEquals(0L, state.getPausedStartedMillis());
        assertTrue(state.getAccumulatedPausedMillis() >= accumulatedPausedMs);
    }

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
