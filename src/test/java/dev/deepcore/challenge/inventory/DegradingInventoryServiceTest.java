package dev.deepcore.challenge.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class DegradingInventoryServiceTest {

    private DegradingInventoryService createService(BooleanSupplier enabled, Predicate<Player> active) {
        return new DegradingInventoryService(mock(NamespacedKey.class), enabled, active);
    }

    @Test
    void reduceAllowedInventorySlots_respectsMinimumFloor() {
        DegradingInventoryService service = createService(() -> true, player -> true);

        assertTrue(service.reduceAllowedInventorySlots(0));

        for (int i = 0; i < 100; i++) {
            service.reduceAllowedInventorySlots(1);
        }

        assertFalse(service.reduceAllowedInventorySlots(1));
        assertTrue(service.getAllowedInventorySlots() >= 1);
    }

    @Test
    void resetAllowedInventorySlots_restoresFullyUnlockedCount() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        service.reduceAllowedInventorySlots(0);
        service.reduceAllowedInventorySlots(0);

        service.resetAllowedInventorySlots();

        org.junit.jupiter.api.Assertions.assertEquals(36, service.getAllowedInventorySlots());
    }

    @Test
    void isBarrierProtectionActive_falseWhenFeatureDisabled() {
        DegradingInventoryService service = createService(() -> false, player -> true);
        Player player = mock(Player.class);

        assertFalse(service.isBarrierProtectionActive(player));
    }

    @Test
    void isBarrierProtectionActive_trueWhenChallengeActive() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        Player player = mock(Player.class);

        assertTrue(service.isBarrierProtectionActive(player));
    }

    @Test
    void shouldCancelLockedSlotClick_falseWhenProtectionInactive() {
        DegradingInventoryService service = createService(() -> false, player -> false);
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        assertFalse(service.shouldCancelLockedSlotClick(event, player));
    }

    @Test
    void shouldCancelLockedSlotClick_trueWhenCurrentOrCursorBarrier() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        ItemStack barrier = mock(ItemStack.class);
        when(barrier.getType()).thenReturn(Material.BARRIER);
        when(event.getCurrentItem()).thenReturn(barrier);

        assertTrue(service.shouldCancelLockedSlotClick(event, player));
    }

    @Test
    void shouldCancelLockedSlotClick_trueForSwapOffhandFallback() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        when(event.getCurrentItem()).thenReturn(null);
        when(event.getCursor()).thenReturn(null);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getClick()).thenReturn(ClickType.SWAP_OFFHAND);

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInOffHand()).thenReturn(null);

        assertTrue(service.shouldCancelLockedSlotClick(event, player));
    }

    @Test
    void shouldCancelLockedSlotClick_trueWhenClickedLockedPlayerSlot() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        service.reduceAllowedInventorySlots(35);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(9);
        when(event.getCurrentItem()).thenReturn(mock(ItemStack.class));
        when(event.getCursor()).thenReturn(null);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getHotbarButton()).thenReturn(-1);

        assertTrue(service.shouldCancelLockedSlotClick(event, player));
    }

    @Test
    void shouldCancelLockedSlotClick_trueWhenNumberKeyTargetsLockedSlot() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        service.reduceAllowedInventorySlots(35);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(9)).thenReturn(null);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getCurrentItem()).thenReturn(null);
        when(event.getCursor()).thenReturn(null);
        when(event.getClick()).thenReturn(ClickType.NUMBER_KEY);
        when(event.getHotbarButton()).thenReturn(9);

        assertTrue(service.shouldCancelLockedSlotClick(event, player));
    }

    @Test
    void isLockedRawSlot_handlesInvalidAndValidMappings() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        service.reduceAllowedInventorySlots(35);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        InventoryView view = mock(InventoryView.class);

        assertFalse(service.isLockedRawSlot(view, player, -1));

        when(view.getInventory(5)).thenReturn(null);
        assertFalse(service.isLockedRawSlot(view, player, 5));

        when(view.getInventory(6)).thenReturn(inventory);
        when(view.convertSlot(6)).thenReturn(9);
        assertTrue(service.isLockedRawSlot(view, player, 6));
    }

    @Test
    void isBarrier_recognizesBarrierOnly() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        ItemStack barrier = mock(ItemStack.class);
        ItemStack stone = mock(ItemStack.class);
        when(barrier.getType()).thenReturn(Material.BARRIER);
        when(stone.getType()).thenReturn(Material.STONE);

        assertTrue(service.isBarrier(barrier));
        assertFalse(service.isBarrier(stone));
        assertFalse(service.isBarrier(null));
    }

    @Test
    void isBarrierProtectionActive_trueWhenInventoryContainsLockedBarrier() {
        DegradingInventoryService service = createService(() -> true, player -> false);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.BARRIER);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenReturn((byte) 1);
        when(inventory.getItem(0)).thenReturn(stack);
        for (int i = 1; i < 36; i++) {
            when(inventory.getItem(i)).thenReturn(null);
        }

        assertTrue(service.isBarrierProtectionActive(player));
    }

    @Test
    void clearLockedBarrierSlots_removesOnlyTaggedBarriers() {
        DegradingInventoryService service = createService(() -> true, player -> true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack lockedBarrier = mock(ItemStack.class);
        ItemMeta lockedMeta = mock(ItemMeta.class);
        PersistentDataContainer lockedPdc = mock(PersistentDataContainer.class);
        when(lockedBarrier.getType()).thenReturn(Material.BARRIER);
        when(lockedBarrier.getItemMeta()).thenReturn(lockedMeta);
        when(lockedMeta.getPersistentDataContainer()).thenReturn(lockedPdc);
        when(lockedPdc.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenReturn((byte) 1);

        ItemStack plainBarrier = mock(ItemStack.class);
        ItemMeta plainMeta = mock(ItemMeta.class);
        PersistentDataContainer plainPdc = mock(PersistentDataContainer.class);
        when(plainBarrier.getType()).thenReturn(Material.BARRIER);
        when(plainBarrier.getItemMeta()).thenReturn(plainMeta);
        when(plainMeta.getPersistentDataContainer()).thenReturn(plainPdc);
        when(plainPdc.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenReturn(null);

        when(inventory.getItem(0)).thenReturn(lockedBarrier);
        when(inventory.getItem(1)).thenReturn(plainBarrier);

        service.clearLockedBarrierSlots(player);

        org.mockito.Mockito.verify(inventory).setItem(0, null);
        org.mockito.Mockito.verify(inventory, org.mockito.Mockito.never()).setItem(1, null);
        org.mockito.Mockito.verify(player).updateInventory();
    }

    @Test
    void enforceInventorySlotCap_clearsStaleLockedBarrierInUnlockedSlot() {
        DegradingInventoryService service = createService(() -> true, player -> true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack lockedBarrier = mock(ItemStack.class);
        ItemMeta lockedMeta = mock(ItemMeta.class);
        PersistentDataContainer lockedPdc = mock(PersistentDataContainer.class);
        when(lockedBarrier.getType()).thenReturn(Material.BARRIER);
        when(lockedBarrier.getItemMeta()).thenReturn(lockedMeta);
        when(lockedMeta.getPersistentDataContainer()).thenReturn(lockedPdc);
        when(lockedPdc.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenReturn((byte) 1);
        when(inventory.getItem(5)).thenReturn(lockedBarrier);

        service.enforceInventorySlotCap(player);

        org.mockito.Mockito.verify(inventory, org.mockito.Mockito.atLeastOnce()).setItem(5, null);
        org.mockito.Mockito.verify(player).updateInventory();
    }

    @Test
    void enforceInventorySlotCap_placesBarrierAndDropsExistingItemForLockedSlot() {
        DegradingInventoryService service = createService(() -> true, player -> true);
        service.reduceAllowedInventorySlots(35);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        World world = mock(World.class);
        org.bukkit.Location location = mock(org.bukkit.Location.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);

        ItemStack existing = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(existing.getType()).thenReturn(Material.STONE);
        when(existing.clone()).thenReturn(clone);
        when(inventory.getItem(9)).thenReturn(existing);

        service.enforceInventorySlotCap(player);

        org.mockito.Mockito.verify(world, org.mockito.Mockito.atLeastOnce()).dropItemNaturally(location, clone);
        org.mockito.Mockito.verify(inventory, org.mockito.Mockito.atLeastOnce())
                .setItem(org.mockito.ArgumentMatchers.eq(9), org.mockito.ArgumentMatchers.any(ItemStack.class));
    }
}
