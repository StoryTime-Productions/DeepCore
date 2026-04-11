package dev.deepcore.challenge.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SessionRulesCoordinatorServiceTest {

    @Test
    void syncWorldRules_appliesKeepInventoryAndLobbyPoliciesWhenAvailable() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        when(challengeManager.isEnabled()).thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.KEEP_INVENTORY))
                .thenReturn(true);

        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        RunHealthCoordinatorService healthService = mock(RunHealthCoordinatorService.class);

        SessionRulesCoordinatorService service =
                new SessionRulesCoordinatorService(challengeManager, () -> worldResetManager, () -> healthService);

        World a = mock(World.class);
        World b = mock(World.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(a, b));
            service.syncWorldRules();
        }

        verify(a).setGameRule(GameRule.KEEP_INVENTORY, true);
        verify(b).setGameRule(GameRule.KEEP_INVENTORY, true);
        verify(worldResetManager).enforceLobbyWorldPolicies();
    }

    @Test
    void syncWorldRules_setsFalseWhenFeatureDisabled_andHandlesMissingResetManager() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        when(challengeManager.isEnabled()).thenReturn(false);

        SessionRulesCoordinatorService service =
                new SessionRulesCoordinatorService(challengeManager, () -> null, () -> null);

        World world = mock(World.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            service.syncWorldRules();
        }

        verify(world).setGameRule(GameRule.KEEP_INVENTORY, false);
    }

    @Test
    void applySharedVitalsIfEnabled_onlyInvokesWhenHealthServicePresent() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        RunHealthCoordinatorService presentHealthService = mock(RunHealthCoordinatorService.class);

        SessionRulesCoordinatorService withService =
                new SessionRulesCoordinatorService(challengeManager, () -> null, () -> presentHealthService);
        withService.applySharedVitalsIfEnabled();
        verify(presentHealthService).applySharedVitalsIfEnabled();

        SessionRulesCoordinatorService withoutService =
                new SessionRulesCoordinatorService(challengeManager, () -> null, () -> null);
        withoutService.applySharedVitalsIfEnabled();
    }
}
