package dev.deepcore.challenge.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import org.junit.jupiter.api.Test;

class PrepSettingsServiceTest {

    @Test
    void toggleComponent_togglesAndAppliesDependentUpdates() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        Runnable syncWorldRules = mock(Runnable.class);
        Runnable applySharedVitals = mock(Runnable.class);
        PrepSettingsService service = new PrepSettingsService(challengeManager, syncWorldRules, applySharedVitals);

        service.toggleComponent(ChallengeComponent.SHARED_HEALTH);

        verify(challengeManager).toggleComponent(ChallengeComponent.SHARED_HEALTH);
        verify(syncWorldRules).run();
        verify(applySharedVitals).run();
    }

    @Test
    void setHealthRefill_disablesInitialHalfHeartWhenEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        Runnable syncWorldRules = mock(Runnable.class);
        Runnable applySharedVitals = mock(Runnable.class);
        PrepSettingsService service = new PrepSettingsService(challengeManager, syncWorldRules, applySharedVitals);

        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);

        service.setHealthRefill(true);

        verify(challengeManager).setComponentEnabled(ChallengeComponent.HEALTH_REFILL, true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, false);
        verify(syncWorldRules).run();
        verify(applySharedVitals).run();
    }

    @Test
    void setInitialHalfHeart_disablesHealthRefillWhenEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        Runnable syncWorldRules = mock(Runnable.class);
        Runnable applySharedVitals = mock(Runnable.class);
        PrepSettingsService service = new PrepSettingsService(challengeManager, syncWorldRules, applySharedVitals);

        when(challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL))
                .thenReturn(true);

        service.setInitialHalfHeart(true);

        verify(challengeManager).setComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART, true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.HEALTH_REFILL, false);
        verify(syncWorldRules).run();
        verify(applySharedVitals).run();
    }
}
