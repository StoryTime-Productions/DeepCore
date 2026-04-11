package dev.deepcore.logging;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralized logger that sends messages to console and optionally to players.
 */
public final class DeepCoreLogger {
    private static final String ROOT_PATH = "logging";
    private static final String PREFIX_PATH = ROOT_PATH + ".prefix";
    private static final String CONSOLE_MIN_PATH = ROOT_PATH + ".console-min-level";
    private static final String PLAYER_CHAT_ENABLED_PATH = ROOT_PATH + ".player-chat-enabled";
    private static final String PLAYER_DEFAULT_OP_PATH = ROOT_PATH + ".player-default-op-level";
    private static final String PLAYER_DEFAULT_NON_OP_PATH = ROOT_PATH + ".player-default-non-op-level";
    private static final String PLAYER_LEVELS_PATH = ROOT_PATH + ".player-levels";

    private final JavaPlugin plugin;
    private final Map<String, DeepCoreLogLevel> playerLevels = new HashMap<>();

    private String prefix;
    private DeepCoreLogLevel consoleMinLevel;
    private DeepCoreLogLevel defaultOpLevel;
    private DeepCoreLogLevel defaultNonOpLevel;
    private boolean playerChatEnabled;

    /**
     * Creates a logger bound to this plugin.
     *
     * @param plugin plugin providing config and console logger access
     */
    public DeepCoreLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.prefix = colorize("&8[&bDeepCore&8]&r ");
        this.consoleMinLevel = DeepCoreLogLevel.INFO;
        this.defaultOpLevel = DeepCoreLogLevel.DEBUG;
        this.defaultNonOpLevel = DeepCoreLogLevel.INFO;
        this.playerChatEnabled = true;
    }

    /**
     * Reloads logger behavior from plugin configuration.
     */
    public void loadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        this.prefix = colorize(config.getString(PREFIX_PATH, "&8[&bDeepCore&8]&r "));
        this.consoleMinLevel = DeepCoreLogLevel.fromString(config.getString(CONSOLE_MIN_PATH), DeepCoreLogLevel.INFO);
        this.playerChatEnabled = config.getBoolean(PLAYER_CHAT_ENABLED_PATH, true);
        this.defaultOpLevel =
                DeepCoreLogLevel.fromString(config.getString(PLAYER_DEFAULT_OP_PATH), DeepCoreLogLevel.DEBUG);
        this.defaultNonOpLevel =
                DeepCoreLogLevel.fromString(config.getString(PLAYER_DEFAULT_NON_OP_PATH), DeepCoreLogLevel.INFO);

        playerLevels.clear();
        ConfigurationSection section = config.getConfigurationSection(PLAYER_LEVELS_PATH);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            DeepCoreLogLevel level = DeepCoreLogLevel.fromString(section.getString(key), null);
            if (level != null) {
                playerLevels.put(normalizePlayerKey(key), level);
            }
        }
    }

    /**
     * Logs a DEBUG message to configured destinations.
     *
     * @param message message text to log
     */
    public void debug(String message) {
        log(DeepCoreLogLevel.DEBUG, message, null);
    }

    /**
     * Logs a DEBUG message to console only.
     *
     * @param message message text to log
     */
    public void debugConsole(String message) {
        logConsoleOnly(DeepCoreLogLevel.DEBUG, message, null);
    }

    /**
     * Logs an INFO message to configured destinations.
     *
     * @param message message text to log
     */
    public void info(String message) {
        log(DeepCoreLogLevel.INFO, message, null);
    }

    /**
     * Logs an INFO message to console only.
     *
     * @param message message text to log
     */
    public void infoConsole(String message) {
        logConsoleOnly(DeepCoreLogLevel.INFO, message, null);
    }

    /**
     * Logs a WARN message to configured destinations.
     *
     * @param message message text to log
     */
    public void warn(String message) {
        log(DeepCoreLogLevel.WARN, message, null);
    }

    /**
     * Logs a WARN message to console only.
     *
     * @param message message text to log
     */
    public void warnConsole(String message) {
        logConsoleOnly(DeepCoreLogLevel.WARN, message, null);
    }

    /**
     * Logs an ERROR message to configured destinations.
     *
     * @param message message text to log
     */
    public void error(String message) {
        log(DeepCoreLogLevel.ERROR, message, null);
    }

    /**
     * Logs an ERROR message to console only.
     *
     * @param message message text to log
     */
    public void errorConsole(String message) {
        logConsoleOnly(DeepCoreLogLevel.ERROR, message, null);
    }

    /**
     * Logs an ERROR message with a throwable to configured destinations.
     *
     * @param message   message text to log
     * @param throwable throwable to print alongside the message
     */
    public void error(String message, Throwable throwable) {
        log(DeepCoreLogLevel.ERROR, message, throwable);
    }

    /**
     * Logs an ERROR message with a throwable to console only.
     *
     * @param message   message text to log
     * @param throwable throwable to print alongside the message
     */
    public void errorConsole(String message, Throwable throwable) {
        logConsoleOnly(DeepCoreLogLevel.ERROR, message, throwable);
    }

    /**
     * Sends a DEBUG message to a specific command sender.
     *
     * @param sender  recipient sender
     * @param message message text to send
     */
    public void sendDebug(CommandSender sender, String message) {
        send(sender, DeepCoreLogLevel.DEBUG, message);
    }

    /**
     * Sends an INFO message to a specific command sender.
     *
     * @param sender  recipient sender
     * @param message message text to send
     */
    public void sendInfo(CommandSender sender, String message) {
        send(sender, DeepCoreLogLevel.INFO, message);
    }

    /**
     * Sends a WARN message to a specific command sender.
     *
     * @param sender  recipient sender
     * @param message message text to send
     */
    public void sendWarn(CommandSender sender, String message) {
        send(sender, DeepCoreLogLevel.WARN, message);
    }

    /**
     * Sends an ERROR message to a specific command sender.
     *
     * @param sender  recipient sender
     * @param message message text to send
     */
    public void sendError(CommandSender sender, String message) {
        send(sender, DeepCoreLogLevel.ERROR, message);
    }

    /**
     * Sends a message to one command sender with level formatting.
     *
     * @param sender  recipient sender
     * @param level   log severity level for formatting
     * @param message message text to send
     */
    public void send(CommandSender sender, DeepCoreLogLevel level, String message) {
        if (sender == null) {
            return;
        }

        String sanitized = ChatColor.stripColor(message == null ? "" : message);
        sender.sendMessage(formatPlayerMessage(level, sanitized));
    }

    /**
     * Logs to console and, if enabled, to online players filtered by level.
     *
     * @param level     severity level for routing and formatting
     * @param message   message text to log
     * @param throwable optional throwable to print with the message
     */
    public void log(DeepCoreLogLevel level, String message, Throwable throwable) {
        logToConsole(level, message, throwable);

        if (!playerChatEnabled) {
            return;
        }

        String playerMessage = formatPlayerMessage(level, message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            DeepCoreLogLevel effective = getEffectivePlayerLevel(player);
            if (effective.includes(level)) {
                player.sendMessage(playerMessage);
            }
        }
    }

    private void logConsoleOnly(DeepCoreLogLevel level, String message, Throwable throwable) {
        logToConsole(level, message, throwable);
    }

    private void logToConsole(DeepCoreLogLevel level, String message, Throwable throwable) {
        String consoleLine = "[" + level.name() + "] " + message;
        if (consoleMinLevel.includes(level)) {
            switch (level) {
                case DEBUG, INFO -> plugin.getLogger().info(consoleLine);
                case WARN -> plugin.getLogger().warning(consoleLine);
                case ERROR -> plugin.getLogger().severe(consoleLine);
            }
        }

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    public DeepCoreLogLevel getEffectivePlayerLevel(Player player) {
        DeepCoreLogLevel explicit =
                playerLevels.get(normalizePlayerKey(player.getUniqueId().toString()));
        if (explicit != null) {
            return explicit;
        }

        return player.isOp() ? defaultOpLevel : defaultNonOpLevel;
    }

    /**
     * Assigns an explicit log level override for a player UUID.
     *
     * @param playerId player UUID to configure
     * @param level    override level for that player
     */
    public void setPlayerLevel(UUID playerId, DeepCoreLogLevel level) {
        playerLevels.put(normalizePlayerKey(playerId.toString()), level);
        persistPlayerLevels();
    }

    /**
     * Clears any explicit log level override for a player UUID.
     *
     * @param playerId player UUID whose override should be removed
     */
    public void clearPlayerLevel(UUID playerId) {
        playerLevels.remove(normalizePlayerKey(playerId.toString()));
        persistPlayerLevels();
    }

    public DeepCoreLogLevel getConfiguredPlayerLevel(UUID playerId) {
        return playerLevels.get(normalizePlayerKey(playerId.toString()));
    }

    private void persistPlayerLevels() {
        FileConfiguration config = plugin.getConfig();
        config.set(PLAYER_LEVELS_PATH, null);
        for (Map.Entry<String, DeepCoreLogLevel> entry : playerLevels.entrySet()) {
            config.set(
                    PLAYER_LEVELS_PATH + "." + entry.getKey(), entry.getValue().name());
        }
        plugin.saveConfig();
    }

    private String formatPlayerMessage(DeepCoreLogLevel level, String message) {
        ChatColor levelColor =
                switch (level) {
                    case DEBUG -> ChatColor.GRAY;
                    case INFO -> ChatColor.AQUA;
                    case WARN -> ChatColor.YELLOW;
                    case ERROR -> ChatColor.RED;
                };

        return prefix + levelColor + "[" + level.name() + "] " + ChatColor.WHITE + message;
    }

    private String normalizePlayerKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
