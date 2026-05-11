package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.logging.DeepCoreLogger;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedRunStateServiceTest {

    @TempDir
    File tempDir;

    private SavedRunStateService makeService() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        return new SavedRunStateService(plugin, log);
    }

    private SavedRunStateService.PlayerSnapshot emptyPlayerSnapshot(String world) {
        return new SavedRunStateService.PlayerSnapshot(
                world,
                1.0,
                64.0,
                3.0,
                45.0f,
                10.0f,
                18.0,
                20.0,
                16,
                3.5f,
                1.2f,
                0,
                300,
                5,
                0.75f,
                1400,
                "SURVIVAL",
                false,
                false,
                new ItemStack[36],
                new ItemStack[4],
                new ItemStack[1],
                List.of());
    }

    @Test
    void hasSavedRun_returnsFalseWhenNoFile() {
        assertFalse(makeService().hasSavedRun());
    }

    @Test
    void hasSavedRun_returnsTrueAfterSave() {
        SavedRunStateService service = makeService();
        SavedRunStateService.SavedRunSnapshot snapshot = new SavedRunStateService.SavedRunSnapshot(
                1000L,
                500L,
                50L,
                true,
                200L,
                true,
                350L,
                false,
                0L,
                List.of("uuid-a"),
                List.of("comp-x"),
                "normal",
                Map.of());

        service.saveRun(snapshot);

        assertTrue(service.hasSavedRun());
    }

    @Test
    void saveAndLoad_roundTrip_preservesRunLevelFields() throws Exception {
        SavedRunStateService service = makeService();
        SavedRunStateService.SavedRunSnapshot original = new SavedRunStateService.SavedRunSnapshot(
                99_000L,
                1_000L,
                250L,
                true,
                3_000L,
                true,
                5_000L,
                true,
                8_000L,
                List.of("uuid-1", "uuid-2"),
                List.of("HARDCORE", "SHARED_HEALTH"),
                "hard",
                Map.of());

        service.saveRun(original);
        Optional<SavedRunStateService.SavedRunSnapshot> loaded = service.loadSavedRun();

        assertTrue(loaded.isPresent());
        SavedRunStateService.SavedRunSnapshot s = loaded.get();
        assertEquals(99_000L, s.savedAtMs());
        assertEquals(1_000L, s.runStartMs());
        assertEquals(250L, s.accumulatedPausedMs());
        assertTrue(s.reachedNether());
        assertEquals(3_000L, s.netherMs());
        assertTrue(s.reachedBlazeObjective());
        assertEquals(5_000L, s.blazeObjectiveMs());
        assertTrue(s.reachedEnd());
        assertEquals(8_000L, s.endMs());
        assertEquals(List.of("uuid-1", "uuid-2"), s.participantUuids());
        assertEquals(List.of("HARDCORE", "SHARED_HEALTH"), s.enabledComponents());
        assertEquals("hard", s.difficulty());
    }

    @Test
    void saveAndLoad_roundTrip_preservesPlayerSnapshotFields() {
        SavedRunStateService service = makeService();
        SavedRunStateService.PlayerSnapshot ps = emptyPlayerSnapshot("world");
        SavedRunStateService.SavedRunSnapshot snapshot = new SavedRunStateService.SavedRunSnapshot(
                0L, 0L, 0L, false, 0L, false, 0L, false, 0L, List.of(), List.of(), "normal", Map.of("player-uuid", ps));

        service.saveRun(snapshot);
        Optional<SavedRunStateService.SavedRunSnapshot> loaded = service.loadSavedRun();

        assertTrue(loaded.isPresent());
        Map<String, SavedRunStateService.PlayerSnapshot> snapshots =
                loaded.get().playerSnapshots();
        assertTrue(snapshots.containsKey("player-uuid"));
        SavedRunStateService.PlayerSnapshot restored = snapshots.get("player-uuid");
        assertEquals("world", restored.worldName());
        assertEquals(1.0, restored.x(), 0.001);
        assertEquals(64.0, restored.y(), 0.001);
        assertEquals(3.0, restored.z(), 0.001);
        assertEquals(45.0f, restored.yaw(), 0.001f);
        assertEquals(10.0f, restored.pitch(), 0.001f);
        assertEquals(18.0, restored.health(), 0.001);
        assertEquals(20.0, restored.maxHealth(), 0.001);
        assertEquals(16, restored.foodLevel());
        assertEquals(3.5f, restored.saturation(), 0.001f);
        assertEquals(1.2f, restored.exhaustion(), 0.001f);
        assertEquals(0, restored.fireTicks());
        assertEquals(300, restored.remainingAir());
        assertEquals(5, restored.level());
        assertEquals(0.75f, restored.exp(), 0.001f);
        assertEquals(1400, restored.totalExperience());
        assertEquals("SURVIVAL", restored.gameMode());
        assertFalse(restored.allowFlight());
        assertFalse(restored.flying());
        assertTrue(restored.potionEffects().isEmpty());
    }

    @Test
    void loadSavedRun_returnsEmptyWhenNoFile() {
        assertTrue(makeService().loadSavedRun().isEmpty());
    }

    @Test
    void clearSavedRun_deletesFile() {
        SavedRunStateService service = makeService();
        service.saveRun(new SavedRunStateService.SavedRunSnapshot(
                0L, 0L, 0L, false, 0L, false, 0L, false, 0L, List.of(), List.of(), "normal", Map.of()));

        assertTrue(service.hasSavedRun());
        service.clearSavedRun();
        assertFalse(service.hasSavedRun());
    }

    @Test
    void capturePlayer_capturesPositionAndStats() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 65.0, -5.0, 90.0f, 0.0f);
        when(player.getLocation()).thenReturn(loc);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(inventory.getExtraContents()).thenReturn(new ItemStack[1]);
        when(player.getHealth()).thenReturn(14.0);
        when(player.getFoodLevel()).thenReturn(18);
        when(player.getSaturation()).thenReturn(2.5f);
        when(player.getExhaustion()).thenReturn(0.8f);
        when(player.getFireTicks()).thenReturn(0);
        when(player.getRemainingAir()).thenReturn(300);
        when(player.getLevel()).thenReturn(3);
        when(player.getExp()).thenReturn(0.4f);
        when(player.getTotalExperience()).thenReturn(120);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(false);
        when(player.isFlying()).thenReturn(false);
        when(player.getActivePotionEffects()).thenReturn(List.of());

        SavedRunStateService.PlayerSnapshot snapshot = SavedRunStateService.capturePlayer(player);

        assertEquals("world", snapshot.worldName());
        assertEquals(10.0, snapshot.x(), 0.001);
        assertEquals(65.0, snapshot.y(), 0.001);
        assertEquals(-5.0, snapshot.z(), 0.001);
        assertEquals(14.0, snapshot.health(), 0.001);
        assertEquals(18, snapshot.foodLevel());
        assertEquals(2.5f, snapshot.saturation(), 0.001f);
        assertEquals("SURVIVAL", snapshot.gameMode());
        assertTrue(snapshot.potionEffects().isEmpty());
    }

    @Test
    void applySnapshot_restoresPositionAndStats() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        AttributeInstance attr = mock(AttributeInstance.class);
        when(attr.getBaseValue()).thenReturn(20.0);
        try {
            Attribute maxHealth = resolveMaxHealthAttribute();
            when(player.getAttribute(maxHealth)).thenReturn(attr);
        } catch (Exception ignored) {
        }

        SavedRunStateService.PlayerSnapshot snapshot = new SavedRunStateService.PlayerSnapshot(
                "world",
                5.0,
                70.0,
                -3.0,
                180.0f,
                5.0f,
                16.0,
                20.0,
                14,
                1.0f,
                0.5f,
                0,
                300,
                2,
                0.3f,
                60,
                "SURVIVAL",
                false,
                false,
                new ItemStack[36],
                new ItemStack[4],
                new ItemStack[1],
                List.of());

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("world")).thenReturn(world);
            SavedRunStateService.applySnapshot(player, snapshot);
        }

        verify(player).setHealth(16.0);
        verify(player).setFoodLevel(14);
        verify(player).setSaturation(1.0f);
        verify(player).setExhaustion(0.5f);
        verify(player).setLevel(2);
        verify(player).setExp(0.3f);
        verify(player).setTotalExperience(60);
        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(player).setAllowFlight(false);
        verify(inventory).setStorageContents(org.mockito.ArgumentMatchers.any());
        verify(inventory).setArmorContents(org.mockito.ArgumentMatchers.any());
        verify(inventory).setExtraContents(org.mockito.ArgumentMatchers.any());
        verify(player).updateInventory();
    }

    @Test
    void applySnapshot_clampsBelowZeroHealthToHalfHeart() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        AttributeInstance attr = mock(AttributeInstance.class);
        when(attr.getBaseValue()).thenReturn(20.0);
        try {
            when(player.getAttribute(resolveMaxHealthAttribute())).thenReturn(attr);
        } catch (Exception ignored) {
        }

        SavedRunStateService.PlayerSnapshot snapshot = new SavedRunStateService.PlayerSnapshot(
                "",
                0.0,
                64.0,
                0.0,
                0.0f,
                0.0f,
                -5.0,
                20.0,
                20,
                0.0f,
                0.0f,
                0,
                300,
                0,
                0.0f,
                0,
                "SURVIVAL",
                false,
                false,
                new ItemStack[36],
                new ItemStack[4],
                new ItemStack[1],
                List.of());

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("")).thenReturn(null);
            SavedRunStateService.applySnapshot(player, snapshot);
        }

        verify(player).setHealth(0.5);
    }

    private static Attribute resolveMaxHealthAttribute() throws Exception {
        try {
            return (Attribute) Attribute.class.getField("MAX_HEALTH").get(null);
        } catch (ReflectiveOperationException ignored) {
            return (Attribute) Attribute.class.getField("GENERIC_MAX_HEALTH").get(null);
        }
    }
}
