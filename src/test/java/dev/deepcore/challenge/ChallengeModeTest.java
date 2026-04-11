package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeModeTest {

    @Test
    void fromKey_resolvesCanonicalAndLegacyKeys() {
        assertEquals(
                ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS,
                ChallengeMode.fromKey("keep_inventory_non_hardcore").orElseThrow());
        assertEquals(
                ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS,
                ChallengeMode.fromKey("keep_inventory_unlimited_deaths").orElseThrow());
        assertEquals(
                ChallengeMode.LOSE_INVENTORY_UNLIMITED_DEATHS_SHARED_INVENTORY,
                ChallengeMode.fromKey("lose_inventory_unlimited_deaths_shared_inventory")
                        .orElseThrow());
    }

    @Test
    void fromKey_returnsEmptyForUnknownOrNull() {
        assertFalse(ChallengeMode.fromKey("unknown_mode").isPresent());
        assertFalse(ChallengeMode.fromKey(null).isPresent());
    }

    @Test
    void defaultComponents_returnsDefensiveCopy() {
        Set<ChallengeComponent> defaults =
                ChallengeMode.HARDCORE_SHARED_HEALTH_NO_REFILL_DEGRADING_INVENTORY_INITIAL_HALF_HEART
                        .defaultComponents();
        assertTrue(defaults.contains(ChallengeComponent.HARDCORE));
        assertTrue(defaults.contains(ChallengeComponent.SHARED_HEALTH));

        defaults.clear();

        assertTrue(ChallengeMode.HARDCORE_SHARED_HEALTH_NO_REFILL_DEGRADING_INVENTORY_INITIAL_HALF_HEART
                .defaultComponents()
                .contains(ChallengeComponent.HARDCORE));
    }
}
