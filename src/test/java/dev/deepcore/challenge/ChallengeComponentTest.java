package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChallengeComponentTest {

    @Test
    void fromKey_isCaseInsensitiveForValidKey() {
        assertTrue(ChallengeComponent.fromKey("HARDCORE").isPresent());
        assertEquals(
                ChallengeComponent.HARDCORE,
                ChallengeComponent.fromKey("HARDCORE").orElseThrow());
    }

    @Test
    void fromKey_returnsEmptyForUnknownKey() {
        assertFalse(ChallengeComponent.fromKey("not_a_component").isPresent());
    }

    @Test
    void keysAndDisplayNames_areExposed() {
        assertEquals("shared_health", ChallengeComponent.SHARED_HEALTH.key());
        assertEquals("Shared health pool", ChallengeComponent.SHARED_HEALTH.displayName());
    }
}
