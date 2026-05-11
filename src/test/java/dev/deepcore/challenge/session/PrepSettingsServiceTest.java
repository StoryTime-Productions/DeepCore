package dev.deepcore.challenge.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrepSettingsServiceTest {
    private ChallengeManager challengeManager;
    private Runnable syncWorldRules;
    private Runnable applySharedVitalsIfEnabled;
    private PrepSettingsService service;

    @BeforeEach
    void setup() {
        challengeManager = mock(ChallengeManager.class);
        syncWorldRules = mock(Runnable.class);
        applySharedVitalsIfEnabled = mock(Runnable.class);
        service = new PrepSettingsService(challengeManager, syncWorldRules, applySharedVitalsIfEnabled);
    }

    @Test
    void toggleComponent_callsDependencies() {
        service.toggleComponent(ChallengeComponent.HEALTH_REFILL);
        verify(challengeManager).toggleComponent(ChallengeComponent.HEALTH_REFILL);
        verify(syncWorldRules).run();
        verify(applySharedVitalsIfEnabled).run();
    }

    @Test
    void setHealthRefill_disablesHalfHeartWhenNecessary() {
        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);
        service.setHealthRefill(true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.HEALTH_REFILL, true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, false);
        verify(syncWorldRules).run();
        verify(applySharedVitalsIfEnabled).run();
    }

    @Test
    void setInitialHalfHeart_disablesHealthRefillWhenNecessary() {
        when(challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL))
                .thenReturn(true);
        service.setInitialHalfHeart(true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.HEALTH_REFILL, false);
        verify(syncWorldRules).run();
        verify(applySharedVitalsIfEnabled).run();
    }
}
