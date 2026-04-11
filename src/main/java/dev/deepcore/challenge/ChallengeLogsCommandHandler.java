package dev.deepcore.challenge;

import dev.deepcore.logging.DeepCoreLogLevel;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /challenge logs subcommands and tab completion.
 */
public final class ChallengeLogsCommandHandler {
    private final DeepCoreLogger logger;

    /**
     * Creates a logs subcommand handler backed by DeepCore logger services.
     *
     * @param logger logger backing status, set, and clear operations
     */
    public ChallengeLogsCommandHandler(DeepCoreLogger logger) {
        this.logger = logger;
    }

    /**
     * Handles `/challenge logs` command actions.
     *
     * @param sender command sender issuing the logs subcommand
     * @param args   full challenge command argument array
     * @return true when command handling is complete
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sendLogsStatus(sender, sender instanceof Player ? (Player) sender : null);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> handleLogsSet(sender, args);
            case "clear" -> handleLogsClear(sender, args);
            default -> sendInfo(sender, ChatColor.RED + "Usage: /challenge logs <status|set|clear>");
        }
        return true;
    }

    /**
     * Returns tab-completion candidates for `/challenge logs` arguments.
     *
     * @param sender command sender requesting completion
     * @param args   current command argument array
     * @return completion candidates matching current logs subcommand context
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("logs")) {
            return filterByPrefix(args[1], Arrays.asList("status", "set", "clear"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("logs") && args[1].equalsIgnoreCase("set")) {
            if (sender.hasPermission("deepcore.challenge.logs.admin")) {
                List<String> options = new ArrayList<>(Arrays.stream(DeepCoreLogLevel.values())
                        .map(level -> level.name().toLowerCase(Locale.ROOT))
                        .toList());
                options.addAll(
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return filterByPrefix(args[2], options);
            }

            return filterByPrefix(
                    args[2],
                    Arrays.stream(DeepCoreLogLevel.values())
                            .map(level -> level.name().toLowerCase(Locale.ROOT))
                            .toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("logs") && args[1].equalsIgnoreCase("set")) {
            return filterByPrefix(
                    args[3],
                    Arrays.stream(DeepCoreLogLevel.values())
                            .map(level -> level.name().toLowerCase(Locale.ROOT))
                            .toList());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("logs")
                && args[1].equalsIgnoreCase("clear")
                && sender.hasPermission("deepcore.challenge.logs.admin")) {
            List<String> options = new ArrayList<>();
            options.add("self");
            options.addAll(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return filterByPrefix(args[2], options);
        }

        return List.of();
    }

    private void handleLogsSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendInfo(sender, ChatColor.RED + "Usage: /challenge logs set <level>");
            sendInfo(sender, ChatColor.RED + "Admin: /challenge logs set <player> <level>");
            return;
        }

        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                sendInfo(sender, ChatColor.RED + "Console must specify a player: /challenge logs set <player> <level>");
                return;
            }

            DeepCoreLogLevel level = DeepCoreLogLevel.fromString(args[2], null);
            if (level == null) {
                sendInfo(sender, ChatColor.RED + "Invalid level. Use debug|info|warn|error.");
                return;
            }

            logger.setPlayerLevel(player.getUniqueId(), level);
            sendInfo(sender, ChatColor.GREEN + "Your log level is now " + ChatColor.YELLOW + level.name());
            return;
        }

        if (!sender.hasPermission("deepcore.challenge.logs.admin")) {
            sendInfo(sender, ChatColor.RED + "You do not have permission to set another player's log level.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sendInfo(sender, ChatColor.RED + "Player not found: " + args[2]);
            return;
        }

        DeepCoreLogLevel level = DeepCoreLogLevel.fromString(args[3], null);
        if (level == null) {
            sendInfo(sender, ChatColor.RED + "Invalid level. Use debug|info|warn|error.");
            return;
        }

        logger.setPlayerLevel(target.getUniqueId(), level);
        sendInfo(
                sender,
                ChatColor.GREEN + "Set " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " log level to "
                        + ChatColor.YELLOW + level.name());
    }

    private void handleLogsClear(CommandSender sender, String[] args) {
        if (args.length == 2 || (args.length == 3 && args[2].equalsIgnoreCase("self"))) {
            if (!(sender instanceof Player player)) {
                sendInfo(sender, ChatColor.RED + "Console must specify a player: /challenge logs clear <player>");
                return;
            }

            logger.clearPlayerLevel(player.getUniqueId());
            sendInfo(sender, ChatColor.GREEN + "Your custom log level was cleared.");
            return;
        }

        if (!sender.hasPermission("deepcore.challenge.logs.admin")) {
            sendInfo(sender, ChatColor.RED + "You do not have permission to clear another player's log level.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sendInfo(sender, ChatColor.RED + "Player not found: " + args[2]);
            return;
        }

        logger.clearPlayerLevel(target.getUniqueId());
        sendInfo(sender, ChatColor.GREEN + "Cleared custom log level for " + ChatColor.YELLOW + target.getName());
    }

    private void sendLogsStatus(CommandSender sender, Player target) {
        if (target == null) {
            sendInfo(sender, ChatColor.YELLOW + "Console status requires a player target.");
            sendInfo(sender, ChatColor.YELLOW + "Use: /challenge logs set <player> <level>");
            return;
        }

        DeepCoreLogLevel configured = logger.getConfiguredPlayerLevel(target.getUniqueId());
        DeepCoreLogLevel effective = logger.getEffectivePlayerLevel(target);

        sendInfo(
                sender,
                ChatColor.GOLD + "Log preferences for " + ChatColor.YELLOW + target.getName() + ChatColor.GOLD + ":");
        sendInfo(
                sender,
                ChatColor.GRAY + "Configured: "
                        + (configured == null ? ChatColor.DARK_GRAY + "default" : ChatColor.AQUA + configured.name()));
        sendInfo(sender, ChatColor.GRAY + "Effective: " + ChatColor.AQUA + effective.name());
    }

    private List<String> filterByPrefix(String typed, List<String> options) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                results.add(option);
            }
        }
        return results;
    }

    private void sendInfo(CommandSender sender, String message) {
        logger.sendInfo(sender, message);
    }
}
