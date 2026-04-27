package dev.deepcore.challenge.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TrainingManagerTest {

    private ServerMock server;
    private DeepCoreLogger logger;
    private DeepCorePlugin plugin;
    private YamlConfiguration config;
    private File dataFolder;
    private World trainingWorld;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        trainingWorld = server.addSimpleWorld("deepcore_gym");
        server.addSimpleWorld("world");

        logger = mock(DeepCoreLogger.class);
        plugin = mock(DeepCorePlugin.class);
        config = new YamlConfiguration();
        dataFolder = Files.createTempDirectory("training-manager-test").toFile();

        when(plugin.getDeepCoreLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(Bukkit.getServer());
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        configureBaseTrainingConfig(config, true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tabComplete_andNonPlayerPath_areHandled() {
        TrainingManager manager = new TrainingManager(plugin);

        List<String> firstArg = manager.tabComplete(new String[] {"train", "s"});
        List<String> secondArg = manager.tabComplete(new String[] {"train", "stats", "c"});

        assertTrue(firstArg.contains("start"));
        assertTrue(firstArg.contains("stats"));
        assertTrue(secondArg.contains("craft"));

        CommandSender sender = mock(CommandSender.class);
        assertTrue(manager.handleCommand(sender, new String[] {"train"}));
        verify(logger).sendWarn(sender, "Only players can use training commands.");
    }

    @Test
    void disabledTraining_rejectsPlayerCommand() {
        configureBaseTrainingConfig(config, false);
        TrainingManager manager = new TrainingManager(plugin);

        PlayerMock player = server.addPlayer("trainer-disabled");
        assertTrue(manager.handleCommand(player, new String[] {"train"}));

        verify(logger).sendWarn(player, "Training gym is currently disabled.");
    }

    @Test
    void crossingChallengeEntranceBlock_doesNotTeleportWithoutActiveAttempt() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-entrance");
        player.teleport(new Location(trainingWorld, -18, 65, -33));

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(
                player, new Location(trainingWorld, -18, 65, -33), new Location(trainingWorld, -18, 65, -31));
        manager.onAttemptPlayerMove(moveEvent);

        Map<UUID, Object> activeByPlayer = getActiveByPlayer(manager);
        assertFalse(activeByPlayer.containsKey(player.getUniqueId()));
        assertEquals("deepcore_gym", player.getWorld().getName());
        assertEquals(-18.0D, player.getLocation().getX(), 0.01D);
        assertEquals(65.0D, player.getLocation().getY(), 0.01D);
        assertEquals(-33.0D, player.getLocation().getZ(), 0.01D);
    }

    @Test
    void playerRespawnInTrainingWorld_usesConfiguredTrainingRespawnSpawn() {
        config.set("training.respawn-spawn.world", "deepcore_gym");
        config.set("training.respawn-spawn.x", 12.5D);
        config.set("training.respawn-spawn.y", 70.0D);
        config.set("training.respawn-spawn.z", -8.5D);
        config.set("training.respawn-spawn.yaw", 90.0D);
        config.set("training.respawn-spawn.pitch", 0.0D);

        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-respawn");
        player.teleport(new Location(trainingWorld, 0, 65, 0));

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        when(deathEvent.getPlayer()).thenReturn(player);
        manager.onPlayerDeath(deathEvent);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(player);
        manager.onPlayerRespawn(respawnEvent);

        verify(respawnEvent)
                .setRespawnLocation(argThat(location -> location != null
                        && location.getWorld() != null
                        && "deepcore_gym".equals(location.getWorld().getName())
                        && Math.abs(location.getX() - 12.5D) < 0.001D
                        && Math.abs(location.getY() - 70.0D) < 0.001D
                        && Math.abs(location.getZ() - -8.5D) < 0.001D));
    }

    @Test
    void shutdown_withScheduledTask_cancelsAndSaves() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);

        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        setField(manager, "hudTask", task);
        manager.shutdown();

        verify(task).cancel();
        assertTrue(dataFolder.exists());
    }

    @Test
    void handleCommand_flowCoversLobbyStatsStartResetLeave() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-flow");
        player.teleport(new Location(server.getWorld("world"), 1, 65, 1));

        assertTrue(manager.handleCommand(player, new String[] {"train"}));
        assertTrue(player.getWorld().getName().equalsIgnoreCase("deepcore_gym"));

        assertTrue(manager.handleCommand(player, new String[] {"train", "stats"}));
        assertTrue(manager.handleCommand(player, new String[] {"train", "start", "portal"}));
        assertTrue(manager.handleCommand(player, new String[] {"train", "reset"}));
        assertTrue(manager.handleCommand(player, new String[] {"train", "leave"}));
        assertTrue(manager.handleCommand(player, new String[] {"train", "unknown"}));

        verify(logger, atLeastOnce()).sendInfo(any(Player.class), contains("Welcome to Training Gym"));
        verify(logger, atLeastOnce()).sendWarn(any(Player.class), contains("Usage: /challenge train"));

        Object definition = getDefinition(manager, TrainingChallengeType.PORTAL);
        assertNotNull(definition);
    }

    @Test
    void startAttempt_cancelAttempt_completeAttempt_andEventHandlers_coverCoreFlows() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-events");
        player.teleport(new Location(trainingWorld, 0, 65, 0));

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.PORTAL);

        Map<?, ?> activeByPlayer = getActiveByPlayer(manager);
        assertFalse(activeByPlayer.isEmpty());

        Object portalAttempt = activeByPlayer.get(player.getUniqueId());
        assertNotNull(portalAttempt);

        PlayerInteractEvent portalStartEvent = mock(PlayerInteractEvent.class);
        when(portalStartEvent.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(portalStartEvent.getClickedBlock()).thenReturn(trainingWorld.getBlockAt(-18, 65, -31));
        when(portalStartEvent.getPlayer()).thenReturn(player);
        manager.onStartButtonPress(portalStartEvent);
        verify(portalStartEvent).setCancelled(true);

        PortalCreateEvent portalCreateEvent = mock(PortalCreateEvent.class);
        doReturn(List.of(trainingWorld.getBlockAt(-18, 65, -18).getState()))
                .when(portalCreateEvent)
                .getBlocks();
        manager.onPortalCreate(portalCreateEvent);

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.BRIDGE);

        BlockPlaceEvent placeEvent = mock(BlockPlaceEvent.class);
        when(placeEvent.getPlayer()).thenReturn(player);
        when(placeEvent.getBlockPlaced()).thenReturn(trainingWorld.getBlockAt(100, 65, 100));
        manager.onBridgePlace(placeEvent);
        verify(placeEvent).setCancelled(true);

        PlayerMoveEvent moveEvent = mock(PlayerMoveEvent.class);
        when(moveEvent.getPlayer()).thenReturn(player);
        when(moveEvent.getFrom()).thenReturn(new Location(trainingWorld, 18, 65, 18));
        when(moveEvent.getTo()).thenReturn(new Location(trainingWorld, 100, 65, 100));
        manager.onAttemptPlayerMove(moveEvent);

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.CRAFT);
        Object craftAttempt = getActiveByPlayer(manager).get(player.getUniqueId());

        Object craftObjective = invoke(craftAttempt, "craftObjective", new Class[0]);
        setField(craftObjective, "craftedAxe", true);
        setField(craftObjective, "craftedShovel", true);
        setField(craftObjective, "craftedBeds", 64);
        setField(craftObjective, "craftedEyes", 64);

        org.bukkit.event.inventory.CraftItemEvent craftEvent = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        CraftingInventory craftingInventory = mock(CraftingInventory.class);
        Recipe recipe = mock(Recipe.class);
        when(craftEvent.getWhoClicked()).thenReturn(player);
        when(craftEvent.getInventory()).thenReturn(craftingInventory);
        when(craftEvent.getRecipe()).thenReturn(recipe);
        when(recipe.getResult()).thenReturn(new ItemStack(Material.STICK, 1));
        manager.onCraftItem(craftEvent);

        PlayerTeleportEvent teleportEvent = mock(PlayerTeleportEvent.class);
        when(teleportEvent.getPlayer()).thenReturn(player);
        when(teleportEvent.getTo()).thenReturn(new Location(trainingWorld, 100, 65, 100));
        manager.onAttemptTeleport(teleportEvent);

        TrainingStatsStore statsStore = (TrainingStatsStore) getField(manager, "statsStore");
        TrainingStatsStore.PlayerChallengeStats stats =
                statsStore.getStats(player.getUniqueId(), TrainingChallengeType.CRAFT);
        assertTrue(stats.bestTimeMs() >= 0L || stats.bestTimeMs() == -1L);

        verify(logger, atLeastOnce()).sendInfo(any(Player.class), contains("challenge started"));
    }

    @Test
    void utilityMethods_coverFormattingAndMaterialChecks() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);

        String formatted = (String) invoke(manager, "formatDurationMmSsCc", new Class[] {long.class}, 125_678L);
        String optionalUnknown = (String) invoke(manager, "formatOptionalDuration", new Class[] {long.class}, -1L);
        boolean sameBlock = (boolean) invoke(
                manager,
                "sameBlock",
                new Class[] {Location.class, Location.class},
                new Location(trainingWorld, 1, 2, 3),
                new Location(trainingWorld, 1, 2, 3));
        boolean bed = (boolean) invoke(manager, "isBedMaterial", new Class[] {Material.class}, Material.RED_BED);
        boolean axe = (boolean) invoke(manager, "isMetalAxe", new Class[] {Material.class}, Material.IRON_AXE);
        boolean shovel =
                (boolean) invoke(manager, "isMetalShovel", new Class[] {Material.class}, Material.DIAMOND_SHOVEL);

        assertEquals("02:05.67", formatted);
        assertEquals("--:--.--", optionalUnknown);
        assertTrue(sameBlock);
        assertTrue(bed);
        assertTrue(axe);
        assertTrue(shovel);

        ItemStack[] original = new ItemStack[] {new ItemStack(Material.STONE, 2), null};
        ItemStack[] clone =
                (ItemStack[]) invoke(manager, "cloneContents", new Class[] {ItemStack[].class}, (Object) original);
        assertNotNull(clone[0]);
        assertTrue(clone[0] != original[0]);
    }

    @Test
    void chestObjectiveText_readsHopperInventoryState() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");

        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> cuboidCtor = cuboidClass.getDeclaredConstructors()[0];
        cuboidCtor.setAccessible(true);
        Object region = cuboidCtor.newInstance("deepcore_gym", 4, 65, 4, 6, 65, 6);

        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Location hopperLocation = new Location(trainingWorld, 5, 65, 5);
        Object chestDefinition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST, region, null, null, null, null, hopperLocation, 1, 1, 1, 1, 1, 1, null);
        definitions.put(TrainingChallengeType.CHEST, chestDefinition);
        trainingWorld.getBlockAt(hopperLocation).setType(Material.HOPPER);

        Class<?> chestObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChestObjective");
        java.lang.reflect.Constructor<?> chestObjectiveCtor =
                chestObjectiveClass.getDeclaredConstructors()[0];
        chestObjectiveCtor.setAccessible(true);
        Object chestObjective = chestObjectiveCtor.newInstance(Map.of(Material.DIRT, 1));

        Class<?> activeAttemptClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ActiveAttempt");
        Class<?> craftObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$CraftObjective");
        java.lang.reflect.Constructor<?> activeAttemptCtor = activeAttemptClass.getDeclaredConstructor(
                UUID.class,
                TrainingChallengeType.class,
                long.class,
                craftObjectiveClass,
                chestObjectiveClass,
                Location.class);
        activeAttemptCtor.setAccessible(true);
        Object attempt = activeAttemptCtor.newInstance(
                UUID.randomUUID(), TrainingChallengeType.CHEST, System.currentTimeMillis(), null, chestObjective, null);

        String objectiveText = (String) invoke(manager, "objectiveText", new Class[] {activeAttemptClass}, attempt);
        assertTrue(objectiveText.startsWith("Hopper:"));
    }

    @Test
    void tickHud_andBridgeCompletion_pathsAreCovered() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-hud");
        player.teleport(new Location(trainingWorld, 18, 65, 18));

        setField(manager, "enabled", false);
        invoke(manager, "tickHud", new Class[0]);

        setField(manager, "enabled", true);
        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.BRIDGE);
        invoke(manager, "tickHud", new Class[0]);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(trainingWorld.getBlockAt(20, 65, 20));
        when(event.getPlayer()).thenReturn(player);
        manager.onBridgeCompletionPlate(event);

        Map<?, ?> activeByPlayer = getActiveByPlayer(manager);
        assertTrue(activeByPlayer.isEmpty());
    }

    @Test
    void onChestClickAndDrag_scheduleChecks_whenAttemptIsChest() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-chest");
        player.teleport(new Location(trainingWorld, -18, 65, 18));
        trainingWorld.getBlockAt(-18, 65, 18).setType(Material.CHEST);

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.CHEST);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return task;
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            org.bukkit.event.inventory.InventoryClickEvent clickEvent =
                    mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(clickEvent.getWhoClicked()).thenReturn(player);
            manager.onChestClick(clickEvent);

            org.bukkit.event.inventory.InventoryDragEvent dragEvent =
                    mock(org.bukkit.event.inventory.InventoryDragEvent.class);
            when(dragEvent.getWhoClicked()).thenReturn(player);
            manager.onChestDrag(dragEvent);
        }

        verify(logger, atLeastOnce()).sendInfo(any(Player.class), contains("challenge started"));
    }

    @Test
    void clearDynamicChestsInRegion_alsoClearsHopperInventory() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Object region = createCuboid("deepcore_gym", -1, 65, -1, 1, 65, 1);
        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Object definition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST,
                region,
                null,
                null,
                null,
                null,
                new Location(trainingWorld, 0, 65, 0),
                1,
                1,
                1,
                1,
                1,
                1,
                null);

        trainingWorld.getBlockAt(0, 65, 0).setType(Material.HOPPER);
        Container hopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        hopper.getInventory().setItem(0, new ItemStack(Material.BLAZE_ROD, 4));
        hopper.update(true, false);

        trainingWorld.getBlockAt(1, 65, 0).setType(Material.CHEST);

        invoke(manager, "clearDynamicChestsInRegion", new Class[] {definition.getClass()}, definition);

        Container clearedHopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        assertTrue(clearedHopper.getInventory().isEmpty());
        assertEquals(Material.HOPPER, trainingWorld.getBlockAt(0, 65, 0).getType());
        assertEquals(Material.AIR, trainingWorld.getBlockAt(1, 65, 0).getType());
    }

    @Test
    void clearDynamicChestsInRegion_clearsRegionHopperEvenWhenConfigHopperMissing() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> cuboidCtor = cuboidClass.getDeclaredConstructors()[0];
        cuboidCtor.setAccessible(true);
        Object region = cuboidCtor.newInstance("deepcore_gym", -1, 65, -1, 1, 65, 1);

        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Object definition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST, region, null, null, null, null, null, 1, 1, 1, 1, 1, 1, null);

        trainingWorld.getBlockAt(0, 65, 0).setType(Material.HOPPER);
        Container hopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        hopper.getInventory().setItem(0, new ItemStack(Material.STRING, 9));
        hopper.update(true, false);

        invoke(manager, "clearDynamicChestsInRegion", new Class[] {definitionClass}, definition);

        Container clearedHopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        assertTrue(clearedHopper.getInventory().isEmpty());
    }

    @Test
    void startAttempt_chestSnapshotDoesNotCaptureStaleHopperContents() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> cuboidCtor = cuboidClass.getDeclaredConstructors()[0];
        cuboidCtor.setAccessible(true);
        Object region = cuboidCtor.newInstance("deepcore_gym", -1, 65, -1, 1, 65, 1);

        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Object definition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST,
                region,
                null,
                new Location(trainingWorld, 0, 65, -1),
                new Location(trainingWorld, 0, 65, -1),
                null,
                new Location(trainingWorld, 0, 65, 0),
                1,
                1,
                1,
                1,
                1,
                1,
                null);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");
        definitions.put(TrainingChallengeType.CHEST, definition);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> arenaSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "arenaSnapshots");
        arenaSnapshots.clear();

        trainingWorld.getBlockAt(0, 65, 0).setType(Material.HOPPER);
        Container hopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        hopper.getInventory().setItem(0, new ItemStack(Material.STRING, 9));
        hopper.update(true, false);

        PlayerMock player = server.addPlayer("trainer-snapshot");
        player.teleport(new Location(trainingWorld, 0, 65, 0));

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class, boolean.class},
                player,
                TrainingChallengeType.CHEST,
                false);

        invoke(manager, "cancelAttempt", new Class[] {Player.class, String.class}, player, "test");

        Container clearedHopper = (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        assertTrue(clearedHopper.getInventory().isEmpty());
    }

    @Test
    void restoreAttemptArenaState_chestClearsHopperAfterSnapshotRestore() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> cuboidCtor = cuboidClass.getDeclaredConstructors()[0];
        cuboidCtor.setAccessible(true);
        Object region = cuboidCtor.newInstance("deepcore_gym", -1, 65, -1, 1, 65, 1);

        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Object definition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST,
                region,
                null,
                new Location(trainingWorld, 0, 65, -1),
                new Location(trainingWorld, 0, 65, -1),
                null,
                new Location(trainingWorld, 0, 65, 0),
                1,
                1,
                1,
                1,
                1,
                1,
                null);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");
        definitions.put(TrainingChallengeType.CHEST, definition);

        trainingWorld.getBlockAt(0, 65, 0).setType(Material.HOPPER);

        Object blockKey = createBlockKey(0, 65, 0);
        Object blockSnapshot = createBlockSnapshot(Material.HOPPER, Material.HOPPER.createBlockData(), new ItemStack[] {
            new ItemStack(Material.STRING, 9), null, null, null, null
        });

        Map<Object, Object> blocks = new LinkedHashMap<>();
        blocks.put(blockKey, blockSnapshot);
        Object arenaSnapshot = createArenaSnapshot("deepcore_gym", blocks);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> arenaSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "arenaSnapshots");
        arenaSnapshots.put(TrainingChallengeType.CHEST, arenaSnapshot);

        invoke(
                manager,
                "restoreAttemptArenaState",
                new Class[] {TrainingChallengeType.class},
                TrainingChallengeType.CHEST);

        Container restoredHopper =
                (Container) trainingWorld.getBlockAt(0, 65, 0).getState();
        assertTrue(restoredHopper.getInventory().isEmpty());
    }

    @Test
    void shutdown_restoresHiddenStartButtonsForActiveTraining() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");
        Object definition = definitions.get(TrainingChallengeType.PORTAL);
        assertNotNull(definition);

        Location startButton = new Location(trainingWorld, -18, 65, -31);
        trainingWorld.getBlockAt(startButton).setType(Material.AIR);

        Class<?> snapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockSnapshot");
        java.lang.reflect.Constructor<?> snapshotCtor = snapshotClass.getDeclaredConstructor(
                Material.class, org.bukkit.block.data.BlockData.class, ItemStack[].class);
        snapshotCtor.setAccessible(true);
        Object blockSnapshot = snapshotCtor.newInstance(Material.STONE, Material.STONE.createBlockData(), null);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> hiddenSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "hiddenStartButtonSnapshots");
        hiddenSnapshots.put(TrainingChallengeType.PORTAL, blockSnapshot);

        Object activeAttempt =
                createActiveAttempt(UUID.randomUUID(), TrainingChallengeType.PORTAL, System.currentTimeMillis(), null);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> activeByPlayer = (Map<UUID, Object>) getField(manager, "activeByPlayer");
        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> activeByChallenge =
                (Map<TrainingChallengeType, Object>) getField(manager, "activeByChallenge");
        activeByPlayer.put(UUID.randomUUID(), activeAttempt);
        activeByChallenge.put(TrainingChallengeType.PORTAL, activeAttempt);

        manager.shutdown();

        assertEquals(Material.STONE, trainingWorld.getBlockAt(startButton).getType());
        assertTrue(hiddenSnapshots.isEmpty());
        assertTrue(activeByPlayer.isEmpty());
        assertTrue(activeByChallenge.isEmpty());
    }

    @Test
    void cancelAttempt_restoresStartButtonAfterArenaRestore() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Location startButton = new Location(trainingWorld, -18, 65, -31);
        trainingWorld.getBlockAt(startButton).setType(Material.AIR);

        Class<?> blockSnapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockSnapshot");
        java.lang.reflect.Constructor<?> blockSnapshotCtor = blockSnapshotClass.getDeclaredConstructors()[0];
        blockSnapshotCtor.setAccessible(true);

        Object startButtonSnapshot =
                blockSnapshotCtor.newInstance(Material.STONE_BUTTON, Material.STONE_BUTTON.createBlockData(), null);

        Object arenaButtonSnapshot = blockSnapshotCtor.newInstance(Material.AIR, Material.AIR.createBlockData(), null);

        Object buttonKey = createBlockKey(startButton.getBlockX(), startButton.getBlockY(), startButton.getBlockZ());
        java.util.Map<Object, Object> blocks = new java.util.LinkedHashMap<>();
        blocks.put(buttonKey, arenaButtonSnapshot);
        Object arenaSnapshot = createArenaSnapshot("deepcore_gym", blocks);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> hiddenSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "hiddenStartButtonSnapshots");
        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> arenaSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "arenaSnapshots");
        @SuppressWarnings("unchecked")
        Map<UUID, Object> activeByPlayer = (Map<UUID, Object>) getField(manager, "activeByPlayer");

        hiddenSnapshots.put(TrainingChallengeType.PORTAL, startButtonSnapshot);
        arenaSnapshots.put(TrainingChallengeType.PORTAL, arenaSnapshot);

        PlayerMock player = server.addPlayer("trainer-button-cancel");
        player.teleport(new Location(trainingWorld, -18, 65, -18));
        Object attempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.PORTAL, System.currentTimeMillis(), null);
        activeByPlayer.put(player.getUniqueId(), attempt);

        invoke(manager, "cancelAttempt", new Class[] {Player.class, String.class}, player, "test");

        assertEquals(
                Material.STONE_BUTTON, trainingWorld.getBlockAt(startButton).getType());
    }

    @Test
    void spawnChestsWithLoot_placesChestsAroundHopperInsteadOfSkippingTheRing() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> cuboidCtor = cuboidClass.getDeclaredConstructors()[0];
        cuboidCtor.setAccessible(true);
        Object region = cuboidCtor.newInstance("deepcore_gym", -1, 65, -1, 1, 65, 1);

        Class<?> definitionClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> definitionCtor = definitionClass.getDeclaredConstructors()[0];
        definitionCtor.setAccessible(true);
        Object definition = definitionCtor.newInstance(
                TrainingChallengeType.CHEST,
                region,
                null,
                null,
                null,
                null,
                new Location(trainingWorld, 0, 65, 0),
                1,
                1,
                1,
                1,
                1,
                1,
                null);

        Class<?> chestObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChestObjective");
        java.lang.reflect.Constructor<?> chestObjectiveCtor =
                chestObjectiveClass.getDeclaredConstructors()[0];
        chestObjectiveCtor.setAccessible(true);
        Object chestObjective = chestObjectiveCtor.newInstance(Map.of(Material.DIRT, 1));

        trainingWorld.getBlockAt(0, 65, 0).setType(Material.HOPPER);
        trainingWorld.getBlockAt(1, 65, 1).setType(Material.STONE);
        trainingWorld.getBlockAt(1, 65, -1).setType(Material.STONE);
        trainingWorld.getBlockAt(-1, 65, 1).setType(Material.STONE);
        trainingWorld.getBlockAt(-1, 65, -1).setType(Material.STONE);

        invoke(
                manager,
                "spawnChestsWithLoot",
                new Class[] {definitionClass, chestObjectiveClass},
                definition,
                chestObjective);

        int spawnedChests = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (trainingWorld.getBlockAt(x, 65, z).getType() == Material.CHEST) {
                    spawnedChests++;
                }
            }
        }

        assertEquals(Material.HOPPER, trainingWorld.getBlockAt(0, 65, 0).getType());
        assertTrue(spawnedChests >= 1);
    }

    @Test
    void nestedTypesAndResolvers_coverRemainingUtilityBranches() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        Object cuboid = createCuboid("deepcore_gym", 10, 70, 10, 2, 60, 2);
        Method contains = cuboid.getClass().getDeclaredMethod("contains", Location.class);
        contains.setAccessible(true);
        assertTrue((boolean) contains.invoke(cuboid, new Location(trainingWorld, 5, 65, 5)));
        assertFalse((boolean) contains.invoke(cuboid, new Location(trainingWorld, 100, 65, 100)));

        @SuppressWarnings("unchecked")
        Optional<TrainingChallengeType> startButtonMatch = (Optional<TrainingChallengeType>) invoke(
                manager,
                "resolveChallengeByStartButton",
                new Class[] {Location.class},
                new Location(trainingWorld, -18, 65, -31));
        assertTrue(startButtonMatch.isPresent());

        @SuppressWarnings("unchecked")
        Optional<TrainingChallengeType> locationMatch = (Optional<TrainingChallengeType>) invoke(
                manager,
                "resolveChallengeByLocation",
                new Class[] {Location.class},
                new Location(trainingWorld, -18, 65, -18));
        assertTrue(locationMatch.isPresent());

        Class<?> craftObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$CraftObjective");
        java.lang.reflect.Constructor<?> objectiveCtor = craftObjectiveClass.getDeclaredConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, int.class, int.class);
        objectiveCtor.setAccessible(true);
        Object craftObjective = objectiveCtor.newInstance(true, true, true, true, 1, 1);
        setField(craftObjective, "craftedBeds", 1);
        setField(craftObjective, "craftedEyes", 1);
        setField(craftObjective, "craftedAxe", true);
        setField(craftObjective, "craftedShovel", true);
        Method isComplete = craftObjectiveClass.getDeclaredMethod("isComplete");
        isComplete.setAccessible(true);
        assertTrue((boolean) isComplete.invoke(craftObjective));

        Object bridgeAttempt = createActiveAttempt(
                UUID.randomUUID(), TrainingChallengeType.BRIDGE, System.currentTimeMillis() - 50L, null);
        String text = (String) invoke(manager, "objectiveText", new Class[] {bridgeAttempt.getClass()}, bridgeAttempt);
        assertTrue(text.contains("pressure plate"));
    }

    @Test
    void additionalBranches_coverStartAttemptCraftAndRestoreCases() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-branches");
        World nonTrainingWorld = server.getWorld("world");
        player.teleport(new Location(nonTrainingWorld, 0, 65, 0));

        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.PORTAL);
        verify(logger, atLeastOnce()).sendWarn(player, "Enter the training gym first with /challenge train.");

        player.teleport(new Location(trainingWorld, 0, 65, 0));
        @SuppressWarnings("unchecked")
        Map<UUID, Object> activeByPlayer = (Map<UUID, Object>) getField(manager, "activeByPlayer");
        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> activeByChallenge =
                (Map<TrainingChallengeType, Object>) getField(manager, "activeByChallenge");
        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");

        Object existingAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.PORTAL, System.currentTimeMillis(), null);
        activeByPlayer.put(player.getUniqueId(), existingAttempt);
        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.PORTAL);
        activeByPlayer.clear();

        activeByChallenge.put(
                TrainingChallengeType.PORTAL,
                createActiveAttempt(UUID.randomUUID(), TrainingChallengeType.PORTAL, System.currentTimeMillis(), null));
        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.PORTAL);
        activeByChallenge.clear();

        Object savedPortal = definitions.remove(TrainingChallengeType.PORTAL);
        invoke(
                manager,
                "startAttempt",
                new Class[] {Player.class, TrainingChallengeType.class},
                player,
                TrainingChallengeType.PORTAL);
        definitions.put(TrainingChallengeType.PORTAL, savedPortal);

        Class<?> craftObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$CraftObjective");
        java.lang.reflect.Constructor<?> objectiveCtor = craftObjectiveClass.getDeclaredConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, int.class, int.class);
        objectiveCtor.setAccessible(true);
        Object craftObjective = objectiveCtor.newInstance(true, true, true, true, 2, 2);

        Class<?> chestObjClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChestObjective");
        Class<?> challengeDefClass =
                Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        invoke(
                manager,
                "prepareLoadout",
                new Class[] {
                    Player.class, TrainingChallengeType.class, craftObjectiveClass, chestObjClass, challengeDefClass
                },
                player,
                TrainingChallengeType.CRAFT,
                craftObjective,
                null,
                null);
        invoke(
                manager,
                "prepareLoadout",
                new Class[] {
                    Player.class, TrainingChallengeType.class, craftObjectiveClass, chestObjClass, challengeDefClass
                },
                player,
                TrainingChallengeType.BRIDGE,
                craftObjective,
                null,
                null);

        Object craftAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.CRAFT, System.currentTimeMillis(), craftObjective);
        activeByPlayer.put(player.getUniqueId(), craftAttempt);

        org.bukkit.event.inventory.CraftItemEvent bedEvent = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        Recipe bedRecipe = mock(Recipe.class);
        when(bedEvent.getWhoClicked()).thenReturn(player);
        when(bedEvent.getRecipe()).thenReturn(bedRecipe);
        when(bedRecipe.getResult()).thenReturn(new ItemStack(Material.RED_BED, 1));
        manager.onCraftItem(bedEvent);

        org.bukkit.event.inventory.CraftItemEvent eyeEvent = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        Recipe eyeRecipe = mock(Recipe.class);
        when(eyeEvent.getWhoClicked()).thenReturn(player);
        when(eyeEvent.getRecipe()).thenReturn(eyeRecipe);
        when(eyeRecipe.getResult()).thenReturn(new ItemStack(Material.ENDER_EYE, 1));
        manager.onCraftItem(eyeEvent);

        org.bukkit.event.inventory.CraftItemEvent axeEvent = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        Recipe axeRecipe = mock(Recipe.class);
        when(axeEvent.getWhoClicked()).thenReturn(player);
        when(axeEvent.getRecipe()).thenReturn(axeRecipe);
        when(axeRecipe.getResult()).thenReturn(new ItemStack(Material.IRON_AXE, 1));
        manager.onCraftItem(axeEvent);

        org.bukkit.event.inventory.CraftItemEvent shovelEvent = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        Recipe shovelRecipe = mock(Recipe.class);
        when(shovelEvent.getWhoClicked()).thenReturn(player);
        when(shovelEvent.getRecipe()).thenReturn(shovelRecipe);
        when(shovelRecipe.getResult()).thenReturn(new ItemStack(Material.IRON_SHOVEL, 1));
        manager.onCraftItem(shovelEvent);

        Object cuboid = createCuboid("deepcore_gym", 5, 65, 5, 5, 65, 5);
        trainingWorld.getBlockAt(5, 65, 5).setType(Material.CHEST);
        org.bukkit.block.Container container =
                (org.bukkit.block.Container) trainingWorld.getBlockAt(5, 65, 5).getState();
        container.getInventory().setContents(new ItemStack[] {new ItemStack(Material.STONE, 1)});

        Object blockKey = createBlockKey(5, 65, 5);
        Object blockSnapshot = createBlockSnapshot(
                Material.CHEST,
                trainingWorld.getBlockAt(5, 65, 5).getBlockData(),
                new ItemStack[] {new ItemStack(Material.STONE, 1)});
        java.util.Map<Object, Object> blockMap = new java.util.LinkedHashMap<>();
        blockMap.put(blockKey, blockSnapshot);
        Object arenaSnapshot = createArenaSnapshot("deepcore_gym", blockMap);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> arenaSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "arenaSnapshots");
        arenaSnapshots.put(TrainingChallengeType.CHEST, arenaSnapshot);
        invoke(manager, "restoreArena", new Class[] {TrainingChallengeType.class}, TrainingChallengeType.CHEST);

        Object objectiveNullAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.CRAFT, System.currentTimeMillis(), null);
        String objectiveText = (String)
                invoke(manager, "objectiveText", new Class[] {objectiveNullAttempt.getClass()}, objectiveNullAttempt);
        assertTrue(objectiveText.contains("Craft objective"));
        assertNotNull(cuboid);
    }

    @Test
    void sidebarStatsAndTeleportEdgeCases_coverRemainingBranches() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock commandPlayer = server.addPlayer("trainer-sidebar");
        commandPlayer.teleport(new Location(trainingWorld, -18, 65, -18));

        UUID playerId = UUID.randomUUID();
        Player sidebarPlayer = mock(Player.class);
        when(sidebarPlayer.getUniqueId()).thenReturn(playerId);
        when(sidebarPlayer.getLocation()).thenReturn(new Location(trainingWorld, -18, 65, -18));

        TrainingStatsStore store = (TrainingStatsStore) getField(manager, "statsStore");
        store.recordCompletedAttempt(playerId, TrainingChallengeType.PORTAL, 3_000L);
        store.recordCompletedAttempt(playerId, TrainingChallengeType.PORTAL, 4_000L);
        store.recordCompletedAttempt(commandPlayer.getUniqueId(), TrainingChallengeType.PORTAL, 3_200L);
        store.recordCompletedAttempt(commandPlayer.getUniqueId(), TrainingChallengeType.PORTAL, 4_200L);

        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);
        when(scoreboardManager.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective(any(String.class), any(String.class), any(Component.class)))
                .thenReturn(objective);
        when(scoreboard.registerNewObjective(any(String.class), any(String.class), any(String.class)))
                .thenReturn(objective);
        when(scoreboard.getObjective(any(String.class))).thenReturn(objective);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mock(Scoreboard.class));
        when(objective.getScore(any(String.class))).thenReturn(score);
        when(sidebarPlayer.getScoreboard()).thenReturn(scoreboard);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(scoreboardManager);
            invoke(manager, "showIdleSidebar", new Class[] {Player.class}, sidebarPlayer);
            invoke(manager, "clearTrainingSidebar", new Class[] {Player.class}, sidebarPlayer);
        }

        assertTrue(manager.handleCommand(commandPlayer, new String[] {"train", "stats", "portal"}));
        invoke(
                manager,
                "sendStatsLineup",
                new Class[] {Player.class, TrainingChallengeType.class},
                commandPlayer,
                TrainingChallengeType.PORTAL);

        setField(manager, "trainingWorldName", "missing_world");
        assertTrue(manager.handleCommand(commandPlayer, new String[] {"train"}));

        @SuppressWarnings("unchecked")
        Map<UUID, Location> returnLocations = (Map<UUID, Location>) getField(manager, "returnLocations");
        returnLocations.put(commandPlayer.getUniqueId(), new Location((World) null, 0, 65, 0));
        commandPlayer.teleport(new Location(trainingWorld, 0, 65, 0));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            assertTrue(manager.handleCommand(commandPlayer, new String[] {"train", "leave"}));
        }

        verify(logger, atLeastOnce()).sendWarn(any(Player.class), contains("No return location available"));
        verify(logger, atLeastOnce()).sendError(any(Player.class), contains("is not loaded"));
    }

    @Test
    void schedulerAndNumberFormatHelpers_coverPrivateBranches() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask firstTask = mock(BukkitTask.class);
        BukkitTask secondTask = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(2L)))
                .thenReturn(firstTask)
                .thenReturn(secondTask);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            invoke(manager, "startHudTask", new Class[0]);
            invoke(manager, "startHudTask", new Class[0]);
        }

        verify(firstTask, atLeastOnce()).cancel();

        invoke(manager, "applyBlankSidebarNumberFormat", new Class[] {Objective.class}, new Object[] {null});

        Objective objective = mock(Objective.class);
        invoke(manager, "applyBlankSidebarNumberFormat", new Class[] {Objective.class}, objective);
    }

    @Test
    void movementTeleportBridgeAndRestore_coverLateBranches() throws Exception {
        TrainingManager manager = new TrainingManager(plugin);
        manager.reloadFromConfig();

        PlayerMock player = server.addPlayer("trainer-events");
        player.teleport(new Location(trainingWorld, 11, 65, 11));

        Object chestAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.CHEST, System.currentTimeMillis(), null);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> activeByPlayer = (Map<UUID, Object>) getField(manager, "activeByPlayer");
        activeByPlayer.put(player.getUniqueId(), chestAttempt);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        InventoryDragEvent dragEvent = mock(InventoryDragEvent.class);
        when(dragEvent.getWhoClicked()).thenReturn(player);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return mock(BukkitTask.class);
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.onChestClick(clickEvent);
            manager.onChestDrag(dragEvent);
        }

        Object portalAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.PORTAL, System.currentTimeMillis(), null);
        activeByPlayer.put(player.getUniqueId(), portalAttempt);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");
        Object portalDefinition = definitions.remove(TrainingChallengeType.PORTAL);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(
                player, new Location(trainingWorld, 11, 65, 11), new Location(trainingWorld, 12, 65, 12));
        manager.onAttemptPlayerMove(moveEvent);

        PlayerTeleportEvent teleportEvent = new PlayerTeleportEvent(
                player, new Location(trainingWorld, 11, 65, 11), new Location(trainingWorld, 12, 65, 12));
        manager.onAttemptTeleport(teleportEvent);

        definitions.put(TrainingChallengeType.PORTAL, portalDefinition);
        manager.onAttemptPlayerMove(new PlayerMoveEvent(
                player, new Location(trainingWorld, 11, 65, 11), new Location(trainingWorld, -200, 65, -200)));
        manager.onAttemptTeleport(new PlayerTeleportEvent(
                player, new Location(trainingWorld, 11, 65, 11), new Location(trainingWorld, -200, 65, -200)));

        Object bridgeAttempt = createActiveAttempt(
                player.getUniqueId(), TrainingChallengeType.BRIDGE, System.currentTimeMillis(), null);
        activeByPlayer.put(player.getUniqueId(), bridgeAttempt);
        BlockPlaceEvent placeEvent = mock(BlockPlaceEvent.class);
        when(placeEvent.getPlayer()).thenReturn(player);
        Block placedBlock = mock(Block.class);
        when(placedBlock.getLocation()).thenReturn(new Location(trainingWorld, -150, 64, -150));
        when(placeEvent.getBlockPlaced()).thenReturn(placedBlock);
        manager.onBridgePlace(placeEvent);
        verify(placeEvent, atLeastOnce()).setCancelled(true);

        Class<?> blockKeyClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockKey");
        java.lang.reflect.Constructor<?> blockKeyConstructor = blockKeyClass.getDeclaredConstructors()[0];
        blockKeyConstructor.setAccessible(true);
        Object key = blockKeyConstructor.newInstance(5, 65, 5);

        Class<?> blockSnapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockSnapshot");
        java.lang.reflect.Constructor<?> blockSnapshotConstructor =
                blockSnapshotClass.getDeclaredConstructors()[0];
        blockSnapshotConstructor.setAccessible(true);
        Object blockSnapshot = blockSnapshotConstructor.newInstance(
                Material.CHEST, Material.CHEST.createBlockData(), new ItemStack[] {new ItemStack(Material.STONE, 1)});

        Map<Object, Object> blockMap = Map.of(key, blockSnapshot);
        Class<?> arenaSnapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ArenaSnapshot");
        java.lang.reflect.Constructor<?> arenaSnapshotConstructor =
                arenaSnapshotClass.getDeclaredConstructors()[0];
        arenaSnapshotConstructor.setAccessible(true);
        Object arenaSnapshot = arenaSnapshotConstructor.newInstance(trainingWorld.getName(), blockMap);

        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> arenaSnapshots =
                (Map<TrainingChallengeType, Object>) getField(manager, "arenaSnapshots");
        arenaSnapshots.put(TrainingChallengeType.CHEST, arenaSnapshot);

        World worldMock = mock(World.class);
        Block worldBlock = mock(Block.class);
        Container container = mock(Container.class);
        Inventory inventory = mock(Inventory.class);
        when(worldMock.getBlockAt(5, 65, 5)).thenReturn(worldBlock);
        when(worldBlock.getState()).thenReturn(container);
        when(container.getInventory()).thenReturn(inventory);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getWorld(trainingWorld.getName())).thenReturn(worldMock);
            invoke(manager, "restoreArena", new Class[] {TrainingChallengeType.class}, TrainingChallengeType.CHEST);
        }

        verify(inventory, atLeastOnce()).clear();
        verify(inventory, atLeastOnce()).setContents(any(ItemStack[].class));
        verify(container, atLeastOnce()).update(true, false);
    }

    private void configureBaseTrainingConfig(YamlConfiguration yaml, boolean enabled) {
        yaml.set("training.enabled", enabled);
        yaml.set("training.world", "deepcore_gym");
        yaml.set("training.lobby-spawn.world", "deepcore_gym");
        yaml.set("training.lobby-spawn.x", 0.5D);
        yaml.set("training.lobby-spawn.y", 65.0D);
        yaml.set("training.lobby-spawn.z", 0.5D);
        yaml.set("training.lobby-spawn.yaw", 0.0D);
        yaml.set("training.lobby-spawn.pitch", 0.0D);
        yaml.set("training.respawn-spawn.world", "deepcore_gym");
        yaml.set("training.respawn-spawn.x", 0.5D);
        yaml.set("training.respawn-spawn.y", 65.0D);
        yaml.set("training.respawn-spawn.z", 0.5D);
        yaml.set("training.respawn-spawn.yaw", 0.0D);
        yaml.set("training.respawn-spawn.pitch", 0.0D);

        yaml.set("training.craft.beds.min", 5);
        yaml.set("training.craft.beds.max", 8);
        yaml.set("training.craft.eyes-of-ender.min", 7);
        yaml.set("training.craft.eyes-of-ender.max", 9);

        setChallengeConfig(yaml, "portal", -20, 64, -20, -16, 66, -16, -18, 65, -31, -18.5D, 65D, -18.5D);
        setChallengeConfig(yaml, "craft", 16, 64, -20, 20, 66, -16, 18, 65, -31, 18.5D, 65D, -18.5D);
        setChallengeConfig(yaml, "chest", -20, 64, 16, -16, 66, 20, -18, 65, 5, -18.5D, 65D, 18.5D);
        setChallengeConfig(yaml, "bridge", 16, 64, 16, 20, 66, 20, 18, 65, 5, 18.5D, 65D, 18.5D);

        yaml.set("training.challenges.bridge.completion-pressure-plate.world", "deepcore_gym");
        yaml.set("training.challenges.bridge.completion-pressure-plate.x", 20D);
        yaml.set("training.challenges.bridge.completion-pressure-plate.y", 65D);
        yaml.set("training.challenges.bridge.completion-pressure-plate.z", 20D);

        yaml.set("training.challenges.chest.tracked-chests.c1.world", "deepcore_gym");
        yaml.set("training.challenges.chest.tracked-chests.c1.x", -18D);
        yaml.set("training.challenges.chest.tracked-chests.c1.y", 65D);
        yaml.set("training.challenges.chest.tracked-chests.c1.z", 18D);
    }

    private void setChallengeConfig(
            YamlConfiguration yaml,
            String key,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int startButtonX,
            int startButtonY,
            int startButtonZ,
            double startLocX,
            double startLocY,
            double startLocZ) {
        String base = "training.challenges." + key;
        yaml.set(base + ".enabled", true);
        yaml.set(base + ".region.world", "deepcore_gym");
        yaml.set(base + ".region.min.x", minX);
        yaml.set(base + ".region.min.y", minY);
        yaml.set(base + ".region.min.z", minZ);
        yaml.set(base + ".region.max.x", maxX);
        yaml.set(base + ".region.max.y", maxY);
        yaml.set(base + ".region.max.z", maxZ);

        yaml.set(base + ".start-button.world", "deepcore_gym");
        yaml.set(base + ".start-button.x", startButtonX);
        yaml.set(base + ".start-button.y", startButtonY);
        yaml.set(base + ".start-button.z", startButtonZ);

        yaml.set(base + ".start-location.world", "deepcore_gym");
        yaml.set(base + ".start-location.x", startLocX);
        yaml.set(base + ".start-location.y", startLocY);
        yaml.set(base + ".start-location.z", startLocZ);
        yaml.set(base + ".start-location.yaw", 0.0D);
        yaml.set(base + ".start-location.pitch", 0.0D);
    }

    private Object getDefinition(TrainingManager manager, TrainingChallengeType type) throws Exception {
        @SuppressWarnings("unchecked")
        Map<TrainingChallengeType, Object> definitions =
                (Map<TrainingChallengeType, Object>) getField(manager, "definitions");
        return definitions.get(type);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Object> getActiveByPlayer(TrainingManager manager) throws Exception {
        return (Map<UUID, Object>) getField(manager, "activeByPlayer");
    }

    private Object createCuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
            throws Exception {
        Class<?> cuboidClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$Cuboid");
        java.lang.reflect.Constructor<?> constructor = cuboidClass.getDeclaredConstructor(
                String.class, int.class, int.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Object createChallengeDefinition(
            TrainingChallengeType type,
            Object cuboid,
            Location startButton,
            Location startLocation,
            Location completionPlate,
            List<Location> tracked)
            throws Exception {
        Class<?> defClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChallengeDefinition");
        java.lang.reflect.Constructor<?> constructor = defClass.getDeclaredConstructor(
                TrainingChallengeType.class,
                cuboid.getClass(),
                cuboid.getClass(),
                Location.class,
                Location.class,
                Location.class,
                List.class,
                int.class,
                int.class,
                int.class,
                int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                type, cuboid, null, startButton, startLocation, completionPlate, tracked, 5, 8, 7, 9);
    }

    private Object createActiveAttempt(UUID playerId, TrainingChallengeType type, long startedAt, Object craftObjective)
            throws Exception {
        Class<?> activeAttemptClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ActiveAttempt");
        Class<?> craftObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$CraftObjective");
        Class<?> chestObjectiveClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ChestObjective");
        java.lang.reflect.Constructor<?> constructor = activeAttemptClass.getDeclaredConstructor(
                UUID.class,
                TrainingChallengeType.class,
                long.class,
                craftObjectiveClass,
                chestObjectiveClass,
                Location.class);
        constructor.setAccessible(true);
        return constructor.newInstance(playerId, type, startedAt, craftObjective, null, null);
    }

    private Object createBlockKey(int x, int y, int z) throws Exception {
        Class<?> blockKeyClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockKey");
        java.lang.reflect.Constructor<?> constructor =
                blockKeyClass.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(x, y, z);
    }

    private Object createBlockSnapshot(
            Material material, org.bukkit.block.data.BlockData blockData, ItemStack[] inventoryContents)
            throws Exception {
        Class<?> snapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$BlockSnapshot");
        java.lang.reflect.Constructor<?> constructor = snapshotClass.getDeclaredConstructor(
                Material.class, org.bukkit.block.data.BlockData.class, ItemStack[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(material, blockData, inventoryContents);
    }

    private Object createArenaSnapshot(String worldName, java.util.Map<Object, Object> blocks) throws Exception {
        Class<?> snapshotClass = Class.forName("dev.deepcore.challenge.training.TrainingManager$ArenaSnapshot");
        java.lang.reflect.Constructor<?> constructor =
                snapshotClass.getDeclaredConstructor(String.class, java.util.Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(worldName, blocks);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
