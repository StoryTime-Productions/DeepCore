package dev.deepcore.challenge.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RespawnRoutingServiceTest {

    @Test
    void recordDeathWorld_ignoresNullInputs() {
        RespawnRoutingService service = new RespawnRoutingService(() -> null, w -> w, w -> w);

        service.recordDeathWorld(null, mock(World.class));
        service.recordDeathWorld(UUID.randomUUID(), null);

        UUID id = UUID.randomUUID();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(null);
            assertNull(service.resolveRunRespawnLocation(id));
        }
    }

    @Test
    void resolveRunRespawnLocation_usesRecordedDeathWorld_andNetherResolver() {
        World nether = mock(World.class);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(nether.getName()).thenReturn("run_nether");

        World resolved = mock(World.class);
        when(resolved.getSpawnLocation()).thenReturn(new Location(resolved, 10.0D, 70.0D, 10.0D));

        RespawnRoutingService service = new RespawnRoutingService(() -> null, w -> resolved, w -> w);
        UUID id = UUID.randomUUID();
        service.recordDeathWorld(id, nether);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("run_nether")).thenReturn(nether);
            Location respawn = service.resolveRunRespawnLocation(id);

            assertEquals(10.5D, respawn.getX());
            assertEquals(70.0D, respawn.getY());
            assertEquals(10.5D, respawn.getZ());
        }
    }

    @Test
    void resolveRunRespawnLocation_usesLastDeathLocation_thenEndResolver_thenFallbackOverworld() {
        World end = mock(World.class);
        when(end.getEnvironment()).thenReturn(World.Environment.THE_END);

        World endResolved = mock(World.class);
        when(endResolved.getSpawnLocation()).thenReturn(new Location(endResolved, 0.0D, 80.0D, 0.0D));

        World fallbackOverworld = mock(World.class);
        when(fallbackOverworld.getSpawnLocation()).thenReturn(new Location(fallbackOverworld, 5.0D, 65.0D, 5.0D));

        WorldResetManager manager = mock(WorldResetManager.class);
        when(manager.getCurrentOverworld()).thenReturn(fallbackOverworld);

        RespawnRoutingService service = new RespawnRoutingService(() -> manager, w -> w, w -> endResolved);

        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getLastDeathLocation()).thenReturn(new Location(end, 1.0D, 70.0D, 1.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);

            Location fromEnd = service.resolveRunRespawnLocation(id);
            assertSame(endResolved, fromEnd.getWorld());

            when(player.getLastDeathLocation()).thenReturn(null);
            Location fromFallback = service.resolveRunRespawnLocation(id);
            assertSame(fallbackOverworld, fromFallback.getWorld());
        }
    }

    @Test
    void resolveRunRespawnLocation_returnsNullWhenNoWorldAvailable() {
        RespawnRoutingService service = new RespawnRoutingService(() -> null, w -> null, w -> null);

        UUID id = UUID.randomUUID();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(null);
            assertNull(service.resolveRunRespawnLocation(id));
        }
    }
}
