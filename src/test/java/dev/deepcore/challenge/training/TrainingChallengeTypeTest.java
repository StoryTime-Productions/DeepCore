package dev.deepcore.challenge.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrainingChallengeTypeTest {

    @Test
    void fromKey_isCaseInsensitiveForKnownValues() {
        assertTrue(TrainingChallengeType.fromKey("portal").isPresent());
        assertTrue(TrainingChallengeType.fromKey("CRAFT").isPresent());
        assertTrue(TrainingChallengeType.fromKey("Chest").isPresent());
        assertTrue(TrainingChallengeType.fromKey("bRiDgE").isPresent());
    }

    @Test
    void fromKey_returnsEmptyForUnknownValue() {
        assertFalse(TrainingChallengeType.fromKey("unknown").isPresent());
    }

    @Test
    void keyAndDisplayName_matchConfiguredValues() {
        assertEquals("portal", TrainingChallengeType.PORTAL.key());
        assertEquals("Portal", TrainingChallengeType.PORTAL.displayName());

        assertEquals("craft", TrainingChallengeType.CRAFT.key());
        assertEquals("Craft", TrainingChallengeType.CRAFT.displayName());

        assertEquals("chest", TrainingChallengeType.CHEST.key());
        assertEquals("Chest", TrainingChallengeType.CHEST.displayName());

        assertEquals("bridge", TrainingChallengeType.BRIDGE.key());
        assertEquals("Bridge", TrainingChallengeType.BRIDGE.displayName());
    }
}
