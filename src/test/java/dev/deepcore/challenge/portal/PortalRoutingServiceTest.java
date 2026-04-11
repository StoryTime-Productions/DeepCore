package dev.deepcore.challenge.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PortalRoutingServiceTest {

    @Test
    void resolveLinkedWorlds_returnNullForNullInput() {
        PortalRoutingService service = new PortalRoutingService(() -> null);

        assertNull(service.resolveLinkedPortalWorld(null));
        assertNull(service.resolveLinkedEndWorld(null));
    }

    @Test
    void resolveLinkedPortalWorld_usesActiveWorldPairThenFallsBackByName() {
        WorldResetManager resetManager = mock(WorldResetManager.class);
        PortalRoutingService service = new PortalRoutingService(() -> resetManager);

        World overworld = mock(World.class);
        UUID overworldId = UUID.randomUUID();
        when(overworld.getUID()).thenReturn(overworldId);
        when(overworld.getName()).thenReturn("run_world");

        World nether = mock(World.class);
        UUID netherId = UUID.randomUUID();
        when(nether.getUID()).thenReturn(netherId);
        when(nether.getName()).thenReturn("run_world_nether");

        when(resetManager.getCurrentOverworld()).thenReturn(overworld);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("run_world_nether")).thenReturn(nether);
            bukkit.when(() -> Bukkit.getWorld("run_world")).thenReturn(overworld);

            assertSame(nether, service.resolveLinkedPortalWorld(overworld));
            assertSame(overworld, service.resolveLinkedPortalWorld(nether));
        }

        World fallbackNether = mock(World.class);
        when(fallbackNether.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(fallbackNether.getName()).thenReturn("custom_nether");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("custom")).thenReturn(overworld);
            assertSame(overworld, service.resolveLinkedPortalWorld(fallbackNether));
        }

        World fallbackOverworld = mock(World.class);
        when(fallbackOverworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(fallbackOverworld.getName()).thenReturn("custom");
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("custom_nether")).thenReturn(nether);
            assertSame(nether, service.resolveLinkedPortalWorld(fallbackOverworld));
        }
    }

    @Test
    void resolveLinkedEndWorld_usesActiveWorldsAndNameFallback() {
        WorldResetManager resetManager = mock(WorldResetManager.class);
        PortalRoutingService service = new PortalRoutingService(() -> resetManager);

        World overworld = mock(World.class);
        UUID overworldId = UUID.randomUUID();
        when(overworld.getUID()).thenReturn(overworldId);
        when(overworld.getName()).thenReturn("run_world");

        World nether = mock(World.class);
        UUID netherId = UUID.randomUUID();
        when(nether.getUID()).thenReturn(netherId);

        World theEnd = mock(World.class);
        UUID endId = UUID.randomUUID();
        when(theEnd.getUID()).thenReturn(endId);

        when(resetManager.getCurrentOverworld()).thenReturn(overworld);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("run_world_nether")).thenReturn(nether);
            bukkit.when(() -> Bukkit.getWorld("run_world_the_end")).thenReturn(theEnd);
            bukkit.when(() -> Bukkit.getWorld("run_world")).thenReturn(overworld);

            assertSame(overworld, service.resolveLinkedEndWorld(theEnd));
            assertSame(theEnd, service.resolveLinkedEndWorld(overworld));
            assertSame(theEnd, service.resolveLinkedEndWorld(nether));
        }

        World fallbackEnd = mock(World.class);
        when(fallbackEnd.getEnvironment()).thenReturn(World.Environment.THE_END);
        when(fallbackEnd.getName()).thenReturn("custom_the_end");
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("custom")).thenReturn(overworld);
            assertSame(overworld, service.resolveLinkedEndWorld(fallbackEnd));
        }
    }

    @Test
    void buildLinkedPortalTarget_handlesNullAndComputesSafeTargetY() {
        PortalRoutingService service = new PortalRoutingService(() -> null);
        assertNull(service.buildLinkedPortalTarget(null, mock(World.class), 1.0D));
        assertNull(service.buildLinkedPortalTarget(mock(Location.class), null, 1.0D));

        Location source = mock(Location.class);
        when(source.getX()).thenReturn(10.0D);
        when(source.getZ()).thenReturn(20.0D);
        when(source.getYaw()).thenReturn(90.0F);
        when(source.getPitch()).thenReturn(10.0F);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING)))
                .thenReturn(70);

        Location target = service.buildLinkedPortalTarget(source, world, 0.5D);

        assertNotNull(target);
        assertEquals(5.0D, target.getX());
        assertEquals(71.0D, target.getY());
        assertEquals(10.0D, target.getZ());
    }

    @Test
    void resolveEndPortalTarget_handlesNullEndAndEndReturnPath() {
        PortalRoutingService service = new PortalRoutingService(() -> null);
        assertNull(service.resolveEndPortalTarget(mock(World.class), null));

        World fromEnd = mock(World.class);
        when(fromEnd.getEnvironment()).thenReturn(World.Environment.THE_END);

        World overworld = mock(World.class);
        Location spawn = new Location(overworld, 0.0D, 64.0D, 0.0D);
        when(overworld.getSpawnLocation()).thenReturn(spawn);

        Location target = service.resolveEndPortalTarget(fromEnd, overworld);
        assertEquals(0.5D, target.getX());
        assertEquals(64.0D, target.getY());
        assertEquals(0.5D, target.getZ());
    }

    @Test
    void resolveEndPortalTarget_nonEndSource_buildsEndPlatform() {
        PortalRoutingService service = new PortalRoutingService(() -> null);

        World fromOverworld = mock(World.class);
        when(fromOverworld.getEnvironment()).thenReturn(World.Environment.NORMAL);

        World targetEnd = mock(World.class);
        when(targetEnd.getEnvironment()).thenReturn(World.Environment.THE_END);
        Block block = mock(Block.class);
        when(targetEnd.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

        Location target = service.resolveEndPortalTarget(fromOverworld, targetEnd);

        assertEquals(100.5D, target.getX());
        assertEquals(50.0D, target.getY());
        assertEquals(0.5D, target.getZ());
        verify(block, org.mockito.Mockito.atLeastOnce()).setType(Material.OBSIDIAN, false);
        verify(block, org.mockito.Mockito.atLeastOnce()).setType(Material.AIR, false);
    }

    @Test
    void tryHandleEndPortalTransit_appliesTeleportAndCooldown() {
        WorldResetManager resetManager = mock(WorldResetManager.class);
        PortalRoutingService service = new PortalRoutingService(() -> resetManager);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        World fromWorld = mock(World.class);
        when(fromWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(fromWorld.getName()).thenReturn("run_world");
        UUID fromId = UUID.randomUUID();
        when(fromWorld.getUID()).thenReturn(fromId);

        Location playerLocation = mock(Location.class);
        when(playerLocation.getWorld()).thenReturn(fromWorld);
        when(playerLocation.getBlockX()).thenReturn(10);
        when(playerLocation.getBlockY()).thenReturn(64);
        when(playerLocation.getBlockZ()).thenReturn(10);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getWorld()).thenReturn(fromWorld);

        Block feetBlock = mock(Block.class);
        when(feetBlock.getType()).thenReturn(Material.END_PORTAL);
        Block belowBlock = mock(Block.class);
        when(belowBlock.getType()).thenReturn(Material.AIR);
        when(fromWorld.getBlockAt(playerLocation)).thenReturn(feetBlock);
        when(fromWorld.getBlockAt(10, 63, 10)).thenReturn(belowBlock);

        World activeOverworld = mock(World.class);
        when(activeOverworld.getName()).thenReturn("run_world");
        when(activeOverworld.getUID()).thenReturn(fromId);
        when(resetManager.getCurrentOverworld()).thenReturn(activeOverworld);

        World targetEnd = mock(World.class);
        UUID targetEndId = UUID.randomUUID();
        when(targetEnd.getUID()).thenReturn(targetEndId);
        when(targetEnd.getEnvironment()).thenReturn(World.Environment.THE_END);

        Block genericBlock = mock(Block.class);
        when(targetEnd.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(genericBlock);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("run_world_nether")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("run_world_the_end")).thenReturn(targetEnd);

            boolean first = service.tryHandleEndPortalTransit(player, true, world -> false);
            boolean second = service.tryHandleEndPortalTransit(player, true, world -> false);

            assertTrue(first);
            assertFalse(second);
            verify(player).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.END_PORTAL));
        }
    }

    @Test
    void tryHandleEndPortalTransit_returnsFalseForGuardConditions() {
        PortalRoutingService service = new PortalRoutingService(() -> null);

        assertFalse(service.tryHandleEndPortalTransit(null, true, world -> false));

        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);

        assertFalse(service.tryHandleEndPortalTransit(player, false, w -> false));
        assertFalse(service.tryHandleEndPortalTransit(player, true, w -> true));
        assertFalse(service.tryHandleEndPortalTransit(player, true, w -> false));
        verify(player, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.END_PORTAL));
    }

    @Test
    void tryHandleEndPortalTransit_returnsFalseWhenLinkedTargetCannotResolve() {
        WorldResetManager resetManager = mock(WorldResetManager.class);
        PortalRoutingService service = new PortalRoutingService(() -> resetManager);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        World fromWorld = mock(World.class);
        when(fromWorld.getName()).thenReturn("mystery");
        when(fromWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(player.getWorld()).thenReturn(fromWorld);

        Location playerLocation = mock(Location.class);
        when(playerLocation.getWorld()).thenReturn(fromWorld);
        when(playerLocation.getBlockX()).thenReturn(1);
        when(playerLocation.getBlockY()).thenReturn(64);
        when(playerLocation.getBlockZ()).thenReturn(1);
        when(player.getLocation()).thenReturn(playerLocation);

        Block feet = mock(Block.class);
        when(feet.getType()).thenReturn(Material.END_PORTAL);
        when(fromWorld.getBlockAt(playerLocation)).thenReturn(feet);
        when(fromWorld.getBlockAt(1, 63, 1)).thenReturn(feet);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("mystery_nether")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("mystery_the_end")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("mystery")).thenReturn(null);

            assertFalse(service.tryHandleEndPortalTransit(player, true, world -> false));
        }

        verify(player, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.END_PORTAL));
    }
}
