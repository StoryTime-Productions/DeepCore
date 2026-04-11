package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PrepAreaServiceTest {

    @Test
    void areaBoundsChecks_andClampBehavior_areCorrect() {
        PrepAreaService service = new PrepAreaService(20);
        World world = mock(World.class);
        Location spawn = new Location(world, 100.0D, 64.0D, 100.0D);
        when(world.getSpawnLocation()).thenReturn(spawn);

        Location inside = new Location(world, 105.0D, 64.0D, 95.0D);
        Location outside = new Location(world, 130.0D, 64.0D, 70.0D);

        assertTrue(service.isWithinPrepArea(inside));
        assertFalse(service.isWithinPrepArea(outside));

        Location clamped = service.clampToPrepArea(outside);
        assertNotNull(clamped);
        assertTrue(service.isWithinPrepArea(clamped));
    }

    @Test
    void applyBorder_clearsInRunningOrLobby_andSetsBorderOtherwise() {
        PrepAreaService service = new PrepAreaService(30);
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);

        service.applyBorder(player, true, w -> false);
        verify(player, times(1)).setWorldBorder(null);

        service.applyBorder(player, false, w -> true);
        verify(player, times(2)).setWorldBorder(null);

        Location spawn = new Location(world, 0.0D, 64.0D, 0.0D);
        when(world.getSpawnLocation()).thenReturn(spawn);
        WorldBorder border = mock(WorldBorder.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::createWorldBorder).thenReturn(border);
            service.applyBorder(player, false, w -> false);
            verify(border).setSize(30.0D);
            verify(player).setWorldBorder(border);
        }
    }

    @Test
    void applyBordersToOnlinePlayers_andClearBorders_iterateAllPlayers() {
        PrepAreaService service = new PrepAreaService(40);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        World world = mock(World.class);
        when(p1.getWorld()).thenReturn(world);
        when(p2.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.0D, 64.0D, 0.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            bukkit.when(Bukkit::createWorldBorder).thenReturn(mock(WorldBorder.class));

            service.applyBordersToOnlinePlayers(false, w -> false);
            service.clearBorders();

            verify(p1).setWorldBorder(null);
            verify(p2).setWorldBorder(null);
        }
    }
}
