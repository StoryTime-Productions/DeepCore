package dev.deepcore.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DeepCoreLoggerTest {

    private JavaPlugin plugin;
    private YamlConfiguration config;
    private Logger jul;
    private DeepCoreLogger logger;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        jul = mock(Logger.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(jul);
        logger = new DeepCoreLogger(plugin);
    }

    @Test
    void loadFromConfig_appliesConsoleAndPlayerDefaultsAndOverrides() {
        UUID explicitId = UUID.randomUUID();
        config.set("logging.prefix", "&6[DC]&r ");
        config.set("logging.console-min-level", "warn");
        config.set("logging.player-default-op-level", "error");
        config.set("logging.player-default-non-op-level", "warn");
        config.set("logging.player-levels." + explicitId, "debug");

        logger.loadFromConfig();

        Player explicit = mock(Player.class);
        when(explicit.getUniqueId()).thenReturn(explicitId);
        when(explicit.isOp()).thenReturn(false);
        assertEquals(DeepCoreLogLevel.DEBUG, logger.getEffectivePlayerLevel(explicit));

        Player op = mock(Player.class);
        when(op.getUniqueId()).thenReturn(UUID.randomUUID());
        when(op.isOp()).thenReturn(true);
        assertEquals(DeepCoreLogLevel.ERROR, logger.getEffectivePlayerLevel(op));

        Player nonOp = mock(Player.class);
        when(nonOp.getUniqueId()).thenReturn(UUID.randomUUID());
        when(nonOp.isOp()).thenReturn(false);
        assertEquals(DeepCoreLogLevel.WARN, logger.getEffectivePlayerLevel(nonOp));
    }

    @Test
    void setAndClearPlayerLevel_persistsToConfig() {
        UUID id = UUID.randomUUID();

        logger.setPlayerLevel(id, DeepCoreLogLevel.INFO);
        assertEquals(DeepCoreLogLevel.INFO, logger.getConfiguredPlayerLevel(id));
        verify(plugin).saveConfig();

        logger.clearPlayerLevel(id);
        assertNull(logger.getConfiguredPlayerLevel(id));
    }

    @Test
    void send_helpers_formatMessagesForSender() {
        CommandSender sender = mock(CommandSender.class);

        logger.sendInfo(sender, "hello");
        logger.sendWarn(sender, "warn");
        logger.sendError(sender, "err");

        verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("[INFO]"));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("[WARN]"));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("[ERROR]"));
    }

    @Test
    void consoleLogging_respectsConfiguredMinimumLevel() {
        config.set("logging.console-min-level", "warn");
        logger.loadFromConfig();

        logger.infoConsole("info line");
        logger.warnConsole("warn line");
        logger.errorConsole("error line");

        verify(jul, never()).info(anyString());
        verify(jul).warning(org.mockito.ArgumentMatchers.contains("warn line"));
        verify(jul).severe(org.mockito.ArgumentMatchers.contains("error line"));
    }

    @Test
    void send_withNullSender_isNoop() {
        logger.send(null, DeepCoreLogLevel.INFO, "noop");
        assertTrue(true);
    }

    @Test
    void log_broadcastsOnlyToPlayersWhoseEffectiveLevelIncludesMessageLevel() {
        config.set("logging.player-chat-enabled", true);
        config.set("logging.player-default-non-op-level", "error");
        config.set("logging.player-default-op-level", "debug");
        logger.loadFromConfig();

        Player opPlayer = mock(Player.class);
        when(opPlayer.isOp()).thenReturn(true);
        when(opPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        Player strictNonOp = mock(Player.class);
        when(strictNonOp.isOp()).thenReturn(false);
        when(strictNonOp.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(opPlayer, strictNonOp));

            logger.warn("warn to players");
        }

        verify(opPlayer).sendMessage(org.mockito.ArgumentMatchers.contains("[WARN]"));
        verify(strictNonOp, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void log_skipsPlayerBroadcastWhenPlayerChatDisabled() {
        config.set("logging.player-chat-enabled", false);
        logger.loadFromConfig();

        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(player));

            logger.info("console only");
        }

        verify(player, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        verify(jul).info(org.mockito.ArgumentMatchers.contains("console only"));
    }

    @Test
    void errorConsole_withThrowable_printsStackTrace() {
        Throwable throwable = mock(Throwable.class);

        logger.errorConsole("boom", throwable);

        verify(jul).severe(org.mockito.ArgumentMatchers.contains("boom"));
        verify(throwable).printStackTrace();
    }
}
