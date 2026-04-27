package dev.deepcore.challenge.training;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Manages training-world mini-challenges, timers, HUD output, and persistence.
 */
public final class TrainingManager implements Listener {
    private static final String TRAINING_OBJECTIVE_NAME = "deepcore_train";
    private static final String TRAINING_MUSIC_SOUND = "storytime:training.background";
    private static final String TRAINING_OBJECTIVE_TITLE =
            ChatColor.AQUA + " " + ChatColor.BOLD + "Training Gym" + ChatColor.RESET + ChatColor.AQUA + " ";

    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final TrainingStatsStore statsStore;

    private final Map<TrainingChallengeType, ChallengeDefinition> definitions;
    private final Map<TrainingChallengeType, ActiveAttempt> activeByChallenge;
    private final Map<UUID, ActiveAttempt> activeByPlayer;
    private final Map<UUID, Location> returnLocations;
    private final Map<TrainingChallengeType, ArenaSnapshot> arenaSnapshots;
    private final Map<TrainingChallengeType, BlockSnapshot> hiddenStartButtonSnapshots;
    private final Map<TrainingChallengeType, Set<BlockKey>> trackedPortalLavaByChallenge;
    private final Map<UUID, String> lastDeathWorldByPlayer;
    private final Map<UUID, Location> pendingAttemptRespawnByPlayer;
    private final Map<UUID, BukkitTask> bridgeParticleTaskByPlayer;

    private String trainingWorldName;
    private Location trainingLobbySpawn;
    private Location trainingRespawnSpawn;
    private boolean enabled;
    private BukkitTask hudTask;
    private TrainingReturnItemService returnItemService;

    /**
     * Creates a training manager with configuration-backed behavior.
     *
     * @param plugin plugin instance
     */
    public TrainingManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = ((DeepCorePlugin) plugin).getDeepCoreLogger();
        this.statsStore = new TrainingStatsStore(plugin);
        this.definitions = new EnumMap<>(TrainingChallengeType.class);
        this.activeByChallenge = new EnumMap<>(TrainingChallengeType.class);
        this.activeByPlayer = new HashMap<>();
        this.returnLocations = new HashMap<>();
        this.arenaSnapshots = new EnumMap<>(TrainingChallengeType.class);
        this.hiddenStartButtonSnapshots = new EnumMap<>(TrainingChallengeType.class);
        this.trackedPortalLavaByChallenge = new EnumMap<>(TrainingChallengeType.class);
        this.lastDeathWorldByPlayer = new HashMap<>();
        this.pendingAttemptRespawnByPlayer = new HashMap<>();
        this.bridgeParticleTaskByPlayer = new HashMap<>();
    }

    /**
     * Initializes configuration, persistence, listener registration, and HUD tasks.
     */
    public void initialize() {
        loadFromConfig();
        statsStore.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        NamespacedKey returnItemKey = new NamespacedKey(plugin, "training_return_item");
        returnItemService = new TrainingReturnItemService(plugin, this, returnItemKey);
        returnItemService.initialize();
        startHudTask();
        resetAllChallengeArenas();
    }

    /** Stops scheduled tasks and flushes stats to disk. */
    public void shutdown() {
        restoreAllActiveAttemptsForShutdown();
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        if (returnItemService != null) {
            returnItemService.shutdown();
        }
        clearAllTrainingHud();
        statsStore.save();
    }

    private void restoreAllActiveAttemptsForShutdown() {
        if (activeByPlayer.isEmpty()) {
            return;
        }

        for (ActiveAttempt attempt : new ArrayList<>(activeByPlayer.values())) {
            Player player = Bukkit.getPlayer(attempt.playerId());
            if (player != null) {
                stopBridgeVisuals(player);
            }
            restoreAttemptArenaState(attempt.type());
            restoreStartButton(attempt.type());
        }

        bridgeParticleTaskByPlayer.values().forEach(BukkitTask::cancel);
        bridgeParticleTaskByPlayer.clear();
        activeByPlayer.clear();
        activeByChallenge.clear();
    }

    /**
     * Reloads training configuration from plugin config.
     */
    public void reloadFromConfig() {
        loadFromConfig();
    }

    /**
     * Handles `/challenge train ...` command execution.
     *
     * @param sender command sender
     * @param args   full `/challenge` argument array
     * @return true after handling the command
     */
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            log.sendWarn(sender, "Only players can use training commands.");
            return true;
        }

        if (!enabled) {
            log.sendWarn(sender, "Training gym is currently disabled.");
            return true;
        }

        if (args.length == 1) {
            teleportToTrainingLobby(player);
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "leave" -> leaveTraining(player);
            case "stats" -> showStats(player, args.length >= 3 ? args[2] : null);
            case "start" -> teleportToChallengeStartArea(player, args.length >= 3 ? args[2] : null);
            case "reset" -> resetOwnAttempt(player);
            default -> log.sendWarn(
                    player,
                    "Usage: /challenge train [leave|stats [portal|craft|chest|bridge]|start <challenge>|reset]");
        }
        return true;
    }

    /**
     * Returns tab completions for `/challenge train ...` arguments.
     *
     * @param args full `/challenge` argument array
     * @return completions for train subcommand context
     */
    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(args[1], List.of("leave", "stats", "start", "reset"));
        }

        if (args.length == 3 && (args[1].equalsIgnoreCase("stats") || args[1].equalsIgnoreCase("start"))) {
            return filterByPrefix(
                    args[2],
                    Arrays.stream(TrainingChallengeType.values())
                            .map(TrainingChallengeType::key)
                            .collect(Collectors.toList()));
        }

        return List.of();
    }

    private void loadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("training.enabled", true);
        trainingWorldName = config.getString("training.world", "deepcore_gym");
        ensureTrainingWorldLoaded();
        trainingLobbySpawn = readLocation(config, "training.lobby-spawn").orElse(null);
        trainingRespawnSpawn = readLocation(config, "training.respawn-spawn").orElse(trainingLobbySpawn);

        definitions.clear();
        for (TrainingChallengeType type : TrainingChallengeType.values()) {
            String basePath = "training.challenges." + type.key();
            if (!config.getBoolean(basePath + ".enabled", true)) {
                continue;
            }

            Optional<Cuboid> region = readCuboid(config, basePath + ".region");
            Optional<Cuboid> sidebarRegion = readCuboid(config, basePath + ".sidebar-region");
            Optional<Location> startButton = readLocation(config, basePath + ".start-button");
            Optional<Location> startLocation = readLocation(config, basePath + ".start-location");
            if (region.isEmpty() || startButton.isEmpty() || startLocation.isEmpty()) {
                log.warn("Training challenge '" + type.key() + "' is missing required config and will be disabled.");
                continue;
            }

            Location bridgeCompletionPlate = readLocation(config, basePath + ".completion-pressure-plate")
                    .orElse(null);
            Location hopperLocation = readLocation(config, basePath + ".hopper").orElse(null);
            int minChests = Math.max(1, config.getInt(basePath + ".min-chests", 4));
            int maxChests = Math.max(minChests, config.getInt(basePath + ".max-chests", 8));
            int minBeds = Math.max(1, config.getInt("training.craft.beds.min", 5));
            int maxBeds = Math.max(minBeds, config.getInt("training.craft.beds.max", 8));
            int minEyes = Math.max(1, config.getInt("training.craft.eyes-of-ender.min", 7));
            int maxEyes = Math.max(minEyes, config.getInt("training.craft.eyes-of-ender.max", 9));
            List<BridgePlatform> bridgePlatforms =
                    type == TrainingChallengeType.BRIDGE ? readBridgePlatforms(config, basePath) : List.of();

            definitions.put(
                    type,
                    new ChallengeDefinition(
                            type,
                            region.get(),
                            sidebarRegion.orElse(null),
                            startButton.get(),
                            startLocation.get(),
                            bridgeCompletionPlate,
                            hopperLocation,
                            minChests,
                            maxChests,
                            minBeds,
                            maxBeds,
                            minEyes,
                            maxEyes,
                            bridgePlatforms));
        }

        arenaSnapshots.clear();
        trackedPortalLavaByChallenge.clear();
    }

    private void ensureTrainingWorldLoaded() {
        if (!enabled || trainingWorldName == null || trainingWorldName.isBlank()) {
            return;
        }

        if (Bukkit.getWorld(trainingWorldName) != null) {
            return;
        }

        try {
            WorldCreator creator = new WorldCreator(trainingWorldName);
            creator.environment(World.Environment.NORMAL);
            creator.type(org.bukkit.WorldType.FLAT);
            creator.generateStructures(false);
            try {
                creator.generatorSettings(
                        "{\"biome\":\"minecraft:the_void\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"structures\":{\"structures\":{}}}");
            } catch (Throwable ignored) {
                // Some server variants may not accept generator settings via API.
            }

            World loaded = Bukkit.createWorld(creator);
            if (loaded == null) {
                log.warn("Configured training world '" + trainingWorldName + "' could not be loaded.");
            }
        } catch (RuntimeException ex) {
            log.warn("Failed loading training world '" + trainingWorldName + "': " + ex.getMessage());
        }
    }

    private void startHudTask() {
        if (hudTask != null) {
            hudTask.cancel();
        }

        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHud, 0L, 2L);
    }

    private void tickHud() {
        if (!enabled) {
            clearAllTrainingHud();
            return;
        }

        checkPortalAttemptCompletion();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInTrainingWorld(player)) {
                clearTrainingSidebar(player);
                continue;
            }

            ActiveAttempt activeAttempt = activeByPlayer.get(player.getUniqueId());
            if (activeAttempt != null) {
                player.sendActionBar(Component.text(buildActiveActionBar(activeAttempt)));
                clearTrainingSidebar(player);
                continue;
            }

            showIdleSidebar(player);
        }
    }

    private void clearAllTrainingHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTrainingSidebar(player);
            if (isInTrainingWorld(player)) {
                player.sendActionBar(Component.empty());
            }
        }
    }

    private void showIdleSidebar(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        TrainingChallengeType regionChallenge =
                resolveChallengeByLocation(player.getLocation()).orElse(null);
        TrainingStatsStore.PlayerChallengeStats stats = regionChallenge == null
                ? new TrainingStatsStore.PlayerChallengeStats(-1L, List.of())
                : statsStore.getStats(player.getUniqueId(), regionChallenge);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective =
                scoreboard.registerNewObjective(TRAINING_OBJECTIVE_NAME, "dummy", TRAINING_OBJECTIVE_TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        applyBlankSidebarNumberFormat(objective);

        int score = 12;
        objective.getScore(ChatColor.DARK_GRAY.toString()).setScore(score--);
        objective
                .getScore(ChatColor.YELLOW + "Current Mini-challenge: " + ChatColor.WHITE
                        + (regionChallenge == null ? "NONE" : regionChallenge.displayName()))
                .setScore(score--);
        objective.getScore(ChatColor.GRAY.toString()).setScore(score--);
        objective
                .getScore(ChatColor.GRAY + "Best: " + formatOptionalDuration(stats.bestTimeMs()))
                .setScore(score--);
        objective.getScore(ChatColor.BLUE.toString()).setScore(score--);

        List<Long> attempts = stats.lastAttemptsMs();
        if (attempts.isEmpty()) {
            objective.getScore(ChatColor.DARK_GRAY + "No attempts yet").setScore(score--);
        } else {
            for (int index = 0; index < attempts.size() && index < 5; index++) {
                objective
                        .getScore(ChatColor.AQUA + "Try " + (index + 1) + ": " + ChatColor.WHITE
                                + formatDurationMmSsCc(attempts.get(index)))
                        .setScore(score--);
            }
        }

        player.setScoreboard(scoreboard);
    }

    private void clearTrainingSidebar(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        Objective objective = scoreboard.getObjective(TRAINING_OBJECTIVE_NAME);
        if (objective != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void teleportToTrainingLobby(Player player) {
        World world = Bukkit.getWorld(trainingWorldName);
        if (world == null) {
            log.sendError(player, "Training world '" + trainingWorldName + "' is not loaded.");
            return;
        }

        if (!isInTrainingWorld(player)) {
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        }

        Location destination = trainingLobbySpawn == null
                ? world.getSpawnLocation().clone().add(0.5, 0.0, 0.5)
                : trainingLobbySpawn.clone();
        if (destination.getWorld() == null) {
            destination.setWorld(world);
        }

        player.teleport(destination);
        if (returnItemService != null) {
            returnItemService.onPlayerEnterTraining(player);
        }
        log.sendInfo(player, ChatColor.GREEN + "Welcome to Training Gym.");
    }

    /**
     * Returns a player from their training attempt to the training lobby.
     *
     * @param player player to return to lobby
     */
    public void returnToTrainingLobby(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveAttempt attempt = activeByPlayer.get(playerId);
        if (attempt != null) {
            cancelAttempt(player, "Attempt cancelled: returned to lobby.");
        }
        if (returnItemService != null) {
            returnItemService.onPlayerLeaveTraining(player);
        }
        teleportToTrainingLobby(player);
        log.sendInfo(player, ChatColor.YELLOW + "Returned to training lobby.");
    }

    public boolean isInActiveAttempt(Player player) {
        return activeByPlayer.containsKey(player.getUniqueId());
    }

    /**
     * Cancels any active attempt and cleans up return-item state for the player.
     *
     * @param player player leaving the training area
     */
    public void leaveTraining(Player player) {
        cancelAttempt(player, "Attempt cancelled: left training.");
        if (returnItemService != null) {
            returnItemService.onPlayerLeaveTraining(player);
        }

        Location returnLocation = returnLocations.remove(player.getUniqueId());
        if (returnLocation == null || returnLocation.getWorld() == null) {
            World world =
                    Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                log.sendWarn(player, "No return location available.");
                return;
            }
            returnLocation = world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }

        player.teleport(returnLocation);
        clearTrainingSidebar(player);
    }

    private void showStats(Player player, String challengeKey) {
        if (challengeKey == null) {
            TrainingChallengeType regionType =
                    resolveChallengeByLocation(player.getLocation()).orElse(null);
            if (regionType == null) {
                log.sendInfo(player, ChatColor.YELLOW + "Current Mini-challenge: NONE");
                return;
            }
            sendStatsLineup(player, regionType);
            return;
        }

        TrainingChallengeType type = TrainingChallengeType.fromKey(challengeKey).orElse(null);
        if (type == null) {
            log.sendWarn(player, "Unknown challenge. Use portal|craft|chest|bridge.");
            return;
        }

        sendStatsLineup(player, type);
    }

    private void sendStatsLineup(Player player, TrainingChallengeType type) {
        TrainingStatsStore.PlayerChallengeStats stats = statsStore.getStats(player.getUniqueId(), type);
        log.sendInfo(player, ChatColor.GOLD + type.displayName() + " stats:");
        log.sendInfo(player, ChatColor.GRAY + "Best: " + formatOptionalDuration(stats.bestTimeMs()));
        if (stats.lastAttemptsMs().isEmpty()) {
            log.sendInfo(player, ChatColor.DARK_GRAY + "No attempts yet.");
            return;
        }

        int index = 1;
        for (Long attempt : stats.lastAttemptsMs()) {
            log.sendInfo(
                    player, ChatColor.AQUA + "Try " + index + ": " + ChatColor.WHITE + formatDurationMmSsCc(attempt));
            index++;
        }
    }

    private void teleportToChallengeStartArea(Player player, String challengeKey) {
        if (challengeKey == null) {
            log.sendWarn(player, "Usage: /challenge train start <portal|craft|chest|bridge>");
            return;
        }

        TrainingChallengeType type = TrainingChallengeType.fromKey(challengeKey).orElse(null);
        if (type == null) {
            log.sendWarn(player, "Unknown challenge. Use portal|craft|chest|bridge.");
            return;
        }

        ChallengeDefinition definition = definitions.get(type);
        if (definition == null) {
            log.sendWarn(player, "That challenge is not configured.");
            return;
        }

        player.teleport(definition.startButton().clone().add(0.5, 0.0, 0.5));
        log.sendInfo(player, ChatColor.YELLOW + "Press the start button to begin " + type.displayName() + ".");
    }

    private void resetOwnAttempt(Player player) {
        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null) {
            log.sendWarn(player, "You are not currently in an active training attempt.");
            return;
        }

        cancelAttempt(player, "Attempt cancelled.");
        if (returnItemService != null) {
            returnItemService.onPlayerEnterTraining(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStartButtonPress(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Optional<TrainingChallengeType> match =
                resolveChallengeByStartButton(event.getClickedBlock().getLocation());
        if (match.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        startAttempt(player, match.get());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBridgeCompletionPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.BRIDGE) {
            return;
        }

        Location destinationPlate = attempt.bridgeDestinationPlate();
        if (destinationPlate == null) {
            ChallengeDefinition definition = definitions.get(TrainingChallengeType.BRIDGE);
            if (definition == null || definition.completionPressurePlate() == null) {
                return;
            }
            destinationPlate = definition.completionPressurePlate();
        }

        if (!sameBlock(event.getClickedBlock().getLocation(), destinationPlate)) {
            return;
        }

        completeAttempt(event.getPlayer(), attempt);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        ActiveAttempt attempt = activeByChallenge.get(TrainingChallengeType.PORTAL);
        if (attempt == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(TrainingChallengeType.PORTAL);
        if (definition == null) {
            return;
        }

        boolean inside =
                event.getBlocks().stream().anyMatch(state -> definition.region().contains(state.getLocation()));
        if (!inside) {
            return;
        }

        Player player = Bukkit.getPlayer(attempt.playerId());
        if (player != null) {
            completeAttempt(player, attempt);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalBlockForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.NETHER_PORTAL) {
            return;
        }

        ActiveAttempt attempt = activeByChallenge.get(TrainingChallengeType.PORTAL);
        if (attempt == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(TrainingChallengeType.PORTAL);
        if (definition == null || !definition.region().contains(event.getBlock().getLocation())) {
            return;
        }

        Player player = Bukkit.getPlayer(attempt.playerId());
        if (player != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ActiveAttempt latest = activeByChallenge.get(TrainingChallengeType.PORTAL);
                if (latest == null || !latest.playerId().equals(player.getUniqueId())) {
                    return;
                }
                completeAttempt(player, latest);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CRAFT || attempt.craftObjective() == null) {
            return;
        }

        CraftObjective objective = attempt.craftObjective();
        Material result = event.getRecipe().getResult().getType();
        int amount = Math.max(1, event.getRecipe().getResult().getAmount());
        if (event.isShiftClick()) {
            amount *= computeShiftClickMultiplier(event.getInventory().getMatrix());
        }

        if (isBedMaterial(result)) {
            objective.craftedBeds += amount;
        } else if (result == Material.ENDER_EYE) {
            objective.craftedEyes += amount;
        } else if (isMetalAxe(result)) {
            objective.craftedAxe = true;
        } else if (isMetalShovel(result)) {
            objective.craftedShovel = true;
        }

        if (objective.isComplete()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                ActiveAttempt latest = activeByPlayer.get(player.getUniqueId());
                if (latest == null || latest.type() != TrainingChallengeType.CRAFT) {
                    return;
                }
                completeAttempt(player, latest);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CHEST) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> maybeCompleteChestAttempt(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CHEST) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> maybeCompleteChestAttempt(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChestChallengeInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CHEST) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> maybeCompleteChestAttempt(player));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChestChallengeBlockBreak(BlockBreakEvent event) {
        ActiveAttempt attempt = activeByPlayer.get(event.getPlayer().getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CHEST) {
            return;
        }

        ChallengeDefinition definition = definitions.get(TrainingChallengeType.CHEST);
        if (definition == null) {
            return;
        }

        if (definition.hopperLocation() != null
                && sameBlock(event.getBlock().getLocation(), definition.hopperLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttemptPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null
                || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        if (activeByPlayer.get(event.getPlayer().getUniqueId()) == null) {
            return;
        }

        ActiveAttempt attempt = activeByPlayer.get(event.getPlayer().getUniqueId());

        ChallengeDefinition definition = definitions.get(attempt.type());
        if (definition == null) {
            cancelAttempt(event.getPlayer(), "Attempt cancelled: challenge unavailable.");
            return;
        }

        if (!definition.region().contains(event.getTo())) {
            cancelAttempt(event.getPlayer(), "Attempt cancelled: you left the challenge area.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttemptTeleport(PlayerTeleportEvent event) {
        ActiveAttempt attempt = activeByPlayer.get(event.getPlayer().getUniqueId());
        if (attempt == null || event.getTo() == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(attempt.type());
        if (definition == null) {
            cancelAttempt(event.getPlayer(), "Attempt cancelled: challenge unavailable.");
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && sameBlock(event.getTo(), definition.startLocation())) {
            return;
        }

        if (!definition.region().contains(event.getTo())) {
            cancelAttempt(event.getPlayer(), "Attempt cancelled: you left the challenge area.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttemptQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        lastDeathWorldByPlayer.remove(playerId);
        pendingAttemptRespawnByPlayer.remove(playerId);
        if (activeByPlayer.containsKey(playerId)) {
            clearAttemptInventoryAndForceSurvival(player);
        }
        cancelAttempt(player, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttemptKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        lastDeathWorldByPlayer.remove(playerId);
        pendingAttemptRespawnByPlayer.remove(playerId);
        if (activeByPlayer.containsKey(playerId)) {
            clearAttemptInventoryAndForceSurvival(player);
        }
        cancelAttempt(player, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world != null && world.getName().equalsIgnoreCase(trainingWorldName)) {
            event.setKeepInventory(false);
        }

        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt != null) {
            ChallengeDefinition definition = definitions.get(attempt.type());
            if (definition != null) {
                Location buttonLocation = definition.startButton().clone().add(0.5D, 0.0D, 0.5D);
                if (buttonLocation.getWorld() != null) {
                    pendingAttemptRespawnByPlayer.put(player.getUniqueId(), buttonLocation);
                }
            }

            event.getDrops().clear();
            event.setDroppedExp(0);
            clearAttemptInventoryAndForceSurvival(player);
            cancelAttempt(player, "Attempt cancelled: you died.");
        }

        if (world == null) {
            return;
        }
        lastDeathWorldByPlayer.put(player.getUniqueId(), world.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Location pendingAttemptRespawn = pendingAttemptRespawnByPlayer.remove(playerId);
        if (pendingAttemptRespawn != null) {
            event.setRespawnLocation(pendingAttemptRespawn);
            Bukkit.getScheduler().runTask(plugin, () -> clearAttemptInventoryAndForceSurvival(event.getPlayer()));
            return;
        }

        String deathWorld = lastDeathWorldByPlayer.remove(playerId);
        if (deathWorld == null || !deathWorld.equalsIgnoreCase(trainingWorldName)) {
            return;
        }

        Location respawn = resolveTrainingRespawnLocation();
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
    }

    private Location resolveTrainingRespawnLocation() {
        World trainingWorld = Bukkit.getWorld(trainingWorldName);
        if (trainingWorld == null) {
            return null;
        }

        Location base = trainingRespawnSpawn != null
                ? trainingRespawnSpawn.clone()
                : (trainingLobbySpawn != null ? trainingLobbySpawn.clone() : null);

        if (base == null) {
            return trainingWorld.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }

        if (base.getWorld() == null || !base.getWorld().getName().equalsIgnoreCase(trainingWorldName)) {
            base.setWorld(trainingWorld);
        }
        return base;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBridgePlace(BlockPlaceEvent event) {
        ActiveAttempt attempt = activeByPlayer.get(event.getPlayer().getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.BRIDGE) {
            return;
        }

        // Keep bridge arena constrained so players cannot pollute nearby regions.
        ChallengeDefinition definition = definitions.get(TrainingChallengeType.BRIDGE);
        if (definition != null
                && !definition.region().contains(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    private void startAttempt(Player player, TrainingChallengeType type) {
        startAttempt(player, type, true);
    }

    private boolean startAttempt(Player player, TrainingChallengeType type, boolean teleportToStartLocation) {
        if (!isInTrainingWorld(player)) {
            log.sendWarn(player, "Enter the training gym first with /challenge train.");
            return false;
        }

        ActiveAttempt existingByPlayer = activeByPlayer.get(player.getUniqueId());
        if (existingByPlayer != null) {
            log.sendWarn(player, "You are already in an active attempt.");
            return false;
        }

        ActiveAttempt existingByChallenge = activeByChallenge.get(type);
        if (existingByChallenge != null && !existingByChallenge.playerId().equals(player.getUniqueId())) {
            Player occupant = Bukkit.getPlayer(existingByChallenge.playerId());
            String occupantName = occupant == null ? "another player" : occupant.getName();
            log.sendWarn(player, type.displayName() + " arena is currently in use by " + occupantName + ".");
            return false;
        }

        ChallengeDefinition definition = definitions.get(type);
        if (definition == null) {
            log.sendWarn(player, "That challenge is not configured.");
            return false;
        }

        if (returnItemService != null) {
            returnItemService.onPlayerEnterTraining(player);
        }

        if (type == TrainingChallengeType.CHEST) {
            if (!arenaSnapshots.containsKey(type)) {
                clearDynamicChestsInRegion(definition);
            } else {
                World chestWorld = Bukkit.getWorld(definition.region().worldName());
                if (chestWorld != null) {
                    clearHopperInventories(definition, chestWorld);
                }
            }
        }

        if (type == TrainingChallengeType.PORTAL || !arenaSnapshots.containsKey(type)) {
            arenaSnapshots.put(type, snapshotRegion(definition.region()));
        }
        if (type == TrainingChallengeType.PORTAL) {
            trackedPortalLavaByChallenge.put(type, snapshotLavaBlocks(definition.region()));
        }

        CraftObjective craftObjective = null;
        if (type == TrainingChallengeType.CRAFT) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            List<String> pool = new ArrayList<>(List.of("beds", "eyes", "axe", "shovel"));
            Collections.shuffle(pool, rng);
            Set<String> selected = new HashSet<>(pool.subList(0, rng.nextInt(2, 4)));
            boolean reqBeds = selected.contains("beds");
            boolean reqEyes = selected.contains("eyes");
            int bedsTarget = reqBeds ? rng.nextInt(definition.minBeds(), definition.maxBeds() + 1) : 0;
            int eyesTarget = reqEyes ? rng.nextInt(definition.minEyes(), definition.maxEyes() + 1) : 0;
            craftObjective = new CraftObjective(
                    reqBeds, reqEyes, selected.contains("axe"), selected.contains("shovel"), bedsTarget, eyesTarget);
        }

        ChestObjective chestObjective = null;
        if (type == TrainingChallengeType.CHEST) {
            chestObjective = generateChestObjective();
        }

        Location bridgeDestinationPlate = null;
        Location bridgeStartLocation = null;
        if (type == TrainingChallengeType.BRIDGE) {
            List<BridgePlatform> platforms = definition.bridgePlatforms();
            if (platforms.size() >= 2) {
                int startIdx = ThreadLocalRandom.current().nextInt(platforms.size());
                int destIdx;
                do {
                    destIdx = ThreadLocalRandom.current().nextInt(platforms.size());
                } while (destIdx == startIdx);
                bridgeStartLocation = platforms.get(startIdx).spawnLocation();
                bridgeDestinationPlate = platforms.get(destIdx).pressurePlate();
            }
        }

        prepareLoadout(player, type, craftObjective, chestObjective, definition);

        ActiveAttempt attempt = new ActiveAttempt(
                player.getUniqueId(),
                type,
                System.currentTimeMillis(),
                craftObjective,
                chestObjective,
                bridgeDestinationPlate);
        activeByChallenge.put(type, attempt);
        activeByPlayer.put(player.getUniqueId(), attempt);
        hideStartButton(definition, type);

        if (bridgeDestinationPlate != null) {
            startBridgeVisuals(player, bridgeDestinationPlate);
        }

        if (teleportToStartLocation) {
            Location startLoc = bridgeStartLocation != null ? bridgeStartLocation : definition.startLocation();
            player.teleport(startLoc);
        }
        player.stopAllSounds();
        player.playSound(player.getLocation(), TRAINING_MUSIC_SOUND, SoundCategory.MUSIC, 1.0f, 1.0f);
        log.sendInfo(player, ChatColor.GREEN + type.displayName() + " challenge started.");
        return true;
    }

    private void prepareLoadout(
            Player player,
            TrainingChallengeType type,
            CraftObjective craftObjective,
            ChestObjective chestObjective,
            ChallengeDefinition definition) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();

        switch (type) {
            case PORTAL -> {
                inventory.addItem(new ItemStack(Material.WATER_BUCKET, 1));
                inventory.addItem(new ItemStack(Material.DIRT, 6));
                ItemStack flintAndSteel = new ItemStack(Material.FLINT_AND_STEEL, 1);
                if (flintAndSteel.getItemMeta() instanceof Damageable damageable) {
                    damageable.setDamage(0);
                    flintAndSteel.setItemMeta(damageable);
                }
                inventory.addItem(flintAndSteel);
            }
            case CRAFT -> {
                if (craftObjective == null) {
                    break;
                }
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                List<ItemStack> stacks = new ArrayList<>();

                if (craftObjective.requireBeds) {
                    int wool = craftObjective.bedsTarget * 3 + rng.nextInt(0, craftObjective.bedsTarget / 2 + 2);
                    int planks = craftObjective.bedsTarget * 3 + rng.nextInt(0, craftObjective.bedsTarget / 2 + 2);
                    addScatteredStacks(stacks, Material.WHITE_WOOL, wool, rng);
                    addScatteredStacks(stacks, Material.OAK_PLANKS, planks, rng);
                }

                if (craftObjective.requireEyes) {
                    int pearls = craftObjective.eyesTarget + rng.nextInt(0, craftObjective.eyesTarget / 3 + 2);
                    int blaze = craftObjective.eyesTarget + rng.nextInt(0, craftObjective.eyesTarget / 3 + 2);
                    addScatteredStacks(stacks, Material.ENDER_PEARL, pearls, rng);
                    addScatteredStacks(stacks, Material.BLAZE_POWDER, blaze, rng);
                }

                int ironNeeded = (craftObjective.requireAxe ? 3 : 0) + (craftObjective.requireShovel ? 1 : 0);
                int sticksNeeded = (craftObjective.requireAxe ? 2 : 0) + (craftObjective.requireShovel ? 2 : 0);
                if (ironNeeded > 0) {
                    addScatteredStacks(stacks, Material.IRON_INGOT, ironNeeded + rng.nextInt(0, 3), rng);
                    addScatteredStacks(stacks, Material.STICK, sticksNeeded + rng.nextInt(0, 3), rng);
                }

                Collections.shuffle(stacks, rng);
                placeStacksRandomly(
                        inventory, stacks, Set.of(TrainingReturnItemService.RETURN_ITEM_SLOT, 36, 37, 38, 39, 40));
            }
            case BRIDGE -> inventory.addItem(new ItemStack(Material.DIRT, 64));
            case CHEST -> {
                if (chestObjective != null) {
                    spawnChestsWithLoot(definition, chestObjective);
                }
            }
        }
    }

    private String buildActiveActionBar(ActiveAttempt attempt) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - attempt.startedAtMillis());
        String objective = objectiveText(attempt);
        return attempt.type().displayName() + " | Objective: " + objective + " | " + formatDurationMmSsCc(elapsed);
    }

    private String objectiveText(ActiveAttempt attempt) {
        return switch (attempt.type()) {
            case PORTAL -> "Light a valid portal";
            case CHEST -> {
                ChestObjective chest = attempt.chestObjective();
                if (chest == null) {
                    yield "Deposit required items in hopper";
                }
                ChallengeDefinition def = definitions.get(TrainingChallengeType.CHEST);
                if (def != null && def.hopperLocation() != null) {
                    BlockState bs = def.hopperLocation().getBlock().getState();
                    if (bs instanceof Container c) {
                        yield "Hopper: " + chest.remainingText(c.getInventory());
                    }
                }
                yield "Need: " + chest.requiredItemsText();
            }
            case BRIDGE -> {
                Location dest = attempt.bridgeDestinationPlate();
                if (dest != null) {
                    yield "Bridge to (" + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ() + ")";
                }
                yield "Reach destination pressure plate";
            }
            case CRAFT -> {
                CraftObjective craft = attempt.craftObjective();
                if (craft == null) {
                    yield "Craft objective";
                }
                List<String> parts = new ArrayList<>();
                if (craft.requireBeds) {
                    parts.add("Beds " + craft.craftedBeds + "/" + craft.bedsTarget);
                }
                if (craft.requireEyes) {
                    parts.add("Eyes " + craft.craftedEyes + "/" + craft.eyesTarget);
                }
                if (craft.requireAxe) {
                    parts.add(craft.craftedAxe ? "Axe" : "Axe?");
                }
                if (craft.requireShovel) {
                    parts.add(craft.craftedShovel ? "Shovel" : "Shovel?");
                }
                yield String.join(", ", parts);
            }
        };
    }

    private void maybeCompleteChestAttempt(Player player) {
        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null || attempt.type() != TrainingChallengeType.CHEST || attempt.chestObjective() == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(TrainingChallengeType.CHEST);
        if (definition == null || definition.hopperLocation() == null) {
            return;
        }

        BlockState state = definition.hopperLocation().getBlock().getState();
        if (!(state instanceof Container container)) {
            return;
        }

        if (attempt.chestObjective().isHopperComplete(container.getInventory())) {
            completeAttempt(player, attempt);
        }
    }

    private void startBridgeVisuals(Player player, Location destinationPlate) {
        player.setCompassTarget(destinationPlate);

        double cx = destinationPlate.getBlockX() + 0.5;
        double cz = destinationPlate.getBlockZ() + 0.5;
        double baseY = destinationPlate.getBlockY() + 1.0;
        World world = destinationPlate.getWorld();

        BukkitTask task = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (world == null
                                    || !world.isChunkLoaded(
                                            destinationPlate.getBlockX() >> 4, destinationPlate.getBlockZ() >> 4)) {
                                return;
                            }
                            for (int i = 0; i < 6; i++) {
                                world.spawnParticle(Particle.END_ROD, cx, baseY + i, cz, 1, 0.0, 0.0, 0.0, 0.0);
                            }
                        },
                        0L,
                        3L);

        BukkitTask old = bridgeParticleTaskByPlayer.put(player.getUniqueId(), task);
        if (old != null) {
            old.cancel();
        }
    }

    private void stopBridgeVisuals(Player player) {
        BukkitTask task = bridgeParticleTaskByPlayer.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        World world = player.getWorld();
        if (world != null) {
            player.setCompassTarget(world.getSpawnLocation());
        }
    }

    private void completeAttempt(Player player, ActiveAttempt attempt) {
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - attempt.startedAtMillis());
        statsStore.recordCompletedAttempt(player.getUniqueId(), attempt.type(), elapsedMs);
        statsStore.save();

        stopBridgeVisuals(player);
        restoreAttemptArenaState(attempt.type());
        clearAttempt(player, attempt.type());

        player.closeInventory();
        ItemStack slot8 = player.getInventory().getItem(TrainingReturnItemService.RETURN_ITEM_SLOT);
        boolean hasReturnItem = returnItemService != null && returnItemService.isReturnItem(slot8);
        clearAttemptInventoryAndForceSurvival(player);
        if (hasReturnItem) {
            player.getInventory().setItem(TrainingReturnItemService.RETURN_ITEM_SLOT, slot8);
        }

        if (attempt.type() == TrainingChallengeType.PORTAL) {
            clearNetherPortalBlocksInRegion(TrainingChallengeType.PORTAL);
            refillTrackedPortalLava(TrainingChallengeType.PORTAL);
            Bukkit.getScheduler().runTask(plugin, () -> {
                clearNetherPortalBlocksInRegion(TrainingChallengeType.PORTAL);
                refillTrackedPortalLava(TrainingChallengeType.PORTAL);
            });
        }
        player.stopSound(TRAINING_MUSIC_SOUND, SoundCategory.MUSIC);
        teleportToStartButton(player, attempt.type());
        playCompletionJingle(player);
        player.sendActionBar(Component.empty());

        log.sendInfo(
                player,
                ChatColor.GREEN + attempt.type().displayName() + " complete in " + formatDurationMmSsCc(elapsedMs)
                        + ".");
    }

    private void cancelAttempt(Player player, String message) {
        ActiveAttempt attempt = activeByPlayer.get(player.getUniqueId());
        if (attempt == null) {
            return;
        }

        player.stopSound(TRAINING_MUSIC_SOUND, SoundCategory.MUSIC);
        stopBridgeVisuals(player);
        restoreAttemptArenaState(attempt.type());
        clearAttempt(player, attempt.type());
        player.sendActionBar(Component.empty());

        if (returnItemService != null) {
            returnItemService.onPlayerLeaveTraining(player);
        }

        if (message != null && !message.isBlank()) {
            log.sendWarn(player, message);
        }
    }

    private void restoreAttemptArenaState(TrainingChallengeType type) {
        if (type == null) {
            return;
        }

        if (type == TrainingChallengeType.PORTAL) {
            clearNetherPortalBlocksInRegion(type);
            refillTrackedPortalLava(type);
        }

        ChallengeDefinition chestDefinition = null;
        World chestWorld = null;
        if (type == TrainingChallengeType.CHEST) {
            chestDefinition = definitions.get(type);
            if (chestDefinition != null) {
                chestWorld = Bukkit.getWorld(chestDefinition.region().worldName());
                if (chestWorld != null) {
                    clearHopperInventories(chestDefinition, chestWorld);
                }
            }
        }

        restoreArena(type);

        // Snapshot restoration can repopulate hopper inventory, so clear again
        // afterwards.
        if (type == TrainingChallengeType.CHEST && chestDefinition != null && chestWorld != null) {
            clearHopperInventories(chestDefinition, chestWorld);
            ChallengeDefinition finalChestDefinition = chestDefinition;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                World liveWorld = Bukkit.getWorld(
                                        finalChestDefinition.region().worldName());
                                if (liveWorld != null) {
                                    clearHopperInventories(finalChestDefinition, liveWorld);
                                }
                            },
                            2L);
        }
    }

    private void clearAttempt(Player player, TrainingChallengeType type) {
        activeByPlayer.remove(player.getUniqueId());
        ActiveAttempt byChallenge = activeByChallenge.get(type);
        if (byChallenge != null && byChallenge.playerId().equals(player.getUniqueId())) {
            activeByChallenge.remove(type);
        }
        restoreStartButton(type);
    }

    private Optional<TrainingChallengeType> resolveChallengeByStartButton(Location location) {
        for (Map.Entry<TrainingChallengeType, ChallengeDefinition> entry : definitions.entrySet()) {
            if (sameBlock(location, entry.getValue().startButton())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private Optional<TrainingChallengeType> resolveChallengeByLocation(Location location) {
        List<Map.Entry<TrainingChallengeType, ChallengeDefinition>> matches = definitions.entrySet().stream()
                .filter(entry -> entry.getValue().containsSidebarLocation(location))
                .sorted(Comparator.comparing(entry -> entry.getKey().ordinal()))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0).getKey());
    }

    private boolean isInTrainingWorld(Player player) {
        World world = player.getWorld();
        return world != null && world.getName().equalsIgnoreCase(trainingWorldName);
    }

    private ArenaSnapshot snapshotRegion(Cuboid cuboid) {
        World world = Bukkit.getWorld(cuboid.worldName());
        if (world == null) {
            return new ArenaSnapshot(cuboid.worldName(), Map.of());
        }

        Map<BlockKey, BlockSnapshot> blocks = new LinkedHashMap<>();
        for (int x = cuboid.minX(); x <= cuboid.maxX(); x++) {
            for (int y = cuboid.minY(); y <= cuboid.maxY(); y++) {
                for (int z = cuboid.minZ(); z <= cuboid.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockState state = block.getState();

                    ItemStack[] inventory = null;
                    if (state instanceof Container container) {
                        inventory = cloneContents(container.getInventory().getContents());
                    }

                    blocks.put(
                            new BlockKey(x, y, z),
                            new BlockSnapshot(
                                    block.getType(), block.getBlockData().clone(), inventory));
                }
            }
        }

        return new ArenaSnapshot(cuboid.worldName(), blocks);
    }

    private void restoreArena(TrainingChallengeType type) {
        ArenaSnapshot snapshot = arenaSnapshots.get(type);
        if (snapshot == null) {
            return;
        }

        World world = Bukkit.getWorld(snapshot.worldName());
        if (world == null) {
            return;
        }

        for (Map.Entry<BlockKey, BlockSnapshot> entry : snapshot.blocks().entrySet()) {
            BlockKey key = entry.getKey();
            BlockSnapshot value = entry.getValue();
            Block block = world.getBlockAt(key.x(), key.y(), key.z());

            // Prevent container contents from dropping as entities when the block is
            // replaced.
            BlockState currentState = block.getState();
            if (currentState instanceof Container currentContainer) {
                currentContainer.getInventory().clear();
                currentContainer.update(true, false);
            }

            block.setType(value.material(), false);
            block.setBlockData(value.blockData().clone(), false);

            if (value.inventoryContents() != null) {
                BlockState state = block.getState();
                if (state instanceof Container container) {
                    container.getInventory().setContents(cloneContents(value.inventoryContents()));
                    container.update(true, false);
                }
            }
        }
    }

    private ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : original[i].clone();
        }
        return copy;
    }

    private void clearAttemptInventoryAndForceSurvival(Player player) {
        if (player == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        player.setItemOnCursor(null);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.updateInventory();
    }

    private void hideStartButton(ChallengeDefinition definition, TrainingChallengeType type) {
        if (definition == null || definition.startButton() == null) {
            return;
        }

        Location buttonLocation = definition.startButton();
        World world = buttonLocation.getWorld();
        if (world == null) {
            return;
        }

        Block block = world.getBlockAt(buttonLocation);
        if (!hiddenStartButtonSnapshots.containsKey(type)) {
            hiddenStartButtonSnapshots.put(type, snapshotBlock(block));
        }

        // Run next tick so the pressed-button state update cannot immediately reapply.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!activeByChallenge.containsKey(type)) {
                return;
            }

            Block liveBlock = world.getBlockAt(buttonLocation);
            liveBlock.setType(Material.AIR, false);
        });
    }

    private void checkPortalAttemptCompletion() {
        ActiveAttempt attempt = activeByChallenge.get(TrainingChallengeType.PORTAL);
        if (attempt == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(TrainingChallengeType.PORTAL);
        if (definition == null || !regionHasPortalBlocks(definition.region())) {
            return;
        }

        Player player = Bukkit.getPlayer(attempt.playerId());
        if (player != null) {
            completeAttempt(player, attempt);
        }
    }

    private boolean regionHasPortalBlocks(Cuboid region) {
        World world = Bukkit.getWorld(region.worldName());
        if (world == null) {
            return false;
        }

        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Set<BlockKey> snapshotLavaBlocks(Cuboid region) {
        World world = Bukkit.getWorld(region.worldName());
        if (world == null) {
            return Set.of();
        }

        Set<BlockKey> lavaBlocks = new HashSet<>();

        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.LAVA) {
                        lavaBlocks.add(new BlockKey(x, y, z));
                    }
                }
            }
        }

        return lavaBlocks;
    }

    private void resetAllChallengeArenas() {
        for (Map.Entry<TrainingChallengeType, ChallengeDefinition> entry : definitions.entrySet()) {
            TrainingChallengeType type = entry.getKey();
            ChallengeDefinition definition = entry.getValue();

            switch (type) {
                case PORTAL -> clearNetherPortalBlocksInRegion(type);
                case CHEST -> clearDynamicChestsInRegion(definition);
                default -> {}
            }

            arenaSnapshots.put(type, snapshotRegion(definition.region()));

            if (definition.startButton() != null && definition.startButton().getWorld() != null) {
                Block btn = definition.startButton().getBlock();
                if (btn.getType() == Material.AIR) {
                    log.warn("Start button for challenge '" + type.key()
                            + "' is missing at " + formatBlockLocation(definition.startButton())
                            + " — replace it manually.");
                } else {
                    hiddenStartButtonSnapshots.put(
                            type,
                            new BlockSnapshot(btn.getType(), btn.getBlockData().clone(), null));
                }
            }
        }
    }

    private void clearDynamicChestsInRegion(ChallengeDefinition definition) {
        World world = Bukkit.getWorld(definition.region().worldName());
        if (world == null) {
            return;
        }
        clearHopperInventories(definition, world);
        Cuboid region = definition.region();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.CHEST) {
                        continue;
                    }
                    Location loc = block.getLocation();
                    if (definition.hopperLocation() != null && sameBlock(loc, definition.hopperLocation())) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void clearHopperInventories(ChallengeDefinition definition, World world) {
        if (definition == null || world == null) {
            return;
        }

        Cuboid region = definition.region();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.HOPPER) {
                        continue;
                    }
                    BlockState state = block.getState();
                    if (state instanceof Container container) {
                        container.getInventory().clear();
                    }
                }
            }
        }

        Location hopperLocation = definition.hopperLocation();
        if (hopperLocation == null || hopperLocation.getWorld() == null) {
            return;
        }

        BlockState configuredHopper = hopperLocation.getBlock().getState();
        if (configuredHopper instanceof Container container) {
            container.getInventory().clear();
            container.update(true, false);
        }
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static String formatBlockLocation(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void refillTrackedPortalLava(TrainingChallengeType type) {
        Set<BlockKey> trackedLava = trackedPortalLavaByChallenge.get(type);
        if (trackedLava == null || trackedLava.isEmpty()) {
            return;
        }

        ChallengeDefinition definition = definitions.get(type);
        if (definition == null) {
            return;
        }

        World world = Bukkit.getWorld(definition.region().worldName());
        if (world == null) {
            return;
        }

        for (BlockKey key : trackedLava) {
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() != Material.LAVA) {
                block.setType(Material.LAVA, false);
            }
        }
    }

    private void clearNetherPortalBlocksInRegion(TrainingChallengeType type) {
        ChallengeDefinition definition = definitions.get(type);
        if (definition == null) {
            return;
        }

        World world = Bukkit.getWorld(definition.region().worldName());
        if (world == null) {
            return;
        }

        Cuboid region = definition.region();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.NETHER_PORTAL) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private ChestObjective generateChestObjective() {
        record LootEntry(Material material, int min, int max) {}
        List<LootEntry> pool = new ArrayList<>(List.of(
                new LootEntry(Material.IRON_INGOT, 4, 10),
                new LootEntry(Material.OBSIDIAN, 2, 6),
                new LootEntry(Material.ENDER_PEARL, 3, 8),
                new LootEntry(Material.BLAZE_ROD, 2, 5),
                new LootEntry(Material.FLINT, 5, 12),
                new LootEntry(Material.GOLD_INGOT, 3, 8),
                new LootEntry(Material.LEATHER, 2, 6),
                new LootEntry(Material.STRING, 4, 10)));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int count = ThreadLocalRandom.current().nextInt(2, 5);
        Map<Material, Integer> required = new LinkedHashMap<>();
        for (int i = 0; i < count && i < pool.size(); i++) {
            LootEntry e = pool.get(i);
            required.put(e.material(), ThreadLocalRandom.current().nextInt(e.min(), e.max() + 1));
        }
        return new ChestObjective(required);
    }

    private void spawnChestsWithLoot(ChallengeDefinition definition, ChestObjective objective) {
        World world = Bukkit.getWorld(definition.region().worldName());
        if (world == null) {
            return;
        }

        if (definition.hopperLocation() == null) {
            return;
        }

        int floorY = definition.hopperLocation().getBlockY();
        int hopperX = definition.hopperLocation().getBlockX();
        int hopperZ = definition.hopperLocation().getBlockZ();
        Cuboid region = definition.region();

        // Flood-fill from the air tiles around the hopper outward at floorY.
        // Solid wall blocks stop the fill, so only positions inside the ring are found.
        List<int[]> validPositions = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        java.util.Queue<int[]> queue = new java.util.ArrayDeque<>();
        int[][] startTiles = {
            {hopperX + 1, hopperZ},
            {hopperX - 1, hopperZ},
            {hopperX, hopperZ + 1},
            {hopperX, hopperZ - 1}
        };
        for (int[] startTile : startTiles) {
            long key = packXZ(startTile[0], startTile[1]);
            if (visited.add(key)) {
                queue.add(startTile);
            }
        }

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int cx = pos[0];
            int cz = pos[1];

            if (cx < region.minX() || cx > region.maxX() || cz < region.minZ() || cz > region.maxZ()) {
                continue;
            }

            if (!world.getBlockAt(cx, floorY, cz).getType().isAir()) {
                continue;
            }

            if (cx != hopperX || cz != hopperZ) {
                validPositions.add(new int[] {cx, floorY, cz});
            }

            int[][] neighbours = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
            for (int[] n : neighbours) {
                long key = packXZ(n[0], n[1]);
                if (visited.add(key)) {
                    queue.add(n);
                }
            }
        }

        if (validPositions.isEmpty()) {
            return;
        }

        int numChests = ThreadLocalRandom.current().nextInt(definition.minChests(), definition.maxChests() + 1);
        numChests = Math.min(numChests, validPositions.size());
        Collections.shuffle(validPositions, ThreadLocalRandom.current());

        Material[] fillerPool = {
            Material.DIRT, Material.GRAVEL, Material.ROTTEN_FLESH, Material.ARROW,
            Material.BONE, Material.COBBLESTONE, Material.SAND, Material.OAK_LOG,
            Material.WHEAT, Material.GUNPOWDER
        };

        List<ItemStack> requiredStacks = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : objective.requiredItems().entrySet()) {
            requiredStacks.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        Collections.shuffle(requiredStacks, ThreadLocalRandom.current());

        List<Inventory> chestInventories = new ArrayList<>();
        for (int i = 0; i < numChests; i++) {
            int[] pos = validPositions.get(i);
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            block.setType(Material.CHEST, false);
            BlockState state = block.getState();
            if (state instanceof Container container) {
                chestInventories.add(container.getInventory());
            }
        }

        for (int i = 0; i < requiredStacks.size() && !chestInventories.isEmpty(); i++) {
            Inventory chestInventory = chestInventories.get(i % chestInventories.size());
            placeStacksRandomly(chestInventory, splitStackSometimes(requiredStacks.get(i)), Set.of());
        }

        for (Inventory inv : chestInventories) {
            int fillerCount = ThreadLocalRandom.current().nextInt(2, 6);
            List<ItemStack> fillerStacks = new ArrayList<>();
            for (int i = 0; i < fillerCount; i++) {
                Material filler = fillerPool[ThreadLocalRandom.current().nextInt(fillerPool.length)];
                fillerStacks.add(
                        new ItemStack(filler, ThreadLocalRandom.current().nextInt(1, 17)));
            }
            placeStacksRandomly(inv, fillerStacks, Set.of());
        }
    }

    private void addScatteredStacks(List<ItemStack> dest, Material material, int total, ThreadLocalRandom rng) {
        int numStacks = total > 1 ? rng.nextInt(2, 4) : 1;
        for (int i = 0; i < numStacks - 1 && total > 1; i++) {
            int chunk = rng.nextInt(1, total);
            dest.add(new ItemStack(material, chunk));
            total -= chunk;
        }
        dest.add(new ItemStack(material, total));
    }

    private List<ItemStack> splitStackSometimes(ItemStack stack) {
        if (stack == null
                || stack.getAmount() <= 1
                || !ThreadLocalRandom.current().nextBoolean()) {
            return List.of(stack == null ? null : stack.clone());
        }

        int totalAmount = stack.getAmount();
        int firstAmount = ThreadLocalRandom.current().nextInt(1, totalAmount);
        ItemStack first = stack.clone();
        first.setAmount(firstAmount);

        ItemStack second = stack.clone();
        second.setAmount(totalAmount - firstAmount);

        return List.of(first, second);
    }

    private void placeStacksRandomly(Inventory inventory, List<ItemStack> stacks, Set<Integer> excludedSlots) {
        if (inventory == null || stacks == null || stacks.isEmpty()) {
            return;
        }

        List<Integer> availableSlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (excludedSlots != null && excludedSlots.contains(slot)) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            availableSlots.add(slot);
        }
        Collections.shuffle(availableSlots, ThreadLocalRandom.current());

        int stackIndex = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (stackIndex >= availableSlots.size()) {
                break;
            }
            inventory.setItem(availableSlots.get(stackIndex), stack);
            stackIndex++;
        }
    }

    private static String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private int computeShiftClickMultiplier(ItemStack[] matrix) {
        int min = Integer.MAX_VALUE;
        for (ItemStack slot : matrix) {
            if (slot != null && slot.getType() != Material.AIR) {
                min = Math.min(min, slot.getAmount());
            }
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    private void teleportToStartButton(Player player, TrainingChallengeType type) {
        ChallengeDefinition definition = definitions.get(type);
        Location destination = null;
        if (definition != null && definition.startButton() != null) {
            destination = definition.startButton().clone().add(0.5D, 0.0D, 0.5D);
        }

        if (destination == null || destination.getWorld() == null) {
            destination = resolveTrainingRespawnLocation();
        }

        if (destination != null && destination.getWorld() != null) {
            player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    private void playCompletionJingle(Player player) {
        float[] pitches = {1.0f, 1.26f, 1.587f, 2.0f};
        int[] delayTicks = {0, 3, 6, 10};
        for (int i = 0; i < pitches.length; i++) {
            final float pitch = pitches[i];
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch),
                            delayTicks[i]);
        }
    }

    private void restoreStartButton(TrainingChallengeType type) {
        BlockSnapshot snapshot = hiddenStartButtonSnapshots.remove(type);
        if (snapshot == null) {
            return;
        }

        ChallengeDefinition definition = definitions.get(type);
        if (definition == null
                || definition.startButton() == null
                || definition.startButton().getWorld() == null) {
            return;
        }

        Block block = definition.startButton().getWorld().getBlockAt(definition.startButton());
        block.setType(snapshot.material(), false);
        block.setBlockData(snapshot.blockData().clone(), false);
    }

    private BlockSnapshot snapshotBlock(Block block) {
        BlockState state = block.getState();
        ItemStack[] inventory = null;
        if (state instanceof Container container) {
            inventory = cloneContents(container.getInventory().getContents());
        }

        return new BlockSnapshot(block.getType(), block.getBlockData().clone(), inventory);
    }

    private List<String> filterByPrefix(String typed, List<String> options) {
        String normalized = typed.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .collect(Collectors.toList());
    }

    private String formatOptionalDuration(long durationMs) {
        return durationMs < 0L ? "--:--.--" : formatDurationMmSsCc(durationMs);
    }

    private String formatDurationMmSsCc(long durationMs) {
        long clamped = Math.max(0L, durationMs);
        long centiseconds = clamped / 10L;
        long minutes = centiseconds / 6000L;
        long seconds = (centiseconds / 100L) % 60L;
        long cs = centiseconds % 100L;
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, cs);
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().getName().equalsIgnoreCase(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private Optional<Location> readLocation(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }

        String worldName = section.getString("world", trainingWorldName);
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }

    private Optional<Cuboid> readCuboid(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }

        String worldName = section.getString("world", trainingWorldName);
        if (worldName == null || Bukkit.getWorld(worldName) == null) {
            return Optional.empty();
        }

        ConfigurationSection minSection = section.getConfigurationSection("min");
        ConfigurationSection maxSection = section.getConfigurationSection("max");
        if (minSection == null || maxSection == null) {
            return Optional.empty();
        }

        int minX = minSection.getInt("x");
        int minY = minSection.getInt("y");
        int minZ = minSection.getInt("z");
        int maxX = maxSection.getInt("x");
        int maxY = maxSection.getInt("y");
        int maxZ = maxSection.getInt("z");

        return Optional.of(new Cuboid(worldName, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private List<BridgePlatform> readBridgePlatforms(FileConfiguration config, String basePath) {
        ConfigurationSection section = config.getConfigurationSection(basePath + ".platforms");
        if (section == null) {
            return List.of();
        }
        List<BridgePlatform> platforms = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String path = basePath + ".platforms." + key;
            Optional<Location> spawn = readLocation(config, path + ".spawn");
            Optional<Location> plate = readLocation(config, path + ".plate");
            if (spawn.isPresent() && plate.isPresent()) {
                platforms.add(new BridgePlatform(spawn.get(), plate.get()));
            } else {
                log.warn("Bridge platform '" + key + "' missing spawn or plate location — skipped.");
            }
        }
        return Collections.unmodifiableList(platforms);
    }

    private void applyBlankSidebarNumberFormat(Objective objective) {
        if (objective == null) {
            return;
        }

        try {
            Class<?> numberFormatClass = Class.forName("org.bukkit.scoreboard.NumberFormat");
            Object blankFormat = numberFormatClass.getMethod("blank").invoke(null);
            objective.getClass().getMethod("setNumberFormat", numberFormatClass).invoke(objective, blankFormat);
        } catch (ReflectiveOperationException ignored) {
            // Older APIs may not support objective number formatting.
        }
    }

    private boolean isBedMaterial(Material material) {
        return material.name().endsWith("_BED");
    }

    private boolean isMetalAxe(Material material) {
        return Set.of(Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE)
                .contains(material);
    }

    private boolean isMetalShovel(Material material) {
        return Set.of(Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL)
                .contains(material);
    }

    private static final class ChestObjective {
        private final Map<Material, Integer> requiredItems;

        private ChestObjective(Map<Material, Integer> requiredItems) {
            this.requiredItems = requiredItems;
        }

        private Map<Material, Integer> requiredItems() {
            return requiredItems;
        }

        private boolean isHopperComplete(Inventory hopperInventory) {
            for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
                int found = 0;
                for (ItemStack item : hopperInventory.getContents()) {
                    if (item != null && item.getType() == entry.getKey()) {
                        found += item.getAmount();
                    }
                }
                if (found < entry.getValue()) {
                    return false;
                }
            }
            return true;
        }

        private String requiredItemsText() {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
                parts.add(entry.getValue() + "x " + formatMaterialName(entry.getKey()));
            }
            return String.join(", ", parts);
        }

        private String remainingText(Inventory hopperInventory) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
                int found = 0;
                for (ItemStack item : hopperInventory.getContents()) {
                    if (item != null && item.getType() == entry.getKey()) {
                        found += item.getAmount();
                    }
                }
                int remaining = Math.max(0, entry.getValue() - found);
                if (remaining > 0) {
                    parts.add(remaining + "x " + formatMaterialName(entry.getKey()));
                }
            }
            return parts.isEmpty() ? "All items deposited!" : String.join(", ", parts);
        }
    }

    private static final class CraftObjective {
        private final boolean requireBeds;
        private final boolean requireEyes;
        private final boolean requireAxe;
        private final boolean requireShovel;
        private final int bedsTarget;
        private final int eyesTarget;
        private int craftedBeds;
        private int craftedEyes;
        private boolean craftedAxe;
        private boolean craftedShovel;

        private CraftObjective(
                boolean requireBeds,
                boolean requireEyes,
                boolean requireAxe,
                boolean requireShovel,
                int bedsTarget,
                int eyesTarget) {
            this.requireBeds = requireBeds;
            this.requireEyes = requireEyes;
            this.requireAxe = requireAxe;
            this.requireShovel = requireShovel;
            this.bedsTarget = bedsTarget;
            this.eyesTarget = eyesTarget;
        }

        private boolean isComplete() {
            return (!requireBeds || craftedBeds >= bedsTarget)
                    && (!requireEyes || craftedEyes >= eyesTarget)
                    && (!requireAxe || craftedAxe)
                    && (!requireShovel || craftedShovel);
        }
    }

    private record ActiveAttempt(
            UUID playerId,
            TrainingChallengeType type,
            long startedAtMillis,
            CraftObjective craftObjective,
            ChestObjective chestObjective,
            Location bridgeDestinationPlate) {}

    private record BridgePlatform(Location spawnLocation, Location pressurePlate) {}

    private record ChallengeDefinition(
            TrainingChallengeType type,
            Cuboid region,
            Cuboid sidebarRegion,
            Location startButton,
            Location startLocation,
            Location completionPressurePlate,
            Location hopperLocation,
            int minChests,
            int maxChests,
            int minBeds,
            int maxBeds,
            int minEyes,
            int maxEyes,
            List<BridgePlatform> bridgePlatforms) {
        private ChallengeDefinition {}

        private boolean containsSidebarLocation(Location location) {
            if (sidebarRegion != null) {
                return sidebarRegion.contains(location);
            }
            return region.contains(location);
        }
    }

    private record Cuboid(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private Cuboid {
            int resolvedMinX = Math.min(minX, maxX);
            int resolvedMinY = Math.min(minY, maxY);
            int resolvedMinZ = Math.min(minZ, maxZ);
            int resolvedMaxX = Math.max(minX, maxX);
            int resolvedMaxY = Math.max(minY, maxY);
            int resolvedMaxZ = Math.max(minZ, maxZ);

            minX = resolvedMinX;
            minY = resolvedMinY;
            minZ = resolvedMinZ;
            maxX = resolvedMaxX;
            maxY = resolvedMaxY;
            maxZ = resolvedMaxZ;
            worldName = Objects.requireNonNull(worldName, "worldName");
        }

        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
                return false;
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private record ArenaSnapshot(String worldName, Map<BlockKey, BlockSnapshot> blocks) {}

    private record BlockKey(int x, int y, int z) {}

    private record BlockSnapshot(
            Material material, org.bukkit.block.data.BlockData blockData, ItemStack[] inventoryContents) {}
}
