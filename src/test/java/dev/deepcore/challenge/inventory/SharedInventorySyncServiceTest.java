package dev.deepcore.challenge.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class SharedInventorySyncServiceTest {

    @Test
    void removeMaterialFromItemArray_handlesNullZeroAndPartialRemoval() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin, player -> true, () -> true, () -> true, List::of, () -> false, player -> {}, new HashMap<>());

        ItemStack keep = mock(ItemStack.class);
        when(keep.getType()).thenReturn(Material.BREAD);
        when(keep.getAmount()).thenReturn(4);

        ItemStack removeAll = mock(ItemStack.class);
        when(removeAll.getType()).thenReturn(Material.BREAD);
        when(removeAll.getAmount()).thenReturn(2);

        ItemStack[] contents = new ItemStack[] {keep, null, removeAll};

        int removed = invokeRemoveMaterialFromItemArray(service, contents, Material.BREAD, 6);
        ItemStack[] partialContents = new ItemStack[] {mock(ItemStack.class)};
        when(partialContents[0].getType()).thenReturn(Material.BREAD);
        when(partialContents[0].getAmount()).thenReturn(4);
        int partialRemoved = invokeRemoveMaterialFromItemArray(service, partialContents, Material.BREAD, 3);
        int zeroRemoved = invokeRemoveMaterialFromItemArray(service, null, Material.BREAD, 0);

        assertEquals(6, removed);
        assertEquals(3, partialRemoved);
        assertEquals(0, zeroRemoved);
        assertNull(contents[0]);
        assertNull(contents[2]);
        verify(partialContents[0]).setAmount(1);
    }

    @Test
    void requestSharedInventorySync_ignoresWhenInactiveOrSharedInventoryDisabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);

        SharedInventorySyncService inactiveService = new SharedInventorySyncService(
                plugin, player -> false, () -> true, () -> true, List::of, () -> false, player -> {}, new HashMap<>());

        SharedInventorySyncService disabledService = new SharedInventorySyncService(
                plugin, player -> true, () -> false, () -> true, List::of, () -> false, player -> {}, new HashMap<>());

        inactiveService.requestSharedInventorySync(source);
        disabledService.requestSharedInventorySync(source);
    }

    @Test
    void requestSharedInventorySync_runsDeferredSync_andEnforcesSlotCap() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player target = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(target.getUniqueId()).thenReturn(targetId);
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(target.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory sourceInventory = mock(PlayerInventory.class);
        PlayerInventory targetInventory = mock(PlayerInventory.class);
        when(source.getInventory()).thenReturn(sourceInventory);
        when(target.getInventory()).thenReturn(targetInventory);

        ItemStack sourceStack = mock(ItemStack.class);
        ItemStack sourceStackClone = mock(ItemStack.class);
        when(sourceStack.clone()).thenReturn(sourceStackClone);
        when(sourceInventory.getStorageContents()).thenReturn(new ItemStack[] {sourceStack});
        when(sourceInventory.getExtraContents()).thenReturn(new ItemStack[0]);
        when(sourceInventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(targetInventory.getArmorContents()).thenReturn(new ItemStack[4]);

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Player> enforceCap = mock(java.util.function.Consumer.class);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, target),
                () -> true,
                enforceCap,
                new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.requestSharedInventorySync(source);
        }

        verify(targetInventory).setStorageContents(any(ItemStack[].class));
        verify(targetInventory).setExtraContents(any(ItemStack[].class));
        verify(target).updateInventory();
        verify(enforceCap).accept(source);
        verify(enforceCap).accept(target);
    }

    @Test
    void snapshotAndDetectWearables_updatesSnapshotAndConsumesNewWearables() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(otherId);
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory sourceInventory = mock(PlayerInventory.class);
        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(source.getInventory()).thenReturn(sourceInventory);
        when(other.getInventory()).thenReturn(otherInventory);

        ItemStack helmet = mock(ItemStack.class);
        when(helmet.getType()).thenReturn(Material.DIAMOND_HELMET);
        ItemStack boots = mock(ItemStack.class);
        when(boots.getType()).thenReturn(Material.LEATHER_BOOTS);
        when(sourceInventory.getArmorContents()).thenReturn(new ItemStack[4]);

        ItemStack otherHelmet = mock(ItemStack.class);
        when(otherHelmet.getType()).thenReturn(Material.DIAMOND_HELMET);
        when(otherHelmet.getAmount()).thenReturn(1);
        when(otherInventory.getItem(0)).thenReturn(otherHelmet);
        ItemStack otherBoots = mock(ItemStack.class);
        when(otherBoots.getType()).thenReturn(Material.LEATHER_BOOTS);
        when(otherBoots.getAmount()).thenReturn(1);
        when(otherInventory.getItemInOffHand()).thenReturn(otherBoots);
        when(otherInventory.getArmorContents()).thenReturn(new ItemStack[4]);

        Map<UUID, Map<Material, Integer>> snapshots = new HashMap<>();
        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                player -> {},
                snapshots);

        service.snapshotEquippedWearablesForParticipants();
        assertEquals(0, snapshots.get(sourceId).size());

        when(sourceInventory.getArmorContents()).thenReturn(new ItemStack[] {helmet, boots, null, null});
        service.detectNewlyEquippedWearables(source);

        verify(otherInventory).setItem(0, null);
        verify(otherInventory).setItemInOffHand(null);
        verify(other, org.mockito.Mockito.times(2)).updateInventory();
        assertEquals(2, snapshots.get(sourceId).size());
    }

    @Test
    void consumeItemAndWearable_applyToOtherParticipantsOnly() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory sourceInventory = mock(PlayerInventory.class);
        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(source.getInventory()).thenReturn(sourceInventory);
        when(other.getInventory()).thenReturn(otherInventory);

        ItemStack bread = mock(ItemStack.class);
        when(bread.getType()).thenReturn(Material.BREAD);
        when(bread.getAmount()).thenReturn(2);

        ItemStack helmetOffhand = mock(ItemStack.class);
        when(helmetOffhand.getType()).thenReturn(Material.DIAMOND_HELMET);
        when(helmetOffhand.getAmount()).thenReturn(1);

        when(otherInventory.getSize()).thenReturn(36);
        when(otherInventory.getItem(0)).thenReturn(bread);
        when(otherInventory.getItemInOffHand()).thenReturn(helmetOffhand);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                player -> {},
                new HashMap<>());

        service.consumeItemFromOtherParticipants(Material.BREAD, sourceId);
        service.consumeWearableFromOtherParticipants(Material.DIAMOND_HELMET, sourceId);

        verify(otherInventory).setItem(eq(0), any(ItemStack.class));
        verify(otherInventory).setItemInOffHand(null);
        verify(other, org.mockito.Mockito.times(2)).updateInventory();
        verify(sourceInventory, never()).setItem(any(Integer.class), any());
    }

    @Test
    void requestSharedInventorySync_onlyQueuesOnceUntilDeferredTaskRuns() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        when(source.getUniqueId()).thenReturn(UUID.randomUUID());

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source),
                () -> false,
                player -> {},
                new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            service.requestSharedInventorySync(source);
            service.requestSharedInventorySync(source);
        }

        verify(scheduler, org.mockito.Mockito.times(1)).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void requestSharedInventorySync_deferredTaskSkipsWhenNotRunning() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        PlayerInventory sourceInventory = mock(PlayerInventory.class);

        when(source.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getInventory()).thenReturn(sourceInventory);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> false,
                () -> List.of(source),
                () -> false,
                player -> {},
                new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.requestSharedInventorySync(source);
        }

        verify(sourceInventory, never()).getStorageContents();
    }

    @Test
    void requestWearableEquipSync_respectsEligibilityGuards() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);

        SharedInventorySyncService inactive = new SharedInventorySyncService(
                plugin, player -> false, () -> true, () -> true, List::of, () -> false, player -> {}, new HashMap<>());

        SharedInventorySyncService active = new SharedInventorySyncService(
                plugin, player -> true, () -> true, () -> true, List::of, () -> false, player -> {}, new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            inactive.requestWearableEquipSync(source);
            active.requestWearableEquipSync(source);
        }

        verify(scheduler, org.mockito.Mockito.times(1)).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void syncSharedInventoryFromFirstParticipant_noActiveParticipants_isNoop() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player spectator = mock(Player.class);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(spectator),
                () -> false,
                player -> {},
                new HashMap<>());

        service.syncSharedInventoryFromFirstParticipant();

        verify(spectator, never()).getInventory();
    }

    @Test
    void requestSharedInventorySync_keepsSpareWearableWhenSourceHasOneEquipped() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        Player target = mock(Player.class);

        when(source.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(target.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory sourceInventory = mock(PlayerInventory.class);
        PlayerInventory targetInventory = mock(PlayerInventory.class);
        when(source.getInventory()).thenReturn(sourceInventory);
        when(target.getInventory()).thenReturn(targetInventory);

        ItemStack spareChestplate = new ItemStack(Material.IRON_CHESTPLATE, 1);
        ItemStack equippedChestplate = new ItemStack(Material.IRON_CHESTPLATE, 1);
        when(sourceInventory.getStorageContents()).thenReturn(new ItemStack[] {spareChestplate});
        when(sourceInventory.getExtraContents()).thenReturn(new ItemStack[0]);
        when(sourceInventory.getArmorContents()).thenReturn(new ItemStack[] {null, equippedChestplate, null, null});
        when(targetInventory.getArmorContents()).thenReturn(new ItemStack[4]);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, target),
                () -> false,
                player -> {},
                new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.requestSharedInventorySync(source);
        }

        ArgumentCaptor<ItemStack[]> storageCaptor = ArgumentCaptor.forClass(ItemStack[].class);
        verify(targetInventory).setStorageContents(storageCaptor.capture());
        ItemStack[] syncedStorage = storageCaptor.getValue();
        assertEquals(1, syncedStorage.length);
        assertNotNull(syncedStorage[0]);
        assertEquals(Material.IRON_CHESTPLATE, syncedStorage[0].getType());
        assertEquals(1, syncedStorage[0].getAmount());
    }

    @Test
    void wearableSnapshotRemovalAndClear_mutateBackingMap() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        Map<UUID, Map<Material, Integer>> snapshots = new HashMap<>();
        snapshots.put(one, Map.of(Material.DIAMOND_HELMET, 1));
        snapshots.put(two, Map.of(Material.LEATHER_BOOTS, 1));

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin, player -> true, () -> true, () -> true, List::of, () -> false, player -> {}, snapshots);

        service.removePlayerWearableSnapshot(one);
        assertFalse(snapshots.containsKey(one));

        service.clearWearableSnapshots();
        assertTrue(snapshots.isEmpty());
    }

    @Test
    void detectNewlyEquippedWearables_noopsWhenChallengeInactiveOrSharedDisabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        SharedInventorySyncService inactive = new SharedInventorySyncService(
                plugin,
                p -> false,
                () -> true,
                () -> true,
                () -> List.of(player),
                () -> false,
                p -> {},
                new HashMap<>());

        SharedInventorySyncService disabled = new SharedInventorySyncService(
                plugin,
                p -> true,
                () -> false,
                () -> true,
                () -> List.of(player),
                () -> false,
                p -> {},
                new HashMap<>());

        inactive.detectNewlyEquippedWearables(player);
        disabled.detectNewlyEquippedWearables(player);
    }

    @Test
    void requestSharedInventorySync_fallsBackToActiveParticipantWhenPendingSourceIsSpectator() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player spectatorSource = mock(Player.class);
        Player active = mock(Player.class);
        UUID sourceId = UUID.randomUUID();

        when(spectatorSource.getUniqueId()).thenReturn(sourceId);
        when(spectatorSource.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(active.getUniqueId()).thenReturn(UUID.randomUUID());
        when(active.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory activeInventory = mock(PlayerInventory.class);
        when(active.getInventory()).thenReturn(activeInventory);
        when(activeInventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(activeInventory.getExtraContents()).thenReturn(new ItemStack[0]);
        when(activeInventory.getArmorContents()).thenReturn(new ItemStack[4]);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                p -> true,
                () -> true,
                () -> true,
                () -> List.of(spectatorSource, active),
                () -> false,
                p -> {},
                new HashMap<>());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.requestSharedInventorySync(spectatorSource);
        }

        verify(activeInventory).getStorageContents();
    }

    @Test
    void consumeWearableFromOtherParticipants_removesFromMainInventoryWhenPresent() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(other.getInventory()).thenReturn(otherInventory);
        when(otherInventory.getItemInOffHand()).thenReturn(null);

        ItemStack boots = mock(ItemStack.class);
        when(boots.getType()).thenReturn(Material.LEATHER_BOOTS);
        when(boots.getAmount()).thenReturn(1);
        when(otherInventory.getItem(0)).thenReturn(boots);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                p -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                p -> {},
                new HashMap<>());

        service.consumeWearableFromOtherParticipants(Material.LEATHER_BOOTS, sourceId);

        verify(otherInventory).setItem(0, null);
        verify(other).updateInventory();
    }

    @Test
    void consumeWearableFromOtherParticipants_decrementsOffhandStackWhenAmountGreaterThanOne() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(other.getInventory()).thenReturn(otherInventory);
        when(otherInventory.getItem(0)).thenReturn(null);

        ItemStack offhandHelmet = mock(ItemStack.class);
        when(offhandHelmet.getType()).thenReturn(Material.DIAMOND_HELMET);
        when(offhandHelmet.getAmount()).thenReturn(3);
        when(otherInventory.getItemInOffHand()).thenReturn(offhandHelmet);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                p -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                p -> {},
                new HashMap<>());

        service.consumeWearableFromOtherParticipants(Material.DIAMOND_HELMET, sourceId);

        verify(offhandHelmet).setAmount(2);
        verify(otherInventory).setItemInOffHand(offhandHelmet);
        verify(other).updateInventory();
    }

    @Test
    void consumeWearableFromOtherParticipants_removesFromFallbackNonArmorSlot() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(other.getInventory()).thenReturn(otherInventory);
        when(source.getInventory()).thenReturn(mock(PlayerInventory.class));

        ItemStack helmet = mock(ItemStack.class);
        when(helmet.getType()).thenReturn(Material.DIAMOND_HELMET);
        when(helmet.getAmount()).thenReturn(1);

        when(otherInventory.getItemInOffHand()).thenReturn(null);
        when(otherInventory.getSize()).thenReturn(42);
        when(otherInventory.getItem(41)).thenReturn(helmet);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                player -> {},
                new HashMap<>());

        service.consumeWearableFromOtherParticipants(Material.DIAMOND_HELMET, sourceId);

        verify(otherInventory).setItem(41, null);
        verify(other).updateInventory();
    }

    @Test
    void consumeWearableFromOtherParticipants_removesFromCursorWhenHeld() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        UUID sourceId = UUID.randomUUID();

        Player source = mock(Player.class);
        Player other = mock(Player.class);
        when(source.getUniqueId()).thenReturn(sourceId);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(other.getGameMode()).thenReturn(GameMode.SURVIVAL);

        PlayerInventory otherInventory = mock(PlayerInventory.class);
        when(other.getInventory()).thenReturn(otherInventory);
        when(otherInventory.getItemInOffHand()).thenReturn(null);
        when(otherInventory.getSize()).thenReturn(41);
        when(otherInventory.getItem(any(Integer.class))).thenReturn(null);

        InventoryView otherOpenInventory = mock(InventoryView.class);
        when(other.getOpenInventory()).thenReturn(otherOpenInventory);

        ItemStack cursorChestplate = mock(ItemStack.class);
        when(cursorChestplate.getType()).thenReturn(Material.IRON_CHESTPLATE);
        when(cursorChestplate.getAmount()).thenReturn(1);
        when(otherOpenInventory.getCursor()).thenReturn(cursorChestplate);

        SharedInventorySyncService service = new SharedInventorySyncService(
                plugin,
                player -> true,
                () -> true,
                () -> true,
                () -> List.of(source, other),
                () -> false,
                player -> {},
                new HashMap<>());

        service.consumeWearableFromOtherParticipants(Material.IRON_CHESTPLATE, sourceId);

        verify(otherOpenInventory).setCursor(null);
        verify(other).updateInventory();
    }

    private static int invokeRemoveMaterialFromItemArray(
            SharedInventorySyncService service, ItemStack[] contents, Material material, int amount) throws Exception {
        Method method = SharedInventorySyncService.class.getDeclaredMethod(
                "removeMaterialFromItemArray", ItemStack[].class, Material.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(service, contents, material, amount);
    }
}
