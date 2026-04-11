package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;

/**
 * Applies prep GUI settings mutations with related normalization rules.
 */
public final class PrepSettingsService {
    private final ChallengeManager challengeManager;
    private final Runnable syncWorldRules;
    private final Runnable applySharedVitalsIfEnabled;

    /**
     * Creates a prep settings service.
     *
     * @param challengeManager           challenge manager for component state
     *                                   mutations
     * @param syncWorldRules             action that reapplies world rule state
     * @param applySharedVitalsIfEnabled action that reapplies shared-vitals state
     *                                   when enabled
     */
    public PrepSettingsService(
            ChallengeManager challengeManager, Runnable syncWorldRules, Runnable applySharedVitalsIfEnabled) {
        this.challengeManager = challengeManager;
        this.syncWorldRules = syncWorldRules;
        this.applySharedVitalsIfEnabled = applySharedVitalsIfEnabled;
    }

    /**
     * Toggles a challenge component and applies dependent updates.
     *
     * @param component challenge component to toggle
     */
    public void toggleComponent(ChallengeComponent component) {
        challengeManager.toggleComponent(component);
        syncWorldRules.run();
        applySharedVitalsIfEnabled.run();
    }

    /**
     * Sets health refill state and enforces mutual exclusion with half-heart mode.
     *
     * @param enabled true to enable health refill
     */
    public void setHealthRefill(boolean enabled) {
        challengeManager.setComponentEnabled(ChallengeComponent.HEALTH_REFILL, enabled);
        if (enabled && challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)) {
            challengeManager.setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, false);
        }
        syncWorldRules.run();
        applySharedVitalsIfEnabled.run();
    }

    /**
     * Sets initial-half-heart state and enforces mutual exclusion with health
     * refill.
     *
     * @param enabled true to enable initial-half-heart mode
     */
    public void setInitialHalfHeart(boolean enabled) {
        challengeManager.setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, enabled);
        if (enabled && challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL)) {
            challengeManager.setComponentEnabled(ChallengeComponent.HEALTH_REFILL, false);
        }
        syncWorldRules.run();
        applySharedVitalsIfEnabled.run();
    }
}
