package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class RunProgressServiceTest {

    @Test
    void milestoneMarkingAndObjectiveText_followExpectedFlow() {
        RunProgressService service = new RunProgressService();

        assertEquals("Enter the Nether", service.currentObjectiveText(0));
        assertTrue(service.markNetherReached(1_000L));
        assertFalse(service.markNetherReached(1_100L));
        assertEquals("Collect Blaze Rods (2/6)", service.currentObjectiveText(2));

        OptionalLong split = service.maybeMarkBlazeObjectiveReached(true, 6, 1_600L);
        assertTrue(split.isPresent());
        assertEquals(600L, split.getAsLong());
        assertEquals("Enter the End", service.currentObjectiveText(6));

        assertTrue(service.markEndReached(2_000L));
        assertEquals("Kill the Ender Dragon", service.currentObjectiveText(6));

        service.markDragonKilled(2_500L);
        assertTrue(service.isDragonKilled());
        assertEquals("Challenge Complete", service.currentObjectiveText(6));
        assertEquals(2_500L, service.resolveElapsedReferenceTime(3_000L));
    }

    @Test
    void maybeMarkBlazeObjectiveReached_requiresRunningNetherAndThreshold() {
        RunProgressService service = new RunProgressService();
        service.markNetherReached(1_000L);

        assertTrue(service.maybeMarkBlazeObjectiveReached(false, 6, 1_200L).isEmpty());
        assertTrue(service.maybeMarkBlazeObjectiveReached(true, 5, 1_200L).isEmpty());
        assertTrue(service.maybeMarkBlazeObjectiveReached(true, 6, 1_400L).isPresent());
        assertTrue(service.maybeMarkBlazeObjectiveReached(true, 10, 1_800L).isEmpty());
    }

    @Test
    void resolveElapsedReferenceTime_withoutDragonKill_returnsCurrentTime() {
        RunProgressService service = new RunProgressService();

        assertEquals(3_333L, service.resolveElapsedReferenceTime(3_333L));
    }

    @Test
    void updateMilestonesFromParticipants_withNoParticipants_keepsMilestonesUnset() {
        RunProgressService service = new RunProgressService();

        service.updateMilestonesFromParticipants(List.of(), 2_000L);

        assertFalse(service.hasReachedNether());
        assertFalse(service.hasReachedEnd());
    }

    @Test
    void updateMilestonesFromParticipants_marksNetherAndEndFromParticipantWorlds() {
        RunProgressService service = new RunProgressService();
        Player netherPlayer = mock(Player.class);
        Player endPlayer = mock(Player.class);
        World nether = mock(World.class);
        World end = mock(World.class);

        when(netherPlayer.getWorld()).thenReturn(nether);
        when(endPlayer.getWorld()).thenReturn(end);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(end.getEnvironment()).thenReturn(World.Environment.THE_END);

        service.updateMilestonesFromParticipants(List.of(netherPlayer, endPlayer), 2_000L);

        assertTrue(service.hasReachedNether());
        assertTrue(service.hasReachedEnd());
    }

    @Test
    void countTeamBlazeRods_sumsAcrossParticipantInventories() {
        RunProgressService service = new RunProgressService();
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        PlayerInventory inv1 = mock(PlayerInventory.class);
        PlayerInventory inv2 = mock(PlayerInventory.class);

        when(p1.getInventory()).thenReturn(inv1);
        when(p2.getInventory()).thenReturn(inv2);
        ItemStack blaze2 = mock(ItemStack.class);
        when(blaze2.getType()).thenReturn(Material.BLAZE_ROD);
        when(blaze2.getAmount()).thenReturn(2);
        ItemStack dirt = mock(ItemStack.class);
        when(dirt.getType()).thenReturn(Material.DIRT);
        when(dirt.getAmount()).thenReturn(5);
        ItemStack blaze4 = mock(ItemStack.class);
        when(blaze4.getType()).thenReturn(Material.BLAZE_ROD);
        when(blaze4.getAmount()).thenReturn(4);

        when(inv1.getContents()).thenReturn(new ItemStack[] {blaze2, dirt});
        when(inv2.getContents()).thenReturn(new ItemStack[] {blaze4, null});

        assertEquals(6, service.countTeamBlazeRods(List.of(p1, p2)));
    }

    @Test
    void calculateSectionDurations_usesRecordedMilestonesOrFallbacks() {
        RunProgressService service = new RunProgressService();
        service.markNetherReached(2_000L);
        service.maybeMarkBlazeObjectiveReached(true, 6, 3_000L);
        service.markEndReached(4_000L);

        RunProgressService.SectionDurations durations = service.calculateSectionDurations(1_000L, 5_000L, 999L);

        assertEquals(1_000L, durations.overworldToNetherMs());
        assertEquals(1_000L, durations.netherToBlazeRodsMs());
        assertEquals(1_000L, durations.blazeRodsToEndMs());
        assertEquals(2_000L, durations.netherToEndMs());
        assertEquals(1_000L, durations.endToDragonMs());
    }

    @Test
    void snapshotForDisplay_reflectsCurrentProgressFlags() {
        RunProgressService service = new RunProgressService();

        RunProgressService.RunProgressSnapshot initial = service.snapshotForDisplay(0);
        assertFalse(initial.reachedNether());
        assertFalse(initial.reachedBlazeObjective());
        assertFalse(initial.reachedEnd());
        assertFalse(initial.dragonKilled());

        service.markNetherReached(1_000L);
        service.maybeMarkBlazeObjectiveReached(true, 6, 1_500L);
        service.markEndReached(2_000L);
        service.markDragonKilled(2_500L);

        RunProgressService.RunProgressSnapshot done = service.snapshotForDisplay(6);
        assertTrue(done.reachedNether());
        assertTrue(done.reachedBlazeObjective());
        assertTrue(done.reachedEnd());
        assertTrue(done.dragonKilled());
    }

    @Test
    void calculateSectionDurations_usesFallbackWhenMilestonesMissing() {
        RunProgressService service = new RunProgressService();

        RunProgressService.SectionDurations durations = service.calculateSectionDurations(1_000L, 5_000L, 777L);

        assertEquals(777L, durations.overworldToNetherMs());
        assertEquals(777L, durations.netherToBlazeRodsMs());
        assertEquals(777L, durations.blazeRodsToEndMs());
        assertEquals(777L, durations.netherToEndMs());
        assertEquals(777L, durations.endToDragonMs());
    }
}
