package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeAdminFacadeTest {

    private DeepCorePlugin plugin;
    private ChallengeManager challengeManager;
    private ChallengeSessionManager sessionManager;
    private WorldResetManager worldResetManager;
    private DeepCoreLogger logger;
    private ChallengeAdminFacade facade;

    @BeforeEach
    void setUp() {
        plugin = mock(DeepCorePlugin.class);
        challengeManager = mock(ChallengeManager.class);
        sessionManager = mock(ChallengeSessionManager.class);
        worldResetManager = mock(WorldResetManager.class);
        logger = mock(DeepCoreLogger.class);
        facade = new ChallengeAdminFacade(plugin, challengeManager, sessionManager, worldResetManager, logger);
    }

    @Test
    void setEnabledAndSetMode_reapplyRulesAndVitals() {
        facade.setEnabled(true);
        facade.setMode(ChallengeMode.HARDCORE_NO_REFILL);

        verify(challengeManager).setEnabled(true);
        verify(challengeManager).setMode(ChallengeMode.HARDCORE_NO_REFILL);
        verify(sessionManager, times(2)).syncWorldRules();
        verify(sessionManager).applySharedVitalsIfEnabled();
    }

    @Test
    void applyComponentOperation_supportsOnOffToggle_andRejectsInvalid() {
        facade.applyComponentOperation(ChallengeComponent.SHARED_HEALTH, "on");
        facade.applyComponentOperation(ChallengeComponent.SHARED_HEALTH, "off");
        facade.applyComponentOperation(ChallengeComponent.SHARED_HEALTH, "toggle");

        verify(challengeManager).setComponentEnabled(ChallengeComponent.SHARED_HEALTH, true);
        verify(challengeManager).setComponentEnabled(ChallengeComponent.SHARED_HEALTH, false);
        verify(challengeManager).toggleComponent(ChallengeComponent.SHARED_HEALTH);

        assertThrows(
                IllegalArgumentException.class,
                () -> facade.applyComponentOperation(ChallengeComponent.SHARED_HEALTH, "bad"));
    }

    @Test
    void stopChallenge_prepAndRunningPaths_behaveDifferently() {
        CommandSender sender = mock(CommandSender.class);

        when(sessionManager.isPrepPhase()).thenReturn(true);
        facade.stopChallenge(sender);
        verify(worldResetManager).resetThreeWorlds(sender);
        verify(worldResetManager, never()).selectRandomLobbyWorld();

        when(sessionManager.isPrepPhase()).thenReturn(false);
        facade.stopChallenge(sender);
        verify(worldResetManager).selectRandomLobbyWorld();
    }

    @Test
    void reloadConfigAndApply_returnsFalseOutsidePrep_andTrueInPrep() {
        when(sessionManager.isPrepPhase()).thenReturn(false, true);

        assertFalse(facade.reloadConfigAndApply());
        assertTrue(facade.reloadConfigAndApply());

        verify(plugin, times(2)).reloadConfig();
        verify(logger, times(2)).loadFromConfig();
        verify(challengeManager).loadFromConfig();
        verify(worldResetManager).ensureThreeWorldsLoaded();
        verify(sessionManager).refreshLobbyPreview();
    }

    @Test
    void pauseResumeAndQueryMethods_delegateToBackingServices() {
        CommandSender sender = mock(CommandSender.class);
        when(sessionManager.pauseChallenge(any())).thenReturn(true);
        when(sessionManager.resumeChallenge(any())).thenReturn(true);

        assertTrue(facade.pauseChallenge(sender));
        assertTrue(facade.resumeChallenge(sender));

        facade.resetWorlds(sender);
        verify(worldResetManager).resetThreeWorlds(sender);
    }
}
