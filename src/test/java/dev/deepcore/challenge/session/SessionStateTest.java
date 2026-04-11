package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionStateTest {

    @Test
    void defaultPhase_isPrep_andPhaseHelpersTrackChanges() {
        SessionState state = new SessionState();

        assertEquals(SessionState.Phase.PREP, state.getPhase());
        assertTrue(state.is(SessionState.Phase.PREP));
        assertEquals("prep", state.phaseNameLowercase());

        state.setPhase(SessionState.Phase.RUNNING);

        assertEquals(SessionState.Phase.RUNNING, state.getPhase());
        assertTrue(state.is(SessionState.Phase.RUNNING));
        assertFalse(state.is(SessionState.Phase.PREP));
        assertEquals("running", state.phaseNameLowercase());
    }

    @Test
    void timing_returnsSharedTimingObject() {
        SessionState state = new SessionState();

        SessionTimingState timing = state.timing();

        assertNotNull(timing);
        timing.beginRun(123L);
        assertEquals(123L, state.timing().getRunStartMillis());
    }
}
