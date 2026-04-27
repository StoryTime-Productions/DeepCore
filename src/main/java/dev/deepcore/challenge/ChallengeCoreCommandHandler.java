package dev.deepcore.challenge;

import dev.deepcore.challenge.training.TrainingManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Handles non-logs /challenge subcommands and tab completion.
 */
public final class ChallengeCoreCommandHandler {
    private final ChallengeAdminFacade adminFacade;
    private final TrainingManager trainingManager;
    private final DeepCoreLogger logger;

    /**
     * Creates a core command handler for non-logs `/challenge` subcommands.
     *
     * @param adminFacade     admin facade providing challenge control operations
     * @param trainingManager training gym manager
     * @param logger          logger used for command diagnostics
     */
    public ChallengeCoreCommandHandler(
            ChallengeAdminFacade adminFacade, TrainingManager trainingManager, DeepCoreLogger logger) {
        this.adminFacade = adminFacade;
        this.trainingManager = trainingManager;
        this.logger = logger;
    }

    /**
     * Handles a `/challenge` subcommand invocation.
     *
     * @param sender command sender invoking the challenge command
     * @param args   command arguments including subcommand and options
     * @return true after processing the command input
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "train" -> {
                return trainingManager.handleCommand(sender, args);
            }
            case "list" -> {
                sendInfo(sender, ChatColor.GOLD + "Available challenge modes:");
                for (ChallengeMode mode : ChallengeMode.values()) {
                    sendInfo(
                            sender, ChatColor.YELLOW + "- " + mode.key() + ChatColor.GRAY + " - " + mode.displayName());
                }
                return true;
            }
            case "enable" -> {
                if (!requireAdminPermission(sender)) {
                    return true;
                }
                if (!canEditSettings(sender)) {
                    return true;
                }
                adminFacade.setEnabled(true);
                sendInfo(sender, ChatColor.GREEN + "Challenge mode is now enabled.");
                sendStatus(sender);
                return true;
            }
            case "disable" -> {
                if (!requireAdminPermission(sender)) {
                    return true;
                }
                if (!canEditSettings(sender)) {
                    return true;
                }
                adminFacade.setEnabled(false);
                sendInfo(sender, ChatColor.RED + "Challenge mode is now disabled.");
                sendStatus(sender);
                return true;
            }
            case "mode" -> {
                if (!requireAdminPermission(sender)) {
                    return true;
                }
                if (!canEditSettings(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    sendInfo(sender, ChatColor.RED + "Usage: /challenge mode <mode-key>");
                    return true;
                }

                String requested = args[1].toLowerCase(Locale.ROOT);
                ChallengeMode mode = ChallengeMode.fromKey(requested).orElse(null);
                if (mode == null) {
                    sendInfo(sender, ChatColor.RED + "Unknown mode '" + requested + "'. Use /challenge list.");
                    return true;
                }

                adminFacade.setMode(mode);
                sendInfo(
                        sender,
                        ChatColor.GREEN + "Active challenge mode set to: " + ChatColor.YELLOW + mode.displayName());
                sendStatus(sender);
                return true;
            }
            case "component" -> {
                handleComponentSubcommand(sender, args);
                return true;
            }
            case "end" -> {
                if (!sender.hasPermission("deepcore.challenge.end")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to end challenges.");
                    return true;
                }

                if (adminFacade.isPrepPhase()) {
                    sendInfo(sender, ChatColor.YELLOW + "Already in prep mode.");
                    return true;
                }

                adminFacade.endChallengeAndReturnToPrep();
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("deepcore.challenge.end")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to stop challenges.");
                    return true;
                }

                if (adminFacade.isPrepPhase()) {
                    sendInfo(sender, ChatColor.YELLOW + "Already in prep mode. Regenerating run worlds.");
                    adminFacade.stopChallenge(sender);
                    return true;
                }

                adminFacade.stopChallenge(sender);
                return true;
            }
            case "pause" -> {
                if (!sender.hasPermission("deepcore.challenge.pause")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to pause challenges.");
                    return true;
                }

                if (!adminFacade.isRunningPhase()) {
                    sendInfo(sender, ChatColor.YELLOW + "Challenge can only be paused while running.");
                    return true;
                }

                if (!adminFacade.pauseChallenge(sender)) {
                    sendInfo(sender, ChatColor.RED + "Failed to pause challenge.");
                }
                return true;
            }
            case "resume" -> {
                if (!sender.hasPermission("deepcore.challenge.pause")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to resume challenges.");
                    return true;
                }

                if (!adminFacade.isPausedPhase()) {
                    sendInfo(sender, ChatColor.YELLOW + "Challenge is not paused.");
                    return true;
                }

                if (!adminFacade.resumeChallenge(sender)) {
                    sendInfo(sender, ChatColor.RED + "Failed to resume challenge.");
                }
                return true;
            }
            case "reset", "resetworld" -> {
                if (!sender.hasPermission("deepcore.challenge.reset")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to reset worlds.");
                    return true;
                }

                adminFacade.resetWorlds(sender);
                return true;
            }
            case "lobby" -> {
                if (!requireAdminPermission(sender)) {
                    return true;
                }

                if (args.length < 2) {
                    sendInfo(sender, ChatColor.RED + "Usage: /challenge lobby <limbo|overworld|nether>");
                    return true;
                }

                String selector = args[1].toLowerCase(Locale.ROOT);
                String selectedWorldName = adminFacade.selectLobbyWorld(selector);
                if (selectedWorldName == null) {
                    sendInfo(sender, ChatColor.RED + "Unknown lobby selector. Use limbo|overworld|nether.");
                    return true;
                }

                int teleported = adminFacade.teleportOnlinePlayersToActiveLobby();
                sendInfo(
                        sender, ChatColor.GREEN + "Active lobby world set to: " + ChatColor.YELLOW + selectedWorldName);
                sendInfo(
                        sender,
                        ChatColor.GREEN + "Teleported players to active lobby: " + ChatColor.YELLOW + teleported);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("deepcore.challenge.reload")
                        && !sender.hasPermission("deepcore.challenge.admin")) {
                    sendInfo(sender, ChatColor.RED + "You do not have permission to reload config.");
                    return true;
                }

                if (adminFacade.reloadConfigAndApply()) {
                    sendInfo(sender, ChatColor.GREEN + "Config reloaded and applied immediately (prep phase).");
                } else {
                    sendInfo(
                            sender,
                            ChatColor.YELLOW
                                    + "Config file reloaded. Challenge settings will apply on next run reset.");
                }
                return true;
            }
            default -> {
                sendInfo(
                        sender,
                        ChatColor.RED
                                + "Unknown subcommand. Use /challenge <status|train|list|enable|disable|mode|component|end|stop|pause|resume|reset|resetworld|lobby|reload|logs>.");
                return true;
            }
        }
    }

    /**
     * Returns tab-completion candidates for `/challenge` arguments.
     *
     * @param args current command arguments typed by the sender
     * @return matching completion candidates for the current argument position
     */
    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return filterByPrefix(
                    args[0],
                    Arrays.asList(
                            "status",
                            "train",
                            "list",
                            "enable",
                            "disable",
                            "mode",
                            "component",
                            "end",
                            "stop",
                            "pause",
                            "resume",
                            "reset",
                            "resetworld",
                            "lobby",
                            "reload",
                            "logs"));
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("train")) {
            return trainingManager.tabComplete(args);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filterByPrefix(
                    args[1],
                    Arrays.stream(ChallengeMode.values())
                            .map(ChallengeMode::key)
                            .collect(Collectors.toList()));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            return filterByPrefix(args[1], Arrays.asList("limbo", "overworld", "nether"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("component")) {
            List<String> options = new ArrayList<>();
            options.add("list");
            options.add("status");
            options.add("reset");
            options.addAll(Arrays.stream(ChallengeComponent.values())
                    .map(ChallengeComponent::key)
                    .toList());
            return filterByPrefix(args[1], options);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("component")) {
            if (ChallengeComponent.fromKey(args[1]).isPresent()) {
                return filterByPrefix(args[2], Arrays.asList("on", "off", "toggle"));
            }
        }

        return List.of();
    }

    private void sendStatus(CommandSender sender) {
        sendInfo(
                sender,
                ChatColor.AQUA + "Challenge enabled: "
                        + (adminFacade.isChallengeEnabled() ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
        sendInfo(sender, ChatColor.AQUA + "Phase: " + ChatColor.YELLOW + adminFacade.getPhaseName());
        sendInfo(
                sender,
                ChatColor.AQUA + "Ready: " + ChatColor.YELLOW + adminFacade.getReadyCount() + ChatColor.GRAY + "/"
                        + ChatColor.YELLOW + adminFacade.getReadyTargetCount());
        sendInfo(
                sender,
                ChatColor.AQUA + "Current mode: " + ChatColor.YELLOW
                        + adminFacade.getMode().displayName());
        sendInfo(sender, ChatColor.GRAY + "Mode key: " + adminFacade.getMode().key());
        sendComponentStatus(sender);
    }

    private void handleComponentSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status") || args[1].equalsIgnoreCase("list")) {
            sendComponentStatus(sender);
            return;
        }

        if (!requireAdminPermission(sender)) {
            return;
        }

        if (!canEditSettings(sender)) {
            return;
        }

        if (args[1].equalsIgnoreCase("reset")) {
            adminFacade.resetComponentsToModeDefaults();
            sendInfo(sender, ChatColor.GREEN + "Components reset to defaults for current mode.");
            sendComponentStatus(sender);
            return;
        }

        ChallengeComponent component = ChallengeComponent.fromKey(args[1]).orElse(null);
        if (component == null) {
            sendInfo(sender, ChatColor.RED + "Unknown component key '" + args[1] + "'. Use /challenge component list.");
            return;
        }

        if (args.length < 3) {
            sendInfo(sender, ChatColor.RED + "Usage: /challenge component <component-key> <on|off|toggle>");
            return;
        }

        String operation = args[2].toLowerCase(Locale.ROOT);
        if (!(operation.equals("on") || operation.equals("off") || operation.equals("toggle"))) {
            sendInfo(sender, ChatColor.RED + "Unknown operation '" + operation + "'. Use on/off/toggle.");
            return;
        }

        adminFacade.applyComponentOperation(component, operation);

        boolean enabled = adminFacade.isComponentEnabled(component);
        sendInfo(
                sender,
                ChatColor.GREEN + component.displayName() + ": "
                        + (enabled ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
    }

    private void sendComponentStatus(CommandSender sender) {
        sendInfo(sender, ChatColor.GOLD + "Challenge components:");
        Map<ChallengeComponent, Boolean> toggles = adminFacade.getComponentToggles();
        for (ChallengeComponent component : ChallengeComponent.values()) {
            boolean enabled = toggles.getOrDefault(component, false);
            sendInfo(
                    sender,
                    ChatColor.YELLOW + "- " + component.key() + ChatColor.GRAY + " :: "
                            + component.displayName() + ChatColor.DARK_GRAY + " ["
                            + (enabled ? ChatColor.GREEN + "on" : ChatColor.RED + "off") + ChatColor.DARK_GRAY + "]");
        }
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

    private boolean canEditSettings(CommandSender sender) {
        if (adminFacade.canEditSettings()) {
            return true;
        }

        sendInfo(sender, ChatColor.RED + "Settings are locked once everyone is ready.");
        return false;
    }

    private boolean requireAdminPermission(CommandSender sender) {
        if (sender.hasPermission("deepcore.challenge.admin")) {
            return true;
        }

        sendInfo(sender, ChatColor.RED + "You do not have permission to manage challenge settings.");
        return false;
    }

    private void sendInfo(CommandSender sender, String message) {
        logger.sendInfo(sender, message);
    }
}
