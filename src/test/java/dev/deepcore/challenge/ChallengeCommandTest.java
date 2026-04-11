package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeCommandTest {

    private DeepCorePlugin plugin;
    private ChallengeManager challengeManager;
    private ChallengeSessionManager sessionManager;
    private WorldResetManager worldResetManager;
    private DeepCoreLogger logger;
    private CommandSender sender;
    private ChallengeCommand commandHandler;

    @BeforeEach
    void setUp() {
        plugin = mock(DeepCorePlugin.class);
        challengeManager = mock(ChallengeManager.class);
        sessionManager = mock(ChallengeSessionManager.class);
        worldResetManager = mock(WorldResetManager.class);
        logger = mock(DeepCoreLogger.class);
        sender = mock(CommandSender.class);
        when(plugin.getDeepCoreLogger()).thenReturn(logger);

        when(challengeManager.getMode()).thenReturn(ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS);
        when(challengeManager.isEnabled()).thenReturn(true);

        when(sessionManager.getPhaseName()).thenReturn("prep");
        when(sessionManager.getReadyCount()).thenReturn(1);
        when(sessionManager.getReadyTargetCount()).thenReturn(2);
        when(sessionManager.canEditSettings()).thenReturn(false);

        Map<ChallengeComponent, Boolean> toggles = new EnumMap<>(ChallengeComponent.class);
        for (ChallengeComponent component : ChallengeComponent.values()) {
            toggles.put(component, false);
        }
        when(challengeManager.getComponentToggles()).thenReturn(toggles);

        commandHandler = new ChallengeCommand(plugin, challengeManager, sessionManager, worldResetManager);
    }

    @Test
    void onCommand_logsPath_delegatesToLogsHandler() {
        Command command = mock(Command.class);

        assertTrue(commandHandler.onCommand(sender, command, "challenge", new String[] {"logs", "status"}));

        // Console logs status emits guidance through logger.
        org.mockito.Mockito.verify(logger).sendInfo(any(CommandSender.class), contains("requires a player"));
    }

    @Test
    void onCommand_corePath_delegatesToCoreHandler() {
        Command command = mock(Command.class);

        assertTrue(commandHandler.onCommand(sender, command, "challenge", new String[] {"status"}));

        when(sender.hasPermission("deepcore.challenge.reload")).thenReturn(false);
        assertTrue(commandHandler.onCommand(sender, command, "challenge", new String[] {"reload"}));
        assertTrue(commandHandler.onCommand(sender, command, "challenge", new String[] {"component", "list"}));

        // status and permission-denied messages indicate delegate execution.
        org.mockito.Mockito.verify(logger, atLeastOnce()).sendInfo(any(CommandSender.class), contains("Challenge"));
    }

    @Test
    void onTabComplete_logsPath_usesLogsHandler() {
        Command command = mock(Command.class);

        List<String> completion = commandHandler.onTabComplete(sender, command, "challenge", new String[] {"logs", ""});

        org.junit.jupiter.api.Assertions.assertNotNull(completion);
    }

    @Test
    void onTabComplete_corePath_usesCoreHandler() {
        Command command = mock(Command.class);

        List<String> completion = commandHandler.onTabComplete(sender, command, "challenge", new String[] {"sta"});

        org.junit.jupiter.api.Assertions.assertNotNull(completion);
    }
}
