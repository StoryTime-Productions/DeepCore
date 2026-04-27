package dev.deepcore.challenge.world;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Manages world lifecycle operations for run resets and lobby/disco behavior.
 */
public final class WorldResetManager {
    private static final String LIMBO_WORLD_NAME_PATH = "reset.limbo-world-name";
    private static final String LOBBY_OVERWORLD_WORLD_NAME_PATH = "reset.lobby-overworld-world-name";
    private static final String LOBBY_NETHER_WORLD_NAME_PATH = "reset.lobby-nether-world-name";
    private static final String TRAINING_WORLD_NAME_PATH = "training.world";
    private static final String PREVIEW_ANCHOR_X_PATH = "challenge.preview_hologram_anchor.x";
    private static final String PREVIEW_ANCHOR_Y_PATH = "challenge.preview_hologram_anchor.y";
    private static final String PREVIEW_ANCHOR_Z_PATH = "challenge.preview_hologram_anchor.z";
    private static final String PREVIEW_ANCHOR_ENABLED_PATH = "challenge.preview_hologram_anchor.enabled";
    private static final String PREVIEW_ANCHOR_WORLDS_PATH = "challenge.preview_hologram_anchor.worlds";
    private static final String LOBBY_SPAWN_IN_LIMBO_PATH = "challenge.lobby_spawn_in_limbo_by_default";
    private static final String DISCO_WORLD_CHANCE_PATH = "reset.disco-world-chance";
    private static final String DISCO_BALL_TEXTURE_URL =
            "http://textures.minecraft.net/texture/17d7b3c8e5735b0da3db74abfdce870c2e904689f5cb2c29bcd2a51ed71fddff";
    private static final String DISCO_JACKPOT_SOUND_KEY = "storytime:disco.jackpot";
    private static final int DISCO_JACKPOT_DURATION_TICKS = 340;
    private static final float DISCO_AUDIO_VOLUME = 0.30F;
    private static final float DISCO_PLAYER_AUDIO_VOLUME = 1.00F;
    private static final String DISCO_ENTITY_TAG = "deepcore_disco_entity";

    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final ChallengeSessionWorldBridge challengeSessionWorldBridge;

    private boolean resetInProgress;
    private final List<BukkitTask> discoTasks = new ArrayList<>();
    private final List<Entity> activeDiscoEntities = new ArrayList<>();
    private final Set<UUID> discoVisualWorldIds = new HashSet<>();
    private UUID activeLobbyWorldId;
    private UUID discoVisualsStartedWorldId;
    private UUID discoOverworldId;

    /**
     * Creates a manager coordinating world operations for one plugin instance.
     *
     * @param plugin                      plugin root instance used for world APIs
     *                                    and config
     * @param challengeSessionWorldBridge bridge to challenge session world hooks
     */
    public WorldResetManager(JavaPlugin plugin, ChallengeSessionWorldBridge challengeSessionWorldBridge) {
        this.plugin = plugin;
        this.log = ((DeepCorePlugin) plugin).getDeepCoreLogger();
        this.challengeSessionWorldBridge = challengeSessionWorldBridge;
    }

    /**
     * Ensures required run worlds and optional lobby worlds are loaded.
     */
    public void ensureThreeWorldsLoaded() {
        String overworldName = resolveConfiguredOverworldName();
        String netherName = overworldName + "_nether";
        String endName = overworldName + "_the_end";

        World overworld = Bukkit.getWorld(overworldName);
        if (overworld == null) {
            overworld = new WorldCreator(overworldName)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generateStructures(true)
                    .createWorld();
            if (overworld != null) {
                maybeDecorateAsDiscoWorld(overworld);
                log.debug("Loaded/created overworld: " + overworldName);
            }
        }

        World nether = Bukkit.getWorld(netherName);
        if (nether == null) {
            nether = new WorldCreator(netherName)
                    .environment(World.Environment.NETHER)
                    .createWorld();
            if (nether != null) {
                log.debug("Loaded/created nether: " + netherName);
            }
        }

        World end = Bukkit.getWorld(endName);
        if (end == null) {
            end = new WorldCreator(endName)
                    .environment(World.Environment.THE_END)
                    .createWorld();
            if (end != null) {
                log.debug("Loaded/created end: " + endName);
            }
        }

        if (shouldSpawnInLimboByDefault()) {
            List<World> lobbyWorlds = ensureLobbyWorldsLoaded();
            for (World lobbyWorld : lobbyWorlds) {
                log.debug("Loaded/created lobby world: " + lobbyWorld.getName());
            }
            selectRandomLobbyWorld();
        }
    }

    /**
     * Cleans up stale non-default worlds when the server starts.
     */
    public void cleanupNonDefaultWorldsOnStartup() {
        String activeOverworld = resolveConfiguredOverworldName();
        Set<String> defaultWorlds = new HashSet<>();
        defaultWorlds.add(activeOverworld);
        defaultWorlds.add(activeOverworld + "_nether");
        defaultWorlds.add(activeOverworld + "_the_end");
        defaultWorlds.addAll(getLobbyWorldNames());
        String trainingWorldName = resolveConfiguredTrainingWorldName();
        if (!trainingWorldName.isBlank()) {
            defaultWorlds.add(trainingWorldName);
        }

        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            String worldName = world.getName();
            if (defaultWorlds.contains(worldName)) {
                continue;
            }

            if (!tryUnloadIfLoaded(worldName)) {
                log.warn("Startup cleanup: could not unload non-default world '" + worldName + "'. Skipping deletion.");
                continue;
            }

            try {
                deleteWorldDirectory(worldName);
                log.debug("Startup cleanup: deleted loaded non-default world '" + worldName + "'.");
            } catch (IOException ex) {
                log.warn("Startup cleanup: failed deleting world '" + worldName + "': " + ex.getMessage());
            }
        }

        Path worldContainer = plugin.getServer().getWorldContainer().toPath();
        try (var entries = Files.list(worldContainer)) {
            entries.filter(Files::isDirectory).forEach(path -> {
                String worldName = path.getFileName().toString();
                if (defaultWorlds.contains(worldName) || !isLikelyWorldDirectory(path)) {
                    return;
                }

                try {
                    deleteWorldDirectory(worldName);
                    log.debug("Startup cleanup: deleted non-default world directory '" + worldName + "'.");
                } catch (IOException ex) {
                    log.warn(
                            "Startup cleanup: failed deleting world directory '" + worldName + "': " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Startup cleanup: failed listing world container: " + ex.getMessage());
        }
    }

    /**
     * Performs full overworld/nether/end reset while moving players to lobby.
     *
     * @param sender command sender requesting the world reset
     */
    public void resetThreeWorlds(CommandSender sender) {
        if (resetInProgress) {
            log.sendWarn(sender, "A world reset is already in progress.");
            return;
        }

        String overworldName = resolveConfiguredOverworldName();
        String netherName = overworldName + "_nether";
        String endName = overworldName + "_the_end";

        World selectedLobbyWorld = getActiveLobbyWorld();
        if (selectedLobbyWorld == null) {
            log.sendError(sender, "Could not create or load lobby worlds.");
            return;
        }

        resetInProgress = true;
        log.sendInfo(
                sender,
                "Starting three-world reset. Moving players to lobby world: " + selectedLobbyWorld.getName() + "...");

        Location limboSpawn = challengeSessionWorldBridge.getPreferredLobbyTeleportLocation();
        if (limboSpawn == null || limboSpawn.getWorld() == null) {
            limboSpawn = selectedLobbyWorld.getSpawnLocation().clone().add(0.5, 1.0, 0.5);
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            if (player.getWorld().equals(selectedLobbyWorld)) {
                continue;
            }
            player.teleport(limboSpawn);
            log.sendInfo(player, "World reset in progress. Please wait...");
        }

        removeAllBlockDisplayEntities();

        // Give teleports one tick to fully settle before unloading worlds.
        Bukkit.getScheduler().runTaskLater(plugin, () -> performReset(sender, overworldName, netherName, endName), 1L);
    }

    private void performReset(CommandSender sender, String overworldName, String netherName, String endName) {

        try {
            boolean fallbackUsed = false;
            boolean primaryWorldPinned = isPrimaryServerWorldName(overworldName);
            boolean overworldUnloaded = !primaryWorldPinned && tryUnloadIfLoaded(overworldName);
            String targetOverworldName = overworldName;
            if (overworldUnloaded) {
                unloadIfLoaded(endName);
                unloadIfLoaded(netherName);

                deleteWorldDirectory(endName);
                deleteWorldDirectory(netherName);
                deleteWorldDirectory(overworldName);
                updateLevelName(targetOverworldName);
            } else {
                targetOverworldName = buildFallbackWorldName(normalizeBaseWorldName(overworldName));
                fallbackUsed = true;
                if (primaryWorldPinned) {
                    log.sendInfo(
                            sender,
                            "Main server world is pinned while running. Switching to fresh world: "
                                    + targetOverworldName);
                    log.debug("Primary server world '" + overworldName
                            + "' is pinned while running. Rotating to fresh world '" + targetOverworldName + "'.");
                } else {
                    log.sendWarn(
                            sender, "Could not unload main world. Switching to fresh world: " + targetOverworldName);
                    log.warn("Could not unload world '" + overworldName + "'. Falling back to fresh world '"
                            + targetOverworldName + "'.");
                }
                updateLevelName(targetOverworldName);
            }

            String targetNetherName = targetOverworldName + "_nether";
            String targetEndName = targetOverworldName + "_the_end";

            unloadIfLoaded(targetEndName);
            unloadIfLoaded(targetNetherName);
            deleteWorldDirectory(targetEndName);
            deleteWorldDirectory(targetNetherName);

            World newOverworld = createOverworld(targetOverworldName);
            World newNether = createEnvironmentWorld(targetNetherName, World.Environment.NETHER);
            World newEnd = createEnvironmentWorld(targetEndName, World.Environment.THE_END);

            if (newOverworld == null) {
                throw new IllegalStateException("Failed to recreate overworld.");
            }

            ensureWorldStorageDirectories(newOverworld);
            if (newNether != null) {
                ensureWorldStorageDirectories(newNether);
            }
            if (newEnd != null) {
                ensureWorldStorageDirectories(newEnd);
            }

            challengeSessionWorldBridge.resetForNewRun();

            for (Player player : Bukkit.getOnlinePlayers()) {
                resetPlayerInventoryState(player);
                player.setGameMode(GameMode.SURVIVAL);
                challengeSessionWorldBridge.ensurePrepBook(player);
                log.sendInfo(player, "World reset complete. You remain in lobby. Ready up to begin countdown.");
            }

            challengeSessionWorldBridge.refreshLobbyPreview();

            log.sendInfo(sender, "Three-world reset complete.");

            if (fallbackUsed) {
                scheduleDeferredOldTrioCleanup(overworldName, netherName, endName);
            }
        } catch (Exception ex) {
            log.error("World reset failed: " + ex.getMessage());
            log.sendError(sender, "World reset failed: " + ex.getMessage());
        } finally {
            resetInProgress = false;
        }
    }

    private World getOrCreateLimboWorld(String limboWorldName) {
        return getOrCreateLobbyWorld(limboWorldName, World.Environment.NORMAL, true, true);
    }

    private World getOrCreateLobbyOverworldWorld(String worldName) {
        return getOrCreateLobbyWorld(worldName, World.Environment.NORMAL, false, false);
    }

    private World getOrCreateLobbyNetherWorld(String worldName) {
        return getOrCreateLobbyWorld(worldName, World.Environment.NETHER, false, false);
    }

    private World getOrCreateLobbyWorld(
            String worldName, World.Environment environment, boolean voidFlat, boolean forceSpawnAtOrigin) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            configureLobbyWorld(existing);
            return existing;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        if (voidFlat) {
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            try {
                creator.generatorSettings(
                        "{\"biome\":\"minecraft:the_void\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"structures\":{\"structures\":{}}}");
            } catch (Throwable ignored) {
                // Some API variants may not support custom generator settings here.
            }
        } else if (environment == World.Environment.NORMAL) {
            creator.type(WorldType.NORMAL);
            creator.generateStructures(true);
        }

        World limbo = creator.createWorld();
        if (limbo != null) {
            configureLobbyWorld(limbo);
            if (forceSpawnAtOrigin) {
                limbo.setSpawnLocation(0, 64, 0);
            }
        }
        return limbo;
    }

    private void configureLobbyWorld(World limbo) {
        applyLobbyWorldPolicies(limbo);
    }

    /**
     * Applies peaceful lobby policies to all configured lobby worlds.
     */
    public void enforceLobbyWorldPolicies() {
        for (String lobbyWorldName : getLobbyWorldNames()) {
            World lobbyWorld = Bukkit.getWorld(lobbyWorldName);
            if (lobbyWorld == null) {
                continue;
            }
            applyLobbyWorldPolicies(lobbyWorld);
        }
    }

    private void applyLobbyWorldPolicies(World world) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000L);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private List<String> getLobbyWorldNames() {
        String limboName = plugin.getConfig().getString(LIMBO_WORLD_NAME_PATH, "deepcore_limbo");
        String lobbyOverworldName =
                plugin.getConfig().getString(LOBBY_OVERWORLD_WORLD_NAME_PATH, "deepcore_lobby_overworld");
        String lobbyNetherName = plugin.getConfig().getString(LOBBY_NETHER_WORLD_NAME_PATH, "deepcore_lobby_nether");

        List<String> names = new ArrayList<>();
        names.add(limboName);
        names.add(lobbyOverworldName);
        names.add(lobbyNetherName);
        return names;
    }

    private List<World> ensureLobbyWorldsLoaded() {
        List<World> lobbyWorlds = new ArrayList<>();
        World limbo = getOrCreateLimboWorld(plugin.getConfig().getString(LIMBO_WORLD_NAME_PATH, "deepcore_limbo"));
        if (limbo != null) {
            lobbyWorlds.add(limbo);
        }

        World lobbyOverworld = getOrCreateLobbyOverworldWorld(
                plugin.getConfig().getString(LOBBY_OVERWORLD_WORLD_NAME_PATH, "deepcore_lobby_overworld"));
        if (lobbyOverworld != null) {
            lobbyWorlds.add(lobbyOverworld);
        }

        World lobbyNether = getOrCreateLobbyNetherWorld(
                plugin.getConfig().getString(LOBBY_NETHER_WORLD_NAME_PATH, "deepcore_lobby_nether"));
        if (lobbyNether != null) {
            lobbyWorlds.add(lobbyNether);
        }

        return lobbyWorlds;
    }

    private World getActiveLobbyWorld() {
        if (activeLobbyWorldId != null) {
            World active = Bukkit.getWorld(activeLobbyWorldId);
            if (active != null) {
                return active;
            }
        }

        return selectRandomLobbyWorld();
    }

    /**
     * Picks one configured lobby world to use as the active lobby destination.
     *
     * @return selected active lobby world, or null when no lobby world could load
     */
    public World selectRandomLobbyWorld() {
        List<World> lobbyWorlds = ensureLobbyWorldsLoaded();
        if (lobbyWorlds.isEmpty()) {
            return null;
        }

        World selected = lobbyWorlds.get(ThreadLocalRandom.current().nextInt(lobbyWorlds.size()));
        activeLobbyWorldId = selected.getUID();
        return selected;
    }

    /**
     * Sets the active lobby world by selector key.
     *
     * @param selector one of limbo, overworld, or nether
     * @return selected world, or null when selector is invalid or world could not
     *         be loaded
     */
    public World selectLobbyWorld(String selector) {
        if (selector == null) {
            return null;
        }

        String normalized = selector.trim().toLowerCase();
        World selected =
                switch (normalized) {
                    case "limbo" -> getOrCreateLimboWorld(
                            plugin.getConfig().getString(LIMBO_WORLD_NAME_PATH, "deepcore_limbo"));
                    case "overworld" -> getOrCreateLobbyOverworldWorld(
                            plugin.getConfig().getString(LOBBY_OVERWORLD_WORLD_NAME_PATH, "deepcore_lobby_overworld"));
                    case "nether" -> getOrCreateLobbyNetherWorld(
                            plugin.getConfig().getString(LOBBY_NETHER_WORLD_NAME_PATH, "deepcore_lobby_nether"));
                    default -> null;
                };

        if (selected != null) {
            activeLobbyWorldId = selected.getUID();
        }
        return selected;
    }

    /**
     * Teleports online players to the currently active lobby world spawn.
     *
     * @return count of players teleported
     */
    public int teleportOnlinePlayersToActiveLobby() {
        World activeLobby = getActiveLobbyWorld();
        if (activeLobby == null) {
            return 0;
        }

        Location lobbySpawn = resolveConfiguredLobbyArrivalLocation(activeLobby);
        int teleported = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobbySpawn);
            teleported++;
        }
        return teleported;
    }

    private Location resolveConfiguredLobbyArrivalLocation(World lobbyWorld) {
        Location fallback = lobbyWorld.getSpawnLocation().clone().add(0.5D, 1.0D, 0.5D);

        ConfigurationSection worldAnchors = plugin.getConfig().getConfigurationSection(PREVIEW_ANCHOR_WORLDS_PATH);
        if (worldAnchors != null) {
            ConfigurationSection worldAnchor = worldAnchors.getConfigurationSection(lobbyWorld.getName());
            if (worldAnchor == null) {
                worldAnchor = worldAnchors.getConfigurationSection(
                        lobbyWorld.getName().toLowerCase(Locale.ROOT));
            }

            if (worldAnchor != null) {
                boolean hasCoords = worldAnchor.contains("x") || worldAnchor.contains("y") || worldAnchor.contains("z");
                boolean enabled = worldAnchor.getBoolean("enabled", hasCoords);
                if (enabled) {
                    double x = worldAnchor.getDouble("x", fallback.getX());
                    double y = worldAnchor.getDouble("y", fallback.getY());
                    double z = worldAnchor.getDouble("z", fallback.getZ());
                    return new Location(lobbyWorld, x, y, z);
                }
            }
        }

        if (plugin.getConfig().getBoolean(PREVIEW_ANCHOR_ENABLED_PATH, false)) {
            double x = plugin.getConfig().getDouble(PREVIEW_ANCHOR_X_PATH, fallback.getX());
            double y = plugin.getConfig().getDouble(PREVIEW_ANCHOR_Y_PATH, fallback.getY());
            double z = plugin.getConfig().getDouble(PREVIEW_ANCHOR_Z_PATH, fallback.getZ());
            return new Location(lobbyWorld, x, y, z);
        }

        return fallback;
    }

    private World createOverworld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(true);
        World created = creator.createWorld();
        if (created != null) {
            maybeDecorateAsDiscoWorld(created);
        }
        return created;
    }

    private void maybeDecorateAsDiscoWorld(World world) {
        double chance = plugin.getConfig().getDouble(DISCO_WORLD_CHANCE_PATH, 0.002D);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        boolean selected = ThreadLocalRandom.current().nextDouble() < chance;
        if (!selected) {
            discoOverworldId = null;
            discoVisualsStartedWorldId = null;
            return;
        }

        discoOverworldId = world.getUID();
        Location spawn = world.getSpawnLocation();
        double centerX = spawn.getX();
        double centerZ = spawn.getZ();
        double baseY = Math.max(world.getMinHeight() + 2.0D, spawn.getY() + 1.0D);

        ensureDiscoVisualsStarted(world, centerX, baseY, centerZ, true);
        discoVisualsStartedWorldId = world.getUID();
        log.debug("Generated disco world variant at spawn in world '" + world.getName()
                + "' (preview disco mode + world disco effects active).");
    }

    /**
     * Starts disco preview visuals/effects around the provided center location.
     *
     * @param center center location used for disco preview effects
     */
    public void ensureDiscoPreviewEffects(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        double centerX = center.getX();
        double centerZ = center.getZ();
        double baseY = center.getY();
        ensureDiscoVisualsStarted(center.getWorld(), centerX, baseY, centerZ, false);
    }

    private void ensureDiscoVisualsStarted(
            World world, double centerX, double baseY, double centerZ, boolean playAudio) {
        if (world == null) {
            return;
        }
        if (discoVisualWorldIds.contains(world.getUID())) {
            return;
        }

        startDiscoVisuals(world, centerX, baseY, centerZ, playAudio);
        discoVisualWorldIds.add(world.getUID());
    }

    private void startDiscoVisuals(World world, double centerX, double baseY, double centerZ, boolean playAudio) {
        Location previewCenter = new Location(world, centerX, baseY, centerZ);
        Location core = previewCenter.clone().add(0.0D, 1.35D, 0.0D);
        ItemStack discoBallHead = createDiscoBallHeadItem();
        ItemDisplay coreDisplay = world.spawn(core, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.addScoreboardTag(DISCO_ENTITY_TAG);
            spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            spawned.setItemStack(discoBallHead);
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Quaternionf(),
                    new Vector3f(1.35F, 1.35F, 1.35F),
                    new Quaternionf()));
        });
        activeDiscoEntities.add(coreDisplay);

        Material[] beamBlocks = {
            Material.RED_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.PINK_STAINED_GLASS
        };

        List<BlockDisplay> beams = new ArrayList<>();
        for (Material beamBlock : beamBlocks) {
            BlockDisplay beam = world.spawn(core, BlockDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.addScoreboardTag(DISCO_ENTITY_TAG);
                spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                spawned.setBlock(beamBlock.createBlockData());
                spawned.setInterpolationDelay(0);
                spawned.setInterpolationDuration(2);
            });
            beams.add(beam);
            activeDiscoEntities.add(beam);
        }

        if (playAudio) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.stopSound(DISCO_JACKPOT_SOUND_KEY, SoundCategory.MASTER);
                player.playSound(
                        player.getLocation(),
                        DISCO_JACKPOT_SOUND_KEY,
                        SoundCategory.MASTER,
                        DISCO_PLAYER_AUDIO_VOLUME,
                        1.0F);
            }
            log.debug("Disco audio mode: jackpot playback (single track, duration ticks=" + DISCO_JACKPOT_DURATION_TICKS
                    + ").");
        }

        final double[] angle = {0.0D};
        final int[] songTick = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (Bukkit.getWorld(world.getUID()) == null || beams.isEmpty()) {
                                for (BlockDisplay beam : beams) {
                                    if (beam != null && beam.isValid()) {
                                        beam.remove();
                                    }
                                }
                                if (coreDisplay != null && coreDisplay.isValid()) {
                                    coreDisplay.remove();
                                }
                                activeDiscoEntities.remove(coreDisplay);
                                activeDiscoEntities.removeIf(display -> display == null || !display.isValid());
                                discoVisualWorldIds.remove(world.getUID());
                                if (taskRef[0] != null) {
                                    taskRef[0].cancel();
                                }
                                return;
                            }

                            angle[0] += (Math.PI / 120.0D);
                            if (coreDisplay != null && coreDisplay.isValid()) {
                                coreDisplay.setTransformation(new Transformation(
                                        new Vector3f(0.0F, 0.0F, 0.0F),
                                        new Quaternionf()
                                                .rotateY((float) (angle[0] * 1.25D))
                                                .rotateX((float) (Math.sin(angle[0]) * 0.15D)),
                                        new Vector3f(1.35F, 1.35F, 1.35F),
                                        new Quaternionf()));
                            }

                            for (int i = 0; i < beams.size(); i++) {
                                BlockDisplay beam = beams.get(i);
                                if (beam == null || !beam.isValid()) {
                                    continue;
                                }

                                double progress = (double) i / (double) beams.size();
                                double beamAngle = angle[0] + (progress * Math.PI * 2.0D);
                                float yaw = (float) beamAngle;
                                float pitch = (float)
                                        (-0.95D + (progress * 1.9D) + (Math.sin(angle[0] + (i * 0.8D)) * 0.18D));
                                float roll = (float) (Math.cos((angle[0] * 1.35D) + i) * 0.28D);
                                beam.setTransformation(new Transformation(
                                        new Vector3f(-0.06F, 0.0F, -0.06F),
                                        new Quaternionf()
                                                .rotateY(yaw)
                                                .rotateX(pitch)
                                                .rotateZ(roll),
                                        new Vector3f(0.12F, 8.5F, 0.12F),
                                        new Quaternionf()));
                            }

                            // Emit colorful burst particles only while jackpot audio is expected to be
                            // playing.
                            if (songTick[0] <= DISCO_JACKPOT_DURATION_TICKS) {
                                if (songTick[0] % 4 == 0) {
                                    spawnDiscoBurstParticles(world, previewCenter, 1);
                                }
                                songTick[0]++;
                            }
                        },
                        0L,
                        1L);
        discoTasks.add(taskRef[0]);
    }

    private void spawnDiscoBurstParticles(World world, Location origin, int burstCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int burst = 0; burst < burstCount; burst++) {
            Color color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.7F);

            int rayCount = 14;
            for (int i = 0; i < rayCount; i++) {
                double theta = random.nextDouble(0.0D, Math.PI * 2.0D);
                double phi = Math.acos((2.0D * random.nextDouble()) - 1.0D);
                double dirX = Math.sin(phi) * Math.cos(theta);
                double dirY = Math.cos(phi);
                double dirZ = Math.sin(phi) * Math.sin(theta);

                for (int step = 1; step <= 2; step++) {
                    double distance = step * 0.6D;
                    Location sample = origin.clone().add(dirX * distance, dirY * distance, dirZ * distance);
                    world.spawnParticle(Particle.DUST, sample, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
                }
            }

            world.spawnParticle(Particle.FIREWORK, origin, 8, 0.45D, 0.45D, 0.45D, 0.04D);
        }
    }

    private World createEnvironmentWorld(String worldName, World.Environment environment) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        return creator.createWorld();
    }

    private String resolveConfiguredOverworldName() {
        Path serverPropertiesPath =
                plugin.getServer().getWorldContainer().toPath().resolve("server.properties");
        if (Files.exists(serverPropertiesPath)) {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(serverPropertiesPath)) {
                properties.load(reader);
                String levelName = properties.getProperty("level-name");
                if (levelName != null && !levelName.isBlank()) {
                    return levelName.trim();
                }
            } catch (IOException ex) {
                log.warn("Could not read level-name from server.properties: " + ex.getMessage());
            }
        }

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (name.endsWith("_nether") || name.endsWith("_the_end")) {
                continue;
            }
            return name;
        }

        return "world";
    }

    private String resolveConfiguredTrainingWorldName() {
        String configuredName = plugin.getConfig().getString(TRAINING_WORLD_NAME_PATH, "deepcore_gym");
        if (configuredName == null) {
            return "";
        }
        return configuredName.trim();
    }

    private void ensureWorldStorageDirectories(World world) throws IOException {
        Path worldPath = world.getWorldFolder().toPath();
        Files.createDirectories(worldPath);
        Files.createDirectories(worldPath.resolve("data"));
        Files.createDirectories(worldPath.resolve("playerdata"));
        Files.createDirectories(worldPath.resolve("poi"));
        Files.createDirectories(worldPath.resolve("stats"));
        Files.createDirectories(worldPath.resolve("advancements"));
    }

    private void unloadIfLoaded(String worldName) {
        if (!tryUnloadIfLoaded(worldName)) {
            throw new IllegalStateException("Could not unload world: " + worldName + unloadFailureDetails(worldName));
        }
    }

    private boolean tryUnloadIfLoaded(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return true;
        }

        prepareWorldForUnload(world, worldName);
        boolean unloaded = Bukkit.unloadWorld(world, true);
        if (!unloaded) {
            prepareWorldForUnload(world, worldName);
            unloaded = Bukkit.unloadWorld(world, true);
        }

        return unloaded && Bukkit.getWorld(worldName) == null;
    }

    private void prepareWorldForUnload(World world, String worldName) {
        evacuatePlayersFromWorld(worldName);

        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity instanceof Player) {
                continue;
            }
            entity.remove();
        }

        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(true);
        }
    }

    private void evacuatePlayersFromWorld(String worldName) {
        World limboWorld = getActiveLobbyWorld();
        if (limboWorld == null) {
            return;
        }

        Location limboSpawn = limboWorld.getSpawnLocation().clone().add(0.5, 1.0, 0.5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }
            player.teleport(limboSpawn);
        }
    }

    private void deleteWorldDirectory(String worldName) throws IOException {
        Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(worldPath)) {
            return;
        }

        try (var pathStream = Files.walk(worldPath)) {
            pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private boolean isLikelyWorldDirectory(Path path) {
        return Files.exists(path.resolve("level.dat"))
                || Files.isDirectory(path.resolve("region"))
                || Files.isDirectory(path.resolve("DIM-1"))
                || Files.isDirectory(path.resolve("DIM1"));
    }

    private void scheduleDeferredOldTrioCleanup(String overworldName, String netherName, String endName) {
        Bukkit.getScheduler()
                .runTaskLater(plugin, () -> cleanupOldTrioDeferredOnce(overworldName, netherName, endName), 20L * 15L);
    }

    private void cleanupOldTrioDeferredOnce(String overworldName, String netherName, String endName) {
        boolean overworldDeleted = cleanupWorldIfPossible(overworldName);
        boolean netherDeleted = cleanupWorldIfPossible(netherName);
        boolean endDeleted = cleanupWorldIfPossible(endName);

        if (overworldDeleted && netherDeleted && endDeleted) {
            log.debug("Deferred cleanup removed old world trio: " + overworldName);
            return;
        }

        log.warn("Deferred cleanup could not delete old world trio now (likely still loaded): " + overworldName
                + ". Cleanup will be attempted again on next server startup.");
    }

    private boolean cleanupWorldIfPossible(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return true;
        }

        if (!tryUnloadIfLoaded(worldName)) {
            return false;
        }

        try {
            deleteWorldDirectory(worldName);
            return true;
        } catch (IOException ex) {
            log.warn("Deferred cleanup failed for world '" + worldName + "': " + ex.getMessage());
            return false;
        }
    }

    private String buildFallbackWorldName(String baseName) {
        return normalizeBaseWorldName(baseName) + "_reset_" + Instant.now().getEpochSecond();
    }

    private boolean isPrimaryServerWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        List<World> loadedWorlds = Bukkit.getWorlds();
        if (loadedWorlds.isEmpty()) {
            return false;
        }

        World primary = loadedWorlds.get(0);
        return primary != null && primary.getName().equalsIgnoreCase(worldName);
    }

    private String normalizeBaseWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "world";
        }

        String normalized = worldName;
        while (normalized.matches(".*_reset_\\d+$")) {
            normalized = normalized.replaceFirst("_reset_\\d+$", "");
        }
        return normalized;
    }

    private String unloadFailureDetails(String worldName) {
        StringBuilder details = new StringBuilder();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equalsIgnoreCase(worldName)) {
                if (!details.isEmpty()) {
                    details.append(", ");
                }
                details.append(player.getName());
            }
        }

        if (details.isEmpty()) {
            return "";
        }
        return " (players still present: " + details + ")";
    }

    private void updateLevelName(String levelName) {
        Path serverPropertiesPath =
                plugin.getServer().getWorldContainer().toPath().resolve("server.properties");
        if (!Files.exists(serverPropertiesPath)) {
            return;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(serverPropertiesPath)) {
            properties.load(reader);
        } catch (IOException ex) {
            log.warn("Could not read server.properties for level-name update: " + ex.getMessage());
            return;
        }

        properties.setProperty("level-name", levelName);
        try (var writer = Files.newBufferedWriter(serverPropertiesPath)) {
            properties.store(writer, "Minecraft server properties");
        } catch (IOException ex) {
            log.warn("Could not update level-name in server.properties: " + ex.getMessage());
        }
    }

    private void resetPlayerInventoryState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        inventory.setExtraContents(new org.bukkit.inventory.ItemStack[inventory.getExtraContents().length]);
        player.getEnderChest().clear();

        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);
        player.updateInventory();
    }

    private void removeAllBlockDisplayEntities() {
        for (BukkitTask task : discoTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        discoTasks.clear();
        discoVisualsStartedWorldId = null;
        discoVisualWorldIds.clear();

        for (Entity display : new ArrayList<>(activeDiscoEntities)) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeDiscoEntities.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Entity.class)) {
                if (!entity.getScoreboardTags().contains(DISCO_ENTITY_TAG)) {
                    continue;
                }
                if (entity instanceof BlockDisplay || entity instanceof ItemDisplay) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Stops all disco preview audio and removes active disco displays.
     */
    public void stopDiscoPreviewAudio() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound(DISCO_JACKPOT_SOUND_KEY, SoundCategory.RECORDS);
            player.stopSound(DISCO_JACKPOT_SOUND_KEY, SoundCategory.MASTER);
        }

        for (BukkitTask task : discoTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        discoTasks.clear();
        discoVisualWorldIds.clear();

        for (Entity display : new ArrayList<>(activeDiscoEntities)) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeDiscoEntities.clear();

        discoVisualsStartedWorldId = null;
    }

    private ItemStack createDiscoBallHeadItem() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return new ItemStack(Material.SEA_LANTERN);
        }

        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "deepcore_disco");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(DISCO_BALL_TEXTURE_URL));
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
            head.setItemMeta(skullMeta);
            return head;
        } catch (MalformedURLException | IllegalArgumentException ex) {
            log.warn("Failed to apply disco ball head texture; falling back to sea lantern: " + ex.getMessage());
            return new ItemStack(Material.SEA_LANTERN);
        }
    }

    /**
     * Returns whether the plugin should spawn players in lobby worlds by default.
     *
     * @return true when default spawn-in-lobby behavior is enabled
     */
    public boolean shouldSpawnInLimboByDefault() {
        return plugin.getConfig().getBoolean(LOBBY_SPAWN_IN_LIMBO_PATH, true);
    }

    public Location getConfiguredLimboSpawn() {
        World limboWorld = getActiveLobbyWorld();
        if (limboWorld == null) {
            return null;
        }
        return limboWorld.getSpawnLocation().clone().add(0.5D, 1.0D, 0.5D);
    }

    public boolean isLobbyWorld(World world) {
        if (world == null) {
            return false;
        }

        String worldName = world.getName();
        for (String lobbyWorldName : getLobbyWorldNames()) {
            if (worldName.equalsIgnoreCase(lobbyWorldName)) {
                return true;
            }
        }
        return false;
    }

    public World getCurrentOverworld() {
        String configuredName = resolveConfiguredOverworldName();
        World configuredWorld = Bukkit.getWorld(configuredName);
        if (configuredWorld != null) {
            return configuredWorld;
        }

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            if (isLobbyWorld(world)) {
                continue;
            }
            return world;
        }

        return null;
    }

    public boolean isCurrentOverworldDiscoVariant() {
        if (discoOverworldId == null) {
            return false;
        }

        // The active run world can be renamed/fallback-created during reset, so rely on
        // the explicit disco world marker instead of inferred world ordering.
        return Bukkit.getWorld(discoOverworldId) != null;
    }
}
