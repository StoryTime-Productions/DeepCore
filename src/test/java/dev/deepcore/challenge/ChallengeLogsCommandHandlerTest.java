package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.logging.DeepCoreLogLevel;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ChallengeLogsCommandHandlerTest {

    private DeepCoreLogger logger;
    private ChallengeLogsCommandHandler handler;

    @BeforeEach
    void setUp() {
        logger = mock(DeepCoreLogger.class);
        handler = new ChallengeLogsCommandHandler(logger);
    }

    @Test
    void handle_statusForConsole_showsUsageHint() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(handler.handle(sender, new String[] {"logs", "status"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("requires a player"));
    }

    @Test
    void handle_setSelf_invalidLevel_reportsError() {
        Player player = mock(Player.class);

        assertTrue(handler.handle(player, new String[] {"logs", "set", "nope"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Invalid level"));
    }

    @Test
    void handle_setSelf_validLevel_updatesLoggerPreference() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        assertTrue(handler.handle(player, new String[] {"logs", "set", "warn"}));

        verify(logger).setPlayerLevel(uuid, DeepCoreLogLevel.WARN);
    }

    @Test
    void handle_clearSelf_clearsPreference() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        assertTrue(handler.handle(player, new String[] {"logs", "clear", "self"}));

        verify(logger).clearPlayerLevel(uuid);
    }

    @Test
    void tabComplete_logsTopLevelReturnsActions() {
        CommandSender sender = mock(CommandSender.class);

        List<String> completions = handler.tabComplete(sender, new String[] {"logs", "s"});

        assertTrue(completions.contains("status"));
        assertTrue(completions.contains("set"));
    }

    @Test
    void handle_setByConsoleWithoutPlayer_reportsUsage() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(handler.handle(sender, new String[] {"logs", "set", "warn"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Console must specify a player"));
    }

    @Test
    void handle_clearByConsoleWithoutPlayer_reportsUsage() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(handler.handle(sender, new String[] {"logs", "clear"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Console must specify a player"));
    }

    @Test
    void handle_setAnotherPlayer_withoutAdminPermission_denies() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"logs", "set", "Steve", "warn"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("do not have permission"));
    }

    @Test
    void tabComplete_nonAdminSetLevel_suggestsOnlyLevels() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(false);

        List<String> completions = handler.tabComplete(sender, new String[] {"logs", "set", "w"});

        assertTrue(completions.contains("warn"));
    }

    @Test
    void handle_unknownAction_showsUsage() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(handler.handle(sender, new String[] {"logs", "mystery"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Usage: /challenge logs"));
        verify(logger, never()).setPlayerLevel(any(), any());
    }

    @Test
    void handle_statusForPlayer_displaysConfiguredAndEffectiveLevels() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        when(logger.getConfiguredPlayerLevel(uuid)).thenReturn(DeepCoreLogLevel.DEBUG);
        when(logger.getEffectivePlayerLevel(player)).thenReturn(DeepCoreLogLevel.DEBUG);

        assertTrue(handler.handle(player, new String[] {"logs", "status"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Log preferences for"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Configured:"));
        verify(logger).sendInfo(any(CommandSender.class), contains("Effective:"));
    }

    @Test
    void handle_setMissingArgs_showsUsage() {
        CommandSender sender = mock(CommandSender.class);

        assertTrue(handler.handle(sender, new String[] {"logs", "set"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("Usage: /challenge logs set <level>"));
    }

    @Test
    void handle_setAnotherPlayer_notFound_reportsError() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Ghost")).thenReturn(null);

            assertTrue(handler.handle(sender, new String[] {"logs", "set", "Ghost", "warn"}));
        }

        verify(logger).sendInfo(any(CommandSender.class), contains("Player not found"));
    }

    @Test
    void handle_setAnotherPlayer_valid_updatesTarget() {
        CommandSender sender = mock(CommandSender.class);
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Steve");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Steve")).thenReturn(target);

            assertTrue(handler.handle(sender, new String[] {"logs", "set", "Steve", "error"}));
        }

        verify(logger).setPlayerLevel(targetId, DeepCoreLogLevel.ERROR);
    }

    @Test
    void handle_clearAnotherPlayer_withoutAdminPermission_denies() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(false);

        assertTrue(handler.handle(sender, new String[] {"logs", "clear", "Steve"}));

        verify(logger).sendInfo(any(CommandSender.class), contains("do not have permission"));
    }

    @Test
    void handle_clearAnotherPlayer_valid_clearsTarget() {
        CommandSender sender = mock(CommandSender.class);
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Steve");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Steve")).thenReturn(target);

            assertTrue(handler.handle(sender, new String[] {"logs", "clear", "Steve"}));
        }

        verify(logger).clearPlayerLevel(targetId);
    }

    @Test
    void tabComplete_adminSetIncludesPlayersAndLevels() {
        CommandSender sender = mock(CommandSender.class);
        Player online = mock(Player.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(true);
        when(online.getName()).thenReturn("Steve");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(online));

            List<String> completions = handler.tabComplete(sender, new String[] {"logs", "set", "s"});
            assertTrue(completions.stream().anyMatch(value -> value.equalsIgnoreCase("steve")));
        }
    }

    @Test
    void tabComplete_setFourthArgFiltersByLevelPrefix() {
        CommandSender sender = mock(CommandSender.class);

        List<String> completions = handler.tabComplete(sender, new String[] {"logs", "set", "Steve", "w"});

        assertTrue(completions.contains("warn"));
    }

    @Test
    void tabComplete_adminClearIncludesSelfAndOnlinePlayers() {
        CommandSender sender = mock(CommandSender.class);
        Player online = mock(Player.class);
        when(sender.hasPermission("deepcore.challenge.logs.admin")).thenReturn(true);
        when(online.getName()).thenReturn("Alex");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(online));

            List<String> completions = handler.tabComplete(sender, new String[] {"logs", "clear", "a"});
            assertTrue(completions.stream().anyMatch(value -> value.equalsIgnoreCase("alex")));
        }
    }
}
