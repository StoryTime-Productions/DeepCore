package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.logging.DeepCoreLogger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeCoreCommandHandlerTest {

    private ChallengeAdminFacade adminFacade;
    private DeepCoreLogger logger;
    private CommandSender sender;
    private ChallengeCoreCommandHandler handler;

    @BeforeEach
    void setUp() {
        adminFacade = mock(ChallengeAdminFacade.class);
        logger = mock(DeepCoreLogger.class);
        sender = mock(CommandSender.class);
        handler = new ChallengeCoreCommandHandler(adminFacade, logger);

        when(adminFacade.getMode()).thenReturn(ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS);
        when(adminFacade.getPhaseName()).thenReturn("prep");
        when(adminFacade.getReadyCount()).thenReturn(1);
        when(adminFacade.getReadyTargetCount()).thenReturn(2);
        when(adminFacade.isChallengeEnabled()).thenReturn(true);

        Map<ChallengeComponent, Boolean> toggles = new EnumMap<>(ChallengeComponent.class);
        for (ChallengeComponent component : ChallengeComponent.values()) {
            toggles.put(component, false);
        }
        when(adminFacade.getComponentToggles()).thenReturn(toggles);
    }

    @Test
    void handle_statusPath_sendsStatus() {
        assertTrue(handler.handle(sender, new String[] {"status"}));

        verify(logger, atLeastOnce()).sendInfo(any(CommandSender.class), contains("Challenge enabled"));
    }

    @Test
    void handle_emptyArgs_sendsStatus() {
        assertTrue(handler.handle(sender, new String[0]));

        verify(logger, atLeastOnce()).sendInfo(any(CommandSender.class), contains("Challenge enabled"));
    }

    @Test
    void handle_enableWhenLocked_doesNotEnable() {
        when(adminFacade.canEditSettings()).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"enable"}));

        verify(adminFacade, never()).setEnabled(true);
        verify(logger).sendInfo(any(CommandSender.class), contains("Settings are locked"));
    }

    @Test
    void handle_modeUnknown_sendsUnknownMessage() {
        when(adminFacade.canEditSettings()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"mode", "bad_mode"}));

        verify(adminFacade, never()).setMode(any());
        verify(logger).sendInfo(any(CommandSender.class), contains("Unknown mode"));
    }

    @Test
    void handle_componentToggle_appliesOperation() {
        when(adminFacade.canEditSettings()).thenReturn(true);
        when(adminFacade.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"component", "shared_health", "on"}));

        verify(adminFacade).applyComponentOperation(ChallengeComponent.SHARED_HEALTH, "on");
    }

    @Test
    void handle_reloadWithoutPermission_denies() {
        when(sender.hasPermission("deepcore.challenge.reload")).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"reload"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("do not have permission"));
    }

    @Test
    void tabComplete_returnsExpectedTopLevelAndModeOptions() {
        List<String> top = handler.tabComplete(new String[] {"re"});
        List<String> mode = handler.tabComplete(new String[] {"mode", "hard"});

        assertTrue(top.contains("reload"));
        assertTrue(mode.stream().anyMatch(s -> s.startsWith("hard")));
    }

    @Test
    void handle_enable_disable_and_mode_valid_applyChanges() {
        when(adminFacade.canEditSettings()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"enable"}));
        assertTrue(handler.handle(sender, new String[] {"disable"}));
        assertTrue(handler.handle(sender, new String[] {"mode", "hardcore_no_refill"}));

        verify(adminFacade).setEnabled(true);
        verify(adminFacade).setEnabled(false);
        verify(adminFacade).setMode(ChallengeMode.HARDCORE_NO_REFILL);
    }

    @Test
    void handle_component_invalidAndReset_branchesAreHandled() {
        when(adminFacade.canEditSettings()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"component", "bad_key", "on"}));
        assertTrue(handler.handle(sender, new String[] {"component", "reset"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Unknown component key"));
        verify(adminFacade).resetComponentsToModeDefaults();
    }

    @Test
    void handle_endStopPauseResume_reset_and_reload_success_paths() {
        when(sender.hasPermission("deepcore.challenge.end")).thenReturn(true);
        when(sender.hasPermission("deepcore.challenge.pause")).thenReturn(true);
        when(sender.hasPermission("deepcore.challenge.reset")).thenReturn(true);
        when(sender.hasPermission("deepcore.challenge.reload")).thenReturn(true);
        when(adminFacade.isPrepPhase()).thenReturn(false);
        when(adminFacade.isRunningPhase()).thenReturn(true);
        when(adminFacade.isPausedPhase()).thenReturn(true);
        when(adminFacade.pauseChallenge(sender)).thenReturn(true);
        when(adminFacade.resumeChallenge(sender)).thenReturn(true);
        when(adminFacade.reloadConfigAndApply()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"end"}));
        assertTrue(handler.handle(sender, new String[] {"stop"}));
        assertTrue(handler.handle(sender, new String[] {"pause"}));
        assertTrue(handler.handle(sender, new String[] {"resume"}));
        assertTrue(handler.handle(sender, new String[] {"reset"}));
        assertTrue(handler.handle(sender, new String[] {"reload"}));

        verify(adminFacade).endChallengeAndReturnToPrep();
        verify(adminFacade).stopChallenge(sender);
        verify(adminFacade).pauseChallenge(sender);
        verify(adminFacade).resumeChallenge(sender);
        verify(adminFacade).resetWorlds(sender);
        verify(adminFacade).reloadConfigAndApply();
    }

    @Test
    void handle_unknownSubcommand_reportsUsage() {
        assertTrue(handler.handle(sender, new String[] {"mystery"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Unknown subcommand"));
    }

    @Test
    void handle_pauseAndResume_failurePaths_reportErrors() {
        when(sender.hasPermission("deepcore.challenge.pause")).thenReturn(true);
        when(adminFacade.isRunningPhase()).thenReturn(true);
        when(adminFacade.pauseChallenge(sender)).thenReturn(false);
        when(adminFacade.isPausedPhase()).thenReturn(true);
        when(adminFacade.resumeChallenge(sender)).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"pause"}));
        assertTrue(handler.handle(sender, new String[] {"resume"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Failed to pause"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Failed to resume"));
    }

    @Test
    void handle_pauseAndResume_invalidPhasePaths_reportStateMessages() {
        when(sender.hasPermission("deepcore.challenge.pause")).thenReturn(true);
        when(adminFacade.isRunningPhase()).thenReturn(false);
        when(adminFacade.isPausedPhase()).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"pause"}));
        assertTrue(handler.handle(sender, new String[] {"resume"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("only be paused while running"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Challenge is not paused"));
        verify(adminFacade, never()).pauseChallenge(sender);
        verify(adminFacade, never()).resumeChallenge(sender);
    }

    @Test
    void handle_endInPrep_reportsAlreadyInPrep() {
        when(sender.hasPermission("deepcore.challenge.end")).thenReturn(true);
        when(adminFacade.isPrepPhase()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"end"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Already in prep mode"));
        verify(adminFacade, never()).endChallengeAndReturnToPrep();
    }

    @Test
    void handle_endStopReset_permissionDeniedPaths() {
        when(sender.hasPermission("deepcore.challenge.end")).thenReturn(false);
        when(sender.hasPermission("deepcore.challenge.reset")).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"end"}));
        assertTrue(handler.handle(sender, new String[] {"stop"}));
        assertTrue(handler.handle(sender, new String[] {"resetworld"}));

        verify(logger, atLeastOnce()).sendInfo(any(CommandSender.class), contains("do not have permission"));
    }

    @Test
    void tabComplete_componentBranches_includeOperationsAndFallbackEmpty() {
        List<String> operations = handler.tabComplete(new String[] {"component", "shared_health", "o"});
        List<String> empty = handler.tabComplete(new String[] {"mode", "x", "y", "z"});

        assertTrue(operations.contains("on"));
        assertTrue(operations.contains("off"));
        assertTrue(empty.isEmpty());
    }

    @Test
    void handle_list_and_modeWithoutArgument_showExpectedMessages() {
        when(adminFacade.canEditSettings()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"list"}));
        assertTrue(handler.handle(sender, new String[] {"mode"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Available challenge modes"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Usage: /challenge mode"));
    }

    @Test
    void handle_componentStatusUsageAndInvalidOperation_paths() {
        when(adminFacade.canEditSettings()).thenReturn(true);

        assertTrue(handler.handle(sender, new String[] {"component"}));
        assertTrue(handler.handle(sender, new String[] {"component", "status"}));
        assertTrue(handler.handle(sender, new String[] {"component", "shared_health"}));
        assertTrue(handler.handle(sender, new String[] {"component", "shared_health", "bad"}));

        verify(logger, atLeastOnce()).sendInfo(any(CommandSender.class), contains("Challenge components"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Usage: /challenge component"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Unknown operation"));
    }

    @Test
    void handle_stopInPrep_andReloadDeferredPaths() {
        when(sender.hasPermission("deepcore.challenge.end")).thenReturn(true);
        when(sender.hasPermission("deepcore.challenge.reload")).thenReturn(true);
        when(adminFacade.isPrepPhase()).thenReturn(true);
        when(adminFacade.reloadConfigAndApply()).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"stop"}));
        assertTrue(handler.handle(sender, new String[] {"reload"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Already in prep mode"));
        verify(adminFacade).stopChallenge(sender);
        verify(logger).sendInfo(any(CommandSender.class), contains("Config file reloaded"));
    }

    @Test
    void tabComplete_componentSecondArg_offersControlTokensAndKeys() {
        List<String> options = handler.tabComplete(new String[] {"component", ""});

        assertTrue(options.contains("list"));
        assertTrue(options.contains("status"));
        assertTrue(options.contains("reset"));
        assertTrue(options.contains("shared_health"));
    }
}
