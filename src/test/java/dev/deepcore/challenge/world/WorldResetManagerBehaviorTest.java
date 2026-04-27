package dev.deepcore.challenge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class WorldResetManagerBehaviorTest {

    @Test
    void performReset_successPath_recreatesWorldsResetsPlayersAndBridge() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.disco-world-chance", 0.0D);
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        ChallengeSessionWorldBridge bridge = mock(ChallengeSessionWorldBridge.class);
        WorldResetManager manager = new WorldResetManager(plugin, bridge);

        World newOverworld = mock(World.class);
        when(newOverworld.getUID()).thenReturn(UUID.randomUUID());
        Path newOverworldPath = worldContainer.resolve("world");
        when(newOverworld.getWorldFolder()).thenReturn(newOverworldPath.toFile());

        World newNether = mock(World.class);
        when(newNether.getWorldFolder())
                .thenReturn(worldContainer.resolve("world_nether").toFile());
        World newEnd = mock(World.class);
        when(newEnd.getWorldFolder())
                .thenReturn(worldContainer.resolve("world_the_end").toFile());

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getExtraContents()).thenReturn(new org.bukkit.inventory.ItemStack[2]);
        Inventory enderChest = mock(Inventory.class);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChest()).thenReturn(enderChest);

        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);

                            String name = (String) context.arguments().get(0);
                            if ("world".equals(name)) {
                                when(creator.createWorld()).thenReturn(newOverworld);
                            } else if ("world_nether".equals(name)) {
                                when(creator.createWorld()).thenReturn(newNether);
                            } else if ("world_the_end".equals(name)) {
                                when(creator.createWorld()).thenReturn(newEnd);
                            }
                        })) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world_the_end")).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));

            invokeVoid(
                    manager,
                    "performReset",
                    new Class<?>[] {CommandSender.class, String.class, String.class, String.class},
                    sender,
                    "world",
                    "world_nether",
                    "world_the_end");

            org.junit.jupiter.api.Assertions.assertEquals(
                    3, creators.constructed().size());
        }

        verify(inventory).clear();
        verify(inventory).setArmorContents(org.mockito.ArgumentMatchers.any(org.bukkit.inventory.ItemStack[].class));
        verify(inventory).setExtraContents(org.mockito.ArgumentMatchers.any(org.bukkit.inventory.ItemStack[].class));
        verify(enderChest).clear();
        verify(player).setGameMode(org.bukkit.GameMode.SURVIVAL);
        verify(bridge).resetForNewRun();
        verify(bridge).ensurePrepBook(player);
        verify(bridge).refreshLobbyPreview();
        verify(log).sendInfo(sender, "Three-world reset complete.");
        assertTrue(Files.isDirectory(newOverworldPath.resolve("data")));
        assertTrue(Files.isDirectory(newOverworldPath.resolve("playerdata")));
    }

    @Test
    void createDiscoBallHeadItem_fallsBackToSeaLanternWhenProfileFails() throws Exception {
        DeepCorePlugin plugin = newPluginStub();
        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createProfile(
                            org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new IllegalArgumentException("profile failed"));

            Method method = WorldResetManager.class.getDeclaredMethod("createDiscoBallHeadItem");
            method.setAccessible(true);
            org.bukkit.inventory.ItemStack fallback = (org.bukkit.inventory.ItemStack) method.invoke(manager);

            assertSame(Material.SEA_LANTERN, fallback.getType());
        }
    }

    @Test
    void ensureThreeWorldsLoaded_createsMissingRunWorlds_whenNotLoaded() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("challenge.lobby_spawn_in_limbo_by_default", false);
        config.set("reset.disco-world-chance", 0.0D);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World createdOverworld = mock(World.class);
        when(createdOverworld.getName()).thenReturn("world");
        when(createdOverworld.getUID()).thenReturn(UUID.randomUUID());
        when(createdOverworld.getSpawnLocation()).thenReturn(new Location(createdOverworld, 0.0D, 64.0D, 0.0D));
        World createdNether = mock(World.class);
        World createdEnd = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);

                            String name = (String) context.arguments().get(0);
                            if ("world".equals(name)) {
                                when(creator.createWorld()).thenReturn(createdOverworld);
                            } else if ("world_nether".equals(name)) {
                                when(creator.createWorld()).thenReturn(createdNether);
                            } else if ("world_the_end".equals(name)) {
                                when(creator.createWorld()).thenReturn(createdEnd);
                            }
                        })) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world_the_end")).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            manager.ensureThreeWorldsLoaded();

            org.junit.jupiter.api.Assertions.assertEquals(
                    3, creators.constructed().size());
        }
    }

    @Test
    void ensureThreeWorldsLoaded_withLobbyEnabled_selectsActiveLobbyWorld() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("challenge.lobby_spawn_in_limbo_by_default", true);
        config.set("reset.limbo-world-name", "deepcore_limbo");
        config.set("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
        config.set("reset.lobby-nether-world-name", "deepcore_lobby_nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        World runOverworld = mock(World.class);
        World runNether = mock(World.class);
        World runEnd = mock(World.class);

        World limbo = mock(World.class);
        World lobbyOverworld = mock(World.class);
        World lobbyNether = mock(World.class);

        when(limbo.getUID()).thenReturn(UUID.randomUUID());
        when(lobbyOverworld.getUID()).thenReturn(UUID.randomUUID());
        when(lobbyNether.getUID()).thenReturn(UUID.randomUUID());
        when(limbo.getSpawnLocation()).thenReturn(new Location(limbo, 1.0D, 64.0D, 1.0D));
        when(lobbyOverworld.getSpawnLocation()).thenReturn(new Location(lobbyOverworld, 2.0D, 64.0D, 2.0D));
        when(lobbyNether.getSpawnLocation()).thenReturn(new Location(lobbyNether, 3.0D, 64.0D, 3.0D));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(runOverworld);
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(runNether);
            bukkit.when(() -> Bukkit.getWorld("world_the_end")).thenReturn(runEnd);

            bukkit.when(() -> Bukkit.getWorld("deepcore_limbo")).thenReturn(limbo);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_overworld")).thenReturn(lobbyOverworld);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_nether")).thenReturn(lobbyNether);

            manager.ensureThreeWorldsLoaded();

            Location configured = manager.getConfiguredLimboSpawn();
            assertNotNull(configured);
            assertTrue(configured.getWorld() == limbo
                    || configured.getWorld() == lobbyOverworld
                    || configured.getWorld() == lobbyNether);
        }
    }

    @Test
    void enforceLobbyWorldPolicies_appliesPeacefulLobbyRules() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "deepcore_limbo");
        config.set("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
        config.set("reset.lobby-nether-world-name", "deepcore_lobby_nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        World limbo = mock(World.class);
        World lobbyOverworld = mock(World.class);
        World lobbyNether = mock(World.class);

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("deepcore_limbo")).thenReturn(limbo);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_overworld")).thenReturn(lobbyOverworld);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_nether")).thenReturn(lobbyNether);

            manager.enforceLobbyWorldPolicies();
        }

        verify(limbo).setDifficulty(Difficulty.PEACEFUL);
        verify(lobbyOverworld).setDifficulty(Difficulty.PEACEFUL);
        verify(lobbyNether).setDifficulty(Difficulty.PEACEFUL);
        verify(limbo).setGameRule(GameRule.DO_MOB_SPAWNING, false);
        verify(lobbyOverworld).setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        verify(lobbyNether).setGameRule(GameRule.DO_WEATHER_CYCLE, false);
    }

    @Test
    void resetThreeWorlds_warnsWhenResetAlreadyInProgress() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        setField(manager, "resetInProgress", true);

        CommandSender sender = mock(CommandSender.class);
        manager.resetThreeWorlds(sender);

        verify(log).sendWarn(sender, "A world reset is already in progress.");
    }

    @Test
    void resetThreeWorlds_errorsWhenLobbyWorldCannotBeResolved() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);

        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);
                            when(creator.createWorld()).thenReturn(null);
                        })) {
            bukkit.when(() -> Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            manager.resetThreeWorlds(sender);

            verify(log).sendError(sender, "Could not create or load lobby worlds.");
            assertTrue(creators.constructed().size() >= 1);
        }
    }

    @Test
    void cleanupNonDefaultWorldsOnStartup_deletesLikelyNonDefaultWorldDirectories() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        Path defaultWorld = worldContainer.resolve("world");
        Path staleWorld = worldContainer.resolve("old_run_world");
        Files.createDirectories(defaultWorld);
        Files.createDirectories(staleWorld);
        Files.writeString(staleWorld.resolve("level.dat"), "dummy");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        World defaultWorldObj = mock(World.class);
        when(defaultWorldObj.getName()).thenReturn("world");

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(defaultWorldObj));
            bukkit.when(() -> Bukkit.getWorld("old_run_world")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorldObj);

            manager.cleanupNonDefaultWorldsOnStartup();
        }

        assertTrue(Files.exists(defaultWorld));
        assertFalse(Files.exists(staleWorld));
    }

    @Test
    void cleanupNonDefaultWorldsOnStartup_keepsConfiguredTrainingWorldDirectory() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        Path defaultWorld = worldContainer.resolve("world");
        Path trainingWorld = worldContainer.resolve("deepcore_gym");
        Path staleWorld = worldContainer.resolve("old_run_world");
        Files.createDirectories(defaultWorld);
        Files.createDirectories(trainingWorld);
        Files.writeString(trainingWorld.resolve("level.dat"), "dummy");
        Files.createDirectories(staleWorld);
        Files.writeString(staleWorld.resolve("level.dat"), "dummy");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("training.world", "deepcore_gym");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        World defaultWorldObj = mock(World.class);
        when(defaultWorldObj.getName()).thenReturn("world");

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(defaultWorldObj));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorldObj);
            bukkit.when(() -> Bukkit.getWorld("old_run_world")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("deepcore_gym")).thenReturn(null);

            manager.cleanupNonDefaultWorldsOnStartup();
        }

        assertTrue(Files.exists(defaultWorld));
        assertTrue(Files.exists(trainingWorld));
        assertFalse(Files.exists(staleWorld));
    }

    @Test
    void cleanupNonDefaultWorldsOnStartup_keepsNonWorldDirectories() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        Path docsDir = worldContainer.resolve("notes_folder");
        Files.createDirectories(docsDir);

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File(worldContainer.toString()));

        World defaultWorldObj = mock(World.class);
        when(defaultWorldObj.getName()).thenReturn("world");

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(defaultWorldObj));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorldObj);

            manager.cleanupNonDefaultWorldsOnStartup();
        }

        assertTrue(Files.exists(docsDir));
    }

    @Test
    void cleanupNonDefaultWorldsOnStartup_unloadsLoadedNonDefaultWorldThenDeletesDirectory() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        Files.createDirectories(worldContainer.resolve("world"));
        Path staleWorldDir = worldContainer.resolve("old_world");
        Files.createDirectories(staleWorldDir);
        Files.writeString(staleWorldDir.resolve("level.dat"), "dummy");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        World defaultWorldObj = mock(World.class);
        when(defaultWorldObj.getName()).thenReturn("world");

        World staleWorldObj = mock(World.class);
        when(staleWorldObj.getName()).thenReturn("old_world");

        Entity entity = mock(Entity.class);
        when(staleWorldObj.getEntities()).thenReturn(List.of(entity));
        Chunk chunk = mock(Chunk.class);
        when(staleWorldObj.getLoadedChunks()).thenReturn(new Chunk[] {chunk});

        World lobbyWorld = mock(World.class);
        UUID lobbyId = UUID.randomUUID();
        when(lobbyWorld.getSpawnLocation()).thenReturn(new Location(lobbyWorld, 0.0D, 64.0D, 0.0D));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        setField(manager, "activeLobbyWorldId", lobbyId);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(defaultWorldObj, staleWorldObj));
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobbyWorld);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorldObj);
            bukkit.when(() -> Bukkit.getWorld("old_world"))
                    .thenReturn(staleWorldObj)
                    .thenReturn((World) null);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            bukkit.when(() -> Bukkit.unloadWorld(staleWorldObj, true)).thenReturn(true);

            manager.cleanupNonDefaultWorldsOnStartup();
        }

        verify(entity).remove();
        verify(chunk).unload(true);
        assertFalse(Files.exists(staleWorldDir));
    }

    @Test
    void shouldSpawnInLimboByDefault_readsConfiguredValue() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        assertTrue(manager.shouldSpawnInLimboByDefault());

        config.set("challenge.lobby_spawn_in_limbo_by_default", false);
        assertFalse(manager.shouldSpawnInLimboByDefault());
    }

    @Test
    void isLobbyWorld_usesConfiguredLobbyWorldNames_caseInsensitively() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "DeepCore_Limbo");
        config.set("reset.lobby-overworld-world-name", "DeepCore_Lobby_Overworld");
        config.set("reset.lobby-nether-world-name", "DeepCore_Lobby_Nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World limbo = mock(World.class);
        when(limbo.getName()).thenReturn("deepcore_limbo");
        World random = mock(World.class);
        when(random.getName()).thenReturn("world_regular");

        assertTrue(manager.isLobbyWorld(limbo));
        assertFalse(manager.isLobbyWorld(random));
        assertFalse(manager.isLobbyWorld(null));
    }

    @Test
    void getConfiguredLimboSpawn_returnsCenteredSpawnFromActiveLobbyWorld() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World lobby = mock(World.class);
        UUID lobbyId = UUID.randomUUID();
        when(lobby.getUID()).thenReturn(lobbyId);
        when(lobby.getSpawnLocation()).thenReturn(new Location(lobby, 10.0D, 64.0D, 20.0D));

        setField(manager, "activeLobbyWorldId", lobbyId);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobby);
            Location spawn = manager.getConfiguredLimboSpawn();
            assertNotNull(spawn);
            org.junit.jupiter.api.Assertions.assertEquals(10.5D, spawn.getX());
            org.junit.jupiter.api.Assertions.assertEquals(65.0D, spawn.getY());
            org.junit.jupiter.api.Assertions.assertEquals(20.5D, spawn.getZ());
        }
    }

    @Test
    void teleportOnlinePlayersToActiveLobby_usesConfiguredWorldAnchorCoordinates() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("challenge.preview_hologram_anchor.worlds.deepcore_lobby_overworld.enabled", true);
        config.set("challenge.preview_hologram_anchor.worlds.deepcore_lobby_overworld.x", 21.5D);
        config.set("challenge.preview_hologram_anchor.worlds.deepcore_lobby_overworld.y", 94.0D);
        config.set("challenge.preview_hologram_anchor.worlds.deepcore_lobby_overworld.z", -41.5D);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World lobby = mock(World.class);
        UUID lobbyId = UUID.randomUUID();
        when(lobby.getUID()).thenReturn(lobbyId);
        when(lobby.getName()).thenReturn("deepcore_lobby_overworld");
        when(lobby.getSpawnLocation()).thenReturn(new Location(lobby, 0.0D, 64.0D, 0.0D));
        setField(manager, "activeLobbyWorldId", lobbyId);

        Player player = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobby);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));

            int moved = manager.teleportOnlinePlayersToActiveLobby();
            org.junit.jupiter.api.Assertions.assertEquals(1, moved);
        }

        verify(player).teleport((Location) org.mockito.ArgumentMatchers.argThat((Location location) -> location != null
                && Math.abs(location.getX() - 21.5D) < 0.001D
                && Math.abs(location.getY() - 94.0D) < 0.001D
                && Math.abs(location.getZ() - -41.5D) < 0.001D));
    }

    @Test
    void getCurrentOverworld_prefersConfiguredThenFallsBackToFirstNormalNonLobbyWorld() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new java.io.File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World configured = mock(World.class);
        World lobby = mock(World.class);
        when(lobby.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(lobby.getName()).thenReturn("deepcore_limbo");

        World candidate = mock(World.class);
        when(candidate.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(candidate.getName()).thenReturn("run_world");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(configured);
            assertSame(configured, manager.getCurrentOverworld());

            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(lobby, candidate));
            assertSame(candidate, manager.getCurrentOverworld());
        }
    }

    @Test
    void getCurrentOverworld_returnsNullWhenNoEligibleNormalWorldExists() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "deepcore_limbo");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new java.io.File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World lobby = mock(World.class);
        when(lobby.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(lobby.getName()).thenReturn("deepcore_limbo");

        World nether = mock(World.class);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(lobby, nether));

            assertNull(manager.getCurrentOverworld());
        }
    }

    @Test
    void getConfiguredLimboSpawn_returnsNullWhenNoLobbyWorldCanLoad() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "deepcore_limbo");
        config.set("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
        config.set("reset.lobby-nether-world-name", "deepcore_lobby_nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);
                            when(creator.createWorld()).thenReturn(null);
                        })) {
            bukkit.when(() -> Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);

            assertNull(manager.getConfiguredLimboSpawn());
            assertTrue(creators.constructed().size() >= 1);
        }
    }

    @Test
    void isCurrentOverworldDiscoVariant_requiresTrackedWorldStillLoaded() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        assertFalse(manager.isCurrentOverworldDiscoVariant());

        UUID discoId = UUID.randomUUID();
        setField(manager, "discoOverworldId", discoId);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(discoId)).thenReturn(mock(World.class));
            assertTrue(manager.isCurrentOverworldDiscoVariant());

            bukkit.when(() -> Bukkit.getWorld(discoId)).thenReturn(null);
            assertFalse(manager.isCurrentOverworldDiscoVariant());
        }
    }

    @Test
    void resetThreeWorlds_schedulesDeferredResetAfterLobbyTeleport() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new java.io.File("."));

        ChallengeSessionWorldBridge bridge = mock(ChallengeSessionWorldBridge.class);
        WorldResetManager manager = new WorldResetManager(plugin, bridge);

        UUID lobbyId = UUID.randomUUID();
        setField(manager, "activeLobbyWorldId", lobbyId);

        World lobbyWorld = mock(World.class);
        World runWorld = mock(World.class);
        when(lobbyWorld.getSpawnLocation()).thenReturn(new Location(lobbyWorld, 0.0D, 64.0D, 0.0D));

        Player inRunWorld = mock(Player.class);
        Player alreadyInLobby = mock(Player.class);
        when(inRunWorld.getWorld()).thenReturn(runWorld);
        when(alreadyInLobby.getWorld()).thenReturn(lobbyWorld);

        Location preferredLobby = new Location(lobbyWorld, 4.0D, 70.0D, 8.0D);
        when(bridge.getPreferredLobbyTeleportLocation()).thenReturn(preferredLobby);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobbyWorld);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(inRunWorld, alreadyInLobby));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            manager.resetThreeWorlds(mock(CommandSender.class));
        }

        verify(inRunWorld).teleport(preferredLobby);
        verify(alreadyInLobby, never()).teleport(preferredLobby);
        verify(scheduler)
                .runTaskLater(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void resetThreeWorlds_usesLobbySpawnWhenPreferredTeleportUnavailable() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new java.io.File("."));

        ChallengeSessionWorldBridge bridge = mock(ChallengeSessionWorldBridge.class);
        when(bridge.getPreferredLobbyTeleportLocation()).thenReturn(null);
        WorldResetManager manager = new WorldResetManager(plugin, bridge);

        UUID lobbyId = UUID.randomUUID();
        setField(manager, "activeLobbyWorldId", lobbyId);

        World lobbyWorld = mock(World.class);
        Location lobbySpawn = new Location(lobbyWorld, 10.0D, 64.0D, 20.0D);
        when(lobbyWorld.getSpawnLocation()).thenReturn(lobbySpawn);
        Player player = mock(Player.class);
        World runWorld = mock(World.class);
        when(player.getWorld()).thenReturn(runWorld);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobbyWorld);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            manager.resetThreeWorlds(mock(CommandSender.class));
        }

        verify(player)
                .teleport(org.mockito.ArgumentMatchers.<Location>argThat(location -> location != null
                        && Math.abs(location.getX() - 10.5D) < 0.0001D
                        && Math.abs(location.getY() - 65.0D) < 0.0001D
                        && Math.abs(location.getZ() - 20.5D) < 0.0001D));
    }

    @Test
    void stopDiscoPreviewAudio_cancelsTasks_andRemovesActiveEntities() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        BukkitTask task = mock(BukkitTask.class);
        Entity display = mock(Entity.class);
        when(display.isValid()).thenReturn(true);

        setField(manager, "discoTasks", new ArrayList<>(List.of(task)));
        setField(manager, "activeDiscoEntities", new ArrayList<>(List.of(display)));
        setField(manager, "discoVisualWorldIds", new java.util.HashSet<>(Set.of(UUID.randomUUID())));
        setField(manager, "discoVisualsStartedWorldId", UUID.randomUUID());

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0.0D, 64.0D, 0.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(player));
            manager.stopDiscoPreviewAudio();
        }

        verify(player).stopSound("storytime:disco.jackpot", org.bukkit.SoundCategory.RECORDS);
        verify(player).stopSound("storytime:disco.jackpot", org.bukkit.SoundCategory.MASTER);
        verify(task).cancel();
        verify(display).remove();
    }

    @Test
    void stopDiscoPreviewAudio_doesNotRemoveInvalidEntities() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        Entity invalidDisplay = mock(Entity.class);
        when(invalidDisplay.isValid()).thenReturn(false);
        setField(manager, "activeDiscoEntities", new ArrayList<>(List.of(invalidDisplay)));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            manager.stopDiscoPreviewAudio();
        }

        verify(invalidDisplay, never()).remove();
    }

    @Test
    void cleanupNonDefaultWorldsOnStartup_keepsDirectoryWhenUnloadFails() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        Files.createDirectories(worldContainer.resolve("world"));
        Path staleWorldDir = worldContainer.resolve("stuck_world");
        Files.createDirectories(staleWorldDir);
        Files.writeString(staleWorldDir.resolve("level.dat"), "dummy");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        World defaultWorldObj = mock(World.class);
        when(defaultWorldObj.getName()).thenReturn("world");

        World stuckWorldObj = mock(World.class);
        when(stuckWorldObj.getName()).thenReturn("stuck_world");
        when(stuckWorldObj.getEntities()).thenReturn(List.of());
        when(stuckWorldObj.getLoadedChunks()).thenReturn(new Chunk[0]);

        World lobbyWorld = mock(World.class);
        UUID lobbyId = UUID.randomUUID();
        when(lobbyWorld.getSpawnLocation()).thenReturn(new Location(lobbyWorld, 0.0D, 64.0D, 0.0D));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        setField(manager, "activeLobbyWorldId", lobbyId);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(defaultWorldObj, stuckWorldObj));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorldObj);
            bukkit.when(() -> Bukkit.getWorld("stuck_world")).thenReturn(stuckWorldObj);
            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobbyWorld);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            bukkit.when(() -> Bukkit.unloadWorld(stuckWorldObj, true)).thenReturn(false);

            manager.cleanupNonDefaultWorldsOnStartup();
        }

        verify(log).warn("Startup cleanup: could not unload non-default world 'stuck_world'. Skipping deletion.");
    }

    @Test
    void buildFallbackWorldName_normalizesResetSuffixBeforeAppendingTimestamp() throws Exception {
        WorldResetManager manager = new WorldResetManager(newPluginStub(), mock(ChallengeSessionWorldBridge.class));

        String fallback =
                invokeString(manager, "buildFallbackWorldName", new Class<?>[] {String.class}, "arena_reset_12");

        assertTrue(fallback.matches("arena_reset_\\d+"));
        assertFalse(fallback.contains("_reset_12_reset_"));
    }

    @Test
    void normalizeBaseWorldName_returnsWorldWhenInputBlank() throws Exception {
        WorldResetManager manager = new WorldResetManager(newPluginStub(), mock(ChallengeSessionWorldBridge.class));

        String normalized = invokeString(manager, "normalizeBaseWorldName", new Class<?>[] {String.class}, " ");

        org.junit.jupiter.api.Assertions.assertEquals("world", normalized);
    }

    @Test
    void isPrimaryServerWorldName_requiresFirstLoadedWorldToMatchIgnoringCase() throws Exception {
        DeepCorePlugin plugin = newPluginStub();
        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World primary = mock(World.class);
        when(primary.getName()).thenReturn("MainWorld");
        World secondary = mock(World.class);
        when(secondary.getName()).thenReturn("secondary");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(primary, secondary));

            boolean matches =
                    invokeBoolean(manager, "isPrimaryServerWorldName", new Class<?>[] {String.class}, "mainworld");
            boolean misses =
                    invokeBoolean(manager, "isPrimaryServerWorldName", new Class<?>[] {String.class}, "secondary");

            assertTrue(matches);
            assertFalse(misses);
        }
    }

    @Test
    void unloadFailureDetails_listsPlayersInNamedWorld() throws Exception {
        DeepCorePlugin plugin = newPluginStub();
        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World targetWorld = mock(World.class);
        when(targetWorld.getName()).thenReturn("run_world");
        World otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("other");

        Player alice = mock(Player.class);
        when(alice.getWorld()).thenReturn(targetWorld);
        when(alice.getName()).thenReturn("Alice");
        Player bob = mock(Player.class);
        when(bob.getWorld()).thenReturn(otherWorld);
        when(bob.getName()).thenReturn("Bob");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(alice, bob));

            String details = invokeString(manager, "unloadFailureDetails", new Class<?>[] {String.class}, "run_world");

            assertTrue(details.contains("players still present"));
            assertTrue(details.contains("Alice"));
            assertFalse(details.contains("Bob"));
        }
    }

    @Test
    void updateLevelName_updatesExistingServerProperties() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Path serverProperties = worldContainer.resolve("server.properties");
        Files.writeString(serverProperties, "level-name=old_world\n");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        invokeVoid(manager, "updateLevelName", new Class<?>[] {String.class}, "fresh_world");

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(serverProperties)) {
            properties.load(reader);
        }
        org.junit.jupiter.api.Assertions.assertEquals("fresh_world", properties.getProperty("level-name"));
    }

    @Test
    void scheduleDeferredOldTrioCleanup_schedulesSingleDeferredCleanupAfterFifteenSeconds() throws Exception {
        DeepCorePlugin plugin = newPluginStub();
        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            invokeVoid(
                    manager,
                    "scheduleDeferredOldTrioCleanup",
                    new Class<?>[] {String.class, String.class, String.class},
                    "old_world",
                    "old_world_nether",
                    "old_world_the_end");
        }

        verify(scheduler)
                .runTaskLater(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(20L * 15L));
    }

    @Test
    void cleanupOldTrioDeferredOnce_whenAllDeleted_logsCompletionAndDoesNotReschedule() throws Exception {
        DeepCorePlugin plugin = newPluginStub();
        DeepCoreLogger log = plugin.getDeepCoreLogger();
        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            invokeVoid(
                    manager,
                    "cleanupOldTrioDeferredOnce",
                    new Class<?>[] {String.class, String.class, String.class},
                    "",
                    "",
                    "");
        }

        verify(log).debug("Deferred cleanup removed old world trio: ");
        verify(scheduler, never())
                .runTaskLater(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(20L * 30L));
    }

    @Test
    void cleanupOldTrioDeferredOnce_whenDeletionIncomplete_warnsAndDoesNotReschedule() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        UUID lobbyId = UUID.randomUUID();
        setField(manager, "activeLobbyWorldId", lobbyId);

        World staleWorld = mock(World.class);
        when(staleWorld.getEntities()).thenReturn(List.of());
        when(staleWorld.getLoadedChunks()).thenReturn(new Chunk[0]);

        World lobby = mock(World.class);
        when(lobby.getSpawnLocation()).thenReturn(new Location(lobby, 0.0D, 64.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            bukkit.when(() -> Bukkit.getWorld(lobbyId)).thenReturn(lobby);
            bukkit.when(() -> Bukkit.getWorld("stuck_world")).thenReturn(staleWorld);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            bukkit.when(() -> Bukkit.unloadWorld(staleWorld, true)).thenReturn(false);

            invokeVoid(
                    manager,
                    "cleanupOldTrioDeferredOnce",
                    new Class<?>[] {String.class, String.class, String.class},
                    "stuck_world",
                    "",
                    "");
        }

        verify(log)
                .warn("Deferred cleanup could not delete old world trio now (likely still loaded): stuck_world"
                        + ". Cleanup will be attempted again on next server startup.");
        verify(scheduler, never())
                .runTaskLater(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(20L * 30L));
    }

    @Test
    void resolveConfiguredOverworldName_prefersTrimmedLevelNameFromServerProperties() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name= custom_world  \n");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            String name = invokeString(manager, "resolveConfiguredOverworldName", new Class<?>[0]);
            org.junit.jupiter.api.Assertions.assertEquals("custom_world", name);
        }
    }

    @Test
    void resolveConfiguredOverworldName_fallsBackToFirstNonNetherEndWorld() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        World netherLike = mock(World.class);
        when(netherLike.getName()).thenReturn("anything_nether");
        World endLike = mock(World.class);
        when(endLike.getName()).thenReturn("anything_the_end");
        World runWorld = mock(World.class);
        when(runWorld.getName()).thenReturn("run_main");

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(netherLike, endLike, runWorld));
            String name = invokeString(manager, "resolveConfiguredOverworldName", new Class<?>[0]);
            org.junit.jupiter.api.Assertions.assertEquals("run_main", name);
        }
    }

    @Test
    void resolveConfiguredOverworldName_defaultsToWorldWhenNoCandidates() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            String name = invokeString(manager, "resolveConfiguredOverworldName", new Class<?>[0]);
            org.junit.jupiter.api.Assertions.assertEquals("world", name);
        }
    }

    @Test
    void isLikelyWorldDirectory_detectsKnownWorldMarkers() throws Exception {
        Path base = Files.createTempDirectory("deepcore-world-markers-");
        Path levelDatDir = base.resolve("leveldat");
        Path regionDir = base.resolve("region");
        Path emptyDir = base.resolve("empty");
        Files.createDirectories(levelDatDir);
        Files.createDirectories(regionDir.resolve("region"));
        Files.createDirectories(emptyDir);
        Files.writeString(levelDatDir.resolve("level.dat"), "dummy");

        WorldResetManager manager = new WorldResetManager(newPluginStub(), mock(ChallengeSessionWorldBridge.class));

        boolean levelDatResult =
                invokeBoolean(manager, "isLikelyWorldDirectory", new Class<?>[] {Path.class}, levelDatDir);
        boolean regionResult = invokeBoolean(manager, "isLikelyWorldDirectory", new Class<?>[] {Path.class}, regionDir);
        boolean emptyResult = invokeBoolean(manager, "isLikelyWorldDirectory", new Class<?>[] {Path.class}, emptyDir);

        assertTrue(levelDatResult);
        assertTrue(regionResult);
        assertFalse(emptyResult);
    }

    @Test
    void ensureDiscoPreviewEffects_returnsEarlyForNullOrWorldlessCenter() {
        WorldResetManager manager = new WorldResetManager(newPluginStub(), mock(ChallengeSessionWorldBridge.class));

        manager.ensureDiscoPreviewEffects(null);
        manager.ensureDiscoPreviewEffects(new Location(null, 1.0D, 2.0D, 3.0D));

        assertTrue(true);
    }

    @Test
    void ensureDiscoPreviewEffects_skipsWhenWorldAlreadyHasDiscoVisuals() throws Exception {
        WorldResetManager manager = new WorldResetManager(newPluginStub(), mock(ChallengeSessionWorldBridge.class));

        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);

        setField(manager, "discoVisualWorldIds", new java.util.HashSet<>(Set.of(worldId)));

        manager.ensureDiscoPreviewEffects(new Location(world, 10.0D, 64.0D, 10.0D));

        @SuppressWarnings("unchecked")
        Set<UUID> tracked = (Set<UUID>) getField(manager, "discoVisualWorldIds");
        org.junit.jupiter.api.Assertions.assertEquals(1, tracked.size());
        assertTrue(tracked.contains(worldId));
    }

    @Test
    void selectRandomLobbyWorld_returnsNullWhenConfiguredLobbyWorldsCannotLoad() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "deepcore_limbo");
        config.set("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
        config.set("reset.lobby-nether-world-name", "deepcore_lobby_nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);
                            when(creator.createWorld()).thenReturn(null);
                        })) {
            bukkit.when(() -> Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);

            assertNull(manager.selectRandomLobbyWorld());
            assertTrue(creators.constructed().size() >= 1);
        }
    }

    @Test
    void selectRandomLobbyWorld_setsActiveLobbyWorldId_whenSingleLobbyWorldAvailable() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.limbo-world-name", "deepcore_limbo");
        config.set("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
        config.set("reset.lobby-nether-world-name", "deepcore_lobby_nether");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));

        World limbo = mock(World.class);
        UUID limboId = UUID.randomUUID();
        when(limbo.getUID()).thenReturn(limboId);
        when(limbo.getSpawnLocation()).thenReturn(new Location(limbo, 0.0D, 64.0D, 0.0D));

        World lobbyOverworld = mock(World.class);
        UUID lobbyOverworldId = UUID.randomUUID();
        when(lobbyOverworld.getUID()).thenReturn(lobbyOverworldId);
        when(lobbyOverworld.getSpawnLocation()).thenReturn(new Location(lobbyOverworld, 0.0D, 64.0D, 0.0D));

        World lobbyNether = mock(World.class);
        UUID lobbyNetherId = UUID.randomUUID();
        when(lobbyNether.getUID()).thenReturn(lobbyNetherId);
        when(lobbyNether.getSpawnLocation()).thenReturn(new Location(lobbyNether, 0.0D, 64.0D, 0.0D));

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("deepcore_limbo")).thenReturn(limbo);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_overworld")).thenReturn(lobbyOverworld);
            bukkit.when(() -> Bukkit.getWorld("deepcore_lobby_nether")).thenReturn(lobbyNether);

            World selected = manager.selectRandomLobbyWorld();

            assertNotNull(selected);
            UUID activeId = (UUID) getField(manager, "activeLobbyWorldId");
            assertSame(selected.getUID(), activeId);
        }
    }

    @Test
    void performReset_primaryWorldPinnedFallsBackAndSchedulesDeferredCleanup() throws Exception {
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        Files.writeString(worldContainer.resolve("server.properties"), "level-name=world\n");

        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.disco-world-chance", 0.0D);
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        ChallengeSessionWorldBridge bridge = mock(ChallengeSessionWorldBridge.class);
        WorldResetManager manager = new WorldResetManager(plugin, bridge);

        World primaryWorld = mock(World.class);
        when(primaryWorld.getName()).thenReturn("world");

        World newOverworld = mock(World.class);
        Path newOverworldPath = worldContainer.resolve("fresh_world");
        when(newOverworld.getWorldFolder()).thenReturn(newOverworldPath.toFile());

        World newNether = mock(World.class);
        when(newNether.getWorldFolder())
                .thenReturn(worldContainer.resolve("fresh_world_nether").toFile());
        World newEnd = mock(World.class);
        when(newEnd.getWorldFolder())
                .thenReturn(worldContainer.resolve("fresh_world_the_end").toFile());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask cleanupTask = mock(BukkitTask.class);
        List<Runnable> scheduledCleanup = new ArrayList<>();
        when(scheduler.runTaskLater(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(20L * 15L)))
                .thenAnswer(invocation -> {
                    scheduledCleanup.add(invocation.getArgument(1));
                    return cleanupTask;
                });

        CommandSender sender = mock(CommandSender.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedConstruction<WorldCreator> creators =
                        org.mockito.Mockito.mockConstruction(WorldCreator.class, (creator, context) -> {
                            when(creator.environment(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.type(org.mockito.ArgumentMatchers.any()))
                                    .thenReturn(creator);
                            when(creator.generateStructures(org.mockito.ArgumentMatchers.anyBoolean()))
                                    .thenReturn(creator);
                            String name = (String) context.arguments().get(0);
                            if (name.endsWith("_nether")) {
                                when(creator.createWorld()).thenReturn(newNether);
                            } else if (name.endsWith("_the_end")) {
                                when(creator.createWorld()).thenReturn(newEnd);
                            } else {
                                when(creator.createWorld()).thenReturn(newOverworld);
                            }
                        })) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(primaryWorld));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);

            invokeVoid(
                    manager,
                    "performReset",
                    new Class<?>[] {CommandSender.class, String.class, String.class, String.class},
                    sender,
                    "world",
                    "world_nether",
                    "world_the_end");

            assertFalse(scheduledCleanup.isEmpty());
            scheduledCleanup.get(0).run();
        }

        verify(bridge).resetForNewRun();
        verify(bridge).refreshLobbyPreview();
        verify(log).sendInfo(sender, "Three-world reset complete.");
        assertTrue(Files.isDirectory(newOverworldPath.resolve("data")));
        assertTrue(Files.isDirectory(newOverworldPath.resolve("playerdata")));
    }

    @Test
    void discoPreviewSetup_andCleanupPaths_manageEntitiesAndAudio() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.disco-world-chance", 1.0D);
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        Path worldContainer = Files.createTempDirectory("deepcore-worlds-");
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(worldContainer.toFile());

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(world.getSpawnLocation()).thenReturn(new Location(world, 8.0D, 70.0D, -3.0D));
        when(world.getMinHeight()).thenReturn(0);

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 8.0D, 70.0D, -3.0D));
        when(player.getWorld()).thenReturn(world);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask discoTask = mock(BukkitTask.class);
        List<Runnable> scheduledTasks = new ArrayList<>();
        when(scheduler.runTaskTimer(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L)))
                .thenAnswer(invocation -> {
                    scheduledTasks.add(invocation.getArgument(1));
                    return discoTask;
                });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.createProfile(
                            org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new IllegalArgumentException("profile failed"));

            org.mockito.Mockito.doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.Consumer<? super ItemDisplay> consumer = invocation.getArgument(2);
                        ItemDisplay display = mock(ItemDisplay.class);
                        when(display.getUniqueId()).thenReturn(UUID.randomUUID());
                        when(display.isValid()).thenReturn(true);
                        consumer.accept(display);
                        return display;
                    })
                    .when(world)
                    .<ItemDisplay>spawn(
                            org.mockito.ArgumentMatchers.any(Location.class),
                            org.mockito.ArgumentMatchers.eq(ItemDisplay.class),
                            org.mockito.ArgumentMatchers.<java.util.function.Consumer<? super ItemDisplay>>any());

            org.mockito.Mockito.doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.Consumer<? super BlockDisplay> consumer = invocation.getArgument(2);
                        BlockDisplay display = mock(BlockDisplay.class);
                        when(display.getUniqueId()).thenReturn(UUID.randomUUID());
                        when(display.isValid()).thenReturn(true);
                        consumer.accept(display);
                        return display;
                    })
                    .when(world)
                    .<BlockDisplay>spawn(
                            org.mockito.ArgumentMatchers.any(Location.class),
                            org.mockito.ArgumentMatchers.eq(BlockDisplay.class),
                            org.mockito.ArgumentMatchers.<java.util.function.Consumer<? super BlockDisplay>>any());

            invokeVoid(manager, "maybeDecorateAsDiscoWorld", new Class<?>[] {World.class}, world);
            assertFalse(scheduledTasks.isEmpty());
            scheduledTasks.get(0).run();
            scheduledTasks.get(0).run();
        }

        verify(player)
                .stopSound(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(org.bukkit.SoundCategory.MASTER));
        verify(player)
                .playSound(
                        org.mockito.ArgumentMatchers.any(Location.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(org.bukkit.SoundCategory.MASTER),
                        org.mockito.ArgumentMatchers.eq(1.0F),
                        org.mockito.ArgumentMatchers.eq(1.0F));
        verify(discoTask).cancel();
        assertTrue(((Set<UUID>) getField(manager, "discoVisualWorldIds")).isEmpty());
    }

    @Test
    void removeAllBlockDisplayEntities_and_stopDiscoPreviewAudio_clearTrackedData() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer())
                .thenReturn(Files.createTempDirectory("deepcore-worlds-").toFile());

        WorldResetManager manager = new WorldResetManager(plugin, mock(ChallengeSessionWorldBridge.class));

        BukkitTask firstTask = mock(BukkitTask.class);
        BukkitTask secondTask = mock(BukkitTask.class);
        List<BukkitTask> discoTasks = (List<BukkitTask>) getField(manager, "discoTasks");
        List<Entity> activeDiscoEntities = (List<Entity>) getField(manager, "activeDiscoEntities");
        discoTasks.add(firstTask);
        Entity trackedDisplay = mock(ItemDisplay.class);
        when(trackedDisplay.isValid()).thenReturn(true);
        activeDiscoEntities.add(trackedDisplay);

        World world = mock(World.class);
        Entity taggedDisplay = mock(BlockDisplay.class);
        when(taggedDisplay.getScoreboardTags()).thenReturn(Set.of("deepcore_disco_entity"));
        Entity ignoredDisplay = mock(Entity.class);
        when(ignoredDisplay.getScoreboardTags()).thenReturn(Set.of("other_tag"));
        when(world.getEntitiesByClass(Entity.class)).thenReturn(List.of(taggedDisplay, ignoredDisplay));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));

            invokeVoid(manager, "removeAllBlockDisplayEntities", new Class<?>[] {});
        }

        verify(firstTask).cancel();
        verify(trackedDisplay).remove();
        verify(taggedDisplay).remove();
        verify(ignoredDisplay, never()).remove();
        assertTrue(discoTasks.isEmpty());
        assertTrue(activeDiscoEntities.isEmpty());

        discoTasks.add(secondTask);
        Entity audioDisplay = mock(ItemDisplay.class);
        when(audioDisplay.isValid()).thenReturn(true);
        activeDiscoEntities.add(audioDisplay);
        setField(manager, "discoVisualsStartedWorldId", UUID.randomUUID());

        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));

            manager.stopDiscoPreviewAudio();
        }

        verify(player)
                .stopSound(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(org.bukkit.SoundCategory.RECORDS));
        verify(player)
                .stopSound(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(org.bukkit.SoundCategory.MASTER));
        verify(secondTask).cancel();
        verify(audioDisplay).remove();
        assertTrue(discoTasks.isEmpty());
        assertTrue(activeDiscoEntities.isEmpty());
        assertNull(getField(manager, "discoVisualsStartedWorldId"));
    }

    private static DeepCorePlugin newPluginStub() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDeepCoreLogger()).thenReturn(mock(DeepCoreLogger.class));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorldContainer()).thenReturn(new File("."));
        return plugin;
    }

    private static String invokeString(Object target, String methodName, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return (String) method.invoke(target, args);
    }

    private static boolean invokeBoolean(Object target, String methodName, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return (boolean) method.invoke(target, args);
    }

    private static void invokeVoid(Object target, String methodName, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
