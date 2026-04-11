package dev.deepcore.challenge.inventory;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Manages degrading-inventory slot locking and locked-barrier protections. */
public final class DegradingInventoryService {
    private static final int TOTAL_HOTBAR_AND_STORAGE_SLOTS = 36;
    private static final int HOTBAR_SLOTS = 9;
    private static final int MAIN_INVENTORY_START_SLOT = 9;
    private static final int MAIN_INVENTORY_SLOTS = 27;
    private static final int[] INVENTORY_LOCK_ORDER = buildInventoryLockOrder();

    private final NamespacedKey lockedInventoryBarrierKey;
    private final BooleanSupplier isDegradingInventoryEnabled;
    private final Predicate<Player> isChallengeActive;

    private int allowedInventorySlots = TOTAL_HOTBAR_AND_STORAGE_SLOTS;

    /**
     * Creates a degrading inventory service.
     *
     * @param lockedInventoryBarrierKey   metadata key used to mark managed lock
     *                                    barriers
     * @param isDegradingInventoryEnabled supplier reporting whether degrading
     *                                    inventory is enabled
     * @param isChallengeActive           predicate reporting whether a player is in
     *                                    active challenge state
     */
    public DegradingInventoryService(
            NamespacedKey lockedInventoryBarrierKey,
            BooleanSupplier isDegradingInventoryEnabled,
            Predicate<Player> isChallengeActive) {
        this.lockedInventoryBarrierKey = lockedInventoryBarrierKey;
        this.isDegradingInventoryEnabled = isDegradingInventoryEnabled;
        this.isChallengeActive = isChallengeActive;
    }

    /** Resets allowed slots to a fully unlocked inventory. */
    public void resetAllowedInventorySlots() {
        allowedInventorySlots = TOTAL_HOTBAR_AND_STORAGE_SLOTS;
    }

    /**
     * Returns the currently allowed hotbar and storage slot count.
     *
     * @return number of unlocked hotbar plus storage slots
     */
    public int getAllowedInventorySlots() {
        return allowedInventorySlots;
    }

    /**
     * Reduces allowed inventory slots down to, but not below, the provided floor.
     *
     * @param minSlots minimum allowed slot count to preserve
     * @return true when the allowed slot count was reduced
     */
    public boolean reduceAllowedInventorySlots(int minSlots) {
        if (allowedInventorySlots <= minSlots) {
            return false;
        }
        allowedInventorySlots--;
        return true;
    }

    /**
     * Enforces locked-slot barriers and clears stale barrier placement.
     *
     * @param player player whose inventory should be normalized to current slot cap
     */
    public void enforceInventorySlotCap(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (isLockedSlot(slot)) {
                if (!isLockedBarrier(existing)) {
                    placeLockedBarrierAndDropDisplacedItem(player, inventory, slot, existing);
                }
                continue;
            }

            if (isLockedBarrier(existing)) {
                inventory.setItem(slot, null);
            }
        }

        normalizeLockedBarrierPositions(player, inventory);
        player.updateInventory();
    }

    /**
     * Removes locked barrier markers from all hotbar and storage slots.
     *
     * @param player player whose inventory lock markers should be cleared
     */
    public void clearLockedBarrierSlots(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            if (isLockedBarrier(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        player.updateInventory();
    }

    /**
     * Returns whether a click should be canceled due to locked-slot protections.
     *
     * @param event  inventory click event being evaluated
     * @param player player whose inventory interaction is being checked
     * @return true when the click should be canceled
     */
    public boolean shouldCancelLockedSlotClick(InventoryClickEvent event, Player player) {
        if (!isBarrierProtectionActive(player)) {
            return false;
        }

        if (isBarrier(event.getCurrentItem()) || isBarrier(event.getCursor())) {
            return true;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && isBarrier(player.getInventory().getItem(hotbarButton))) {
            return true;
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND
                && isBarrier(player.getInventory().getItemInOffHand())) {
            return true;
        }

        int clickedPlayerSlot = resolveClickedPlayerSlot(event, player);
        if (clickedPlayerSlot >= 0 && isLockedSlot(clickedPlayerSlot)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.BARRIER) {
                return true;
            }
        }

        if (isLockedBarrier(event.getCurrentItem()) || isLockedBarrier(event.getCursor())) {
            return true;
        }

        if (clickedPlayerSlot >= 0 && isLockedSlot(clickedPlayerSlot)) {
            return true;
        }

        if (event.getClick() == ClickType.NUMBER_KEY && isLockedSlot(event.getHotbarButton())) {
            return true;
        }

        if (event.getHotbarButton() >= 0 && isLockedSlot(event.getHotbarButton())) {
            return true;
        }

        return event.getClick() == ClickType.SWAP_OFFHAND;
    }

    /**
     * Returns whether barrier protection rules are active for the player.
     *
     * @param player player to evaluate
     * @return true when barrier protections must be enforced for that player
     */
    public boolean isBarrierProtectionActive(Player player) {
        if (!isDegradingInventoryEnabled.getAsBoolean()) {
            return false;
        }

        return isChallengeActive.test(player) || hasLockedBarrierInInventory(player);
    }

    /**
     * Returns whether a raw inventory-view slot maps to a locked player slot.
     *
     * @param view    inventory view containing the raw slot
     * @param player  player whose inventory lock mapping should be used
     * @param rawSlot raw slot index in the view
     * @return true when the raw slot resolves to a currently locked player slot
     */
    public boolean isLockedRawSlot(InventoryView view, Player player, int rawSlot) {
        if (rawSlot < 0) {
            return false;
        }

        Inventory clickedInventory = view.getInventory(rawSlot);
        if (clickedInventory == null || clickedInventory != player.getInventory()) {
            return false;
        }

        int playerSlot = view.convertSlot(rawSlot);
        return isLockedSlot(playerSlot);
    }

    /**
     * Returns whether the given item stack is any barrier item.
     *
     * @param stack item stack to inspect
     * @return true when the stack exists and is a barrier
     */
    public boolean isBarrier(ItemStack stack) {
        return stack != null && stack.getType() == Material.BARRIER;
    }

    private int resolveClickedPlayerSlot(InventoryClickEvent event, Player player) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory != player.getInventory()) {
            return -1;
        }

        return event.getSlot();
    }

    private boolean hasLockedBarrierInInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            if (isLockedBarrier(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createLockedBarrierItem() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Locked Slot");
            meta.setLore(List.of(ChatColor.GRAY + "This slot is locked by degrading inventory."));
            meta.getPersistentDataContainer().set(lockedInventoryBarrierKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            barrier.setItemMeta(meta);
        }
        return barrier;
    }

    private boolean isLockedBarrier(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BARRIER) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(lockedInventoryBarrierKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isLockedSlot(int slot) {
        if (slot < 0 || slot >= TOTAL_HOTBAR_AND_STORAGE_SLOTS) {
            return false;
        }

        int lockedSlots = TOTAL_HOTBAR_AND_STORAGE_SLOTS - allowedInventorySlots;
        if (lockedSlots <= 0) {
            return false;
        }

        for (int index = 0; index < lockedSlots; index++) {
            if (INVENTORY_LOCK_ORDER[index] == slot) {
                return true;
            }
        }

        return false;
    }

    private void normalizeLockedBarrierPositions(Player player, PlayerInventory inventory) {
        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!isLockedSlot(slot) && isLockedBarrier(existing)) {
                inventory.setItem(slot, null);
            }
        }

        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (isLockedSlot(slot) && !isLockedBarrier(existing)) {
                placeLockedBarrierAndDropDisplacedItem(player, inventory, slot, existing);
            }
        }
    }

    private void placeLockedBarrierAndDropDisplacedItem(
            Player player, PlayerInventory inventory, int slot, ItemStack existing) {
        if (existing != null && !existing.getType().isAir()) {
            player.getWorld().dropItemNaturally(player.getLocation(), existing.clone());
        }
        inventory.setItem(slot, createLockedBarrierItem());
    }

    private static int[] buildInventoryLockOrder() {
        int[] order = new int[TOTAL_HOTBAR_AND_STORAGE_SLOTS];
        int index = 0;

        for (int slot = MAIN_INVENTORY_START_SLOT; slot < MAIN_INVENTORY_START_SLOT + MAIN_INVENTORY_SLOTS; slot++) {
            order[index++] = slot;
        }
        for (int slot = HOTBAR_SLOTS - 1; slot >= 0; slot--) {
            order[index++] = slot;
        }

        return order;
    }
}
