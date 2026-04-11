package dev.deepcore.challenge.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Synchronizes participant inventories when shared-inventory challenge mode is
 * enabled.
 */
public final class SharedInventorySyncService {
    private static final int TOTAL_HOTBAR_AND_STORAGE_SLOTS = 36;
    private static final int HELMET_SLOT_INDEX = 39;
    private static final int CHESTPLATE_SLOT_INDEX = 38;
    private static final int LEGGINGS_SLOT_INDEX = 37;
    private static final int BOOTS_SLOT_INDEX = 36;
    private static final int OFFHAND_SLOT_INDEX = 40;

    private final JavaPlugin plugin;
    private final Predicate<Player> isChallengeActive;
    private final BooleanSupplier isSharedInventoryEnabled;
    private final Supplier<Boolean> isRunningPhase;
    private final Supplier<List<Player>> onlineParticipants;
    private final BooleanSupplier isDegradingInventoryEnabled;
    private final Consumer<Player> enforceInventorySlotCap;
    private final Map<UUID, Map<Material, Integer>> equippedWearableCounts;

    private boolean syncingInventory;
    private boolean sharedInventorySyncQueued;
    private UUID pendingSharedInventorySourceId;

    /**
     * Creates a shared inventory synchronization service.
     *
     * @param plugin                      plugin instance used for scheduling sync
     *                                    tasks
     * @param isChallengeActive           predicate indicating whether a player is
     *                                    in active challenge state
     * @param isSharedInventoryEnabled    supplier indicating whether shared
     *                                    inventory is enabled
     * @param isRunningPhase              supplier indicating whether challenge is
     *                                    currently running
     * @param onlineParticipants          supplier for online challenge participants
     * @param isDegradingInventoryEnabled supplier indicating whether degrading
     *                                    inventory is enabled
     * @param enforceInventorySlotCap     action that enforces inventory slot caps
     *                                    for a player
     * @param equippedWearableCounts      mutable wearable snapshot cache keyed by
     *                                    player UUID
     */
    public SharedInventorySyncService(
            JavaPlugin plugin,
            Predicate<Player> isChallengeActive,
            BooleanSupplier isSharedInventoryEnabled,
            Supplier<Boolean> isRunningPhase,
            Supplier<List<Player>> onlineParticipants,
            BooleanSupplier isDegradingInventoryEnabled,
            Consumer<Player> enforceInventorySlotCap,
            Map<UUID, Map<Material, Integer>> equippedWearableCounts) {
        this.plugin = plugin;
        this.isChallengeActive = isChallengeActive;
        this.isSharedInventoryEnabled = isSharedInventoryEnabled;
        this.isRunningPhase = isRunningPhase;
        this.onlineParticipants = onlineParticipants;
        this.isDegradingInventoryEnabled = isDegradingInventoryEnabled;
        this.enforceInventorySlotCap = enforceInventorySlotCap;
        this.equippedWearableCounts = equippedWearableCounts;
    }

    /**
     * Requests a deferred shared-inventory sync using the provided source player.
     *
     * @param source preferred source player for synchronization
     */
    public void requestSharedInventorySync(Player source) {
        if (!isChallengeActive.test(source) || !isSharedInventoryEnabled.getAsBoolean()) {
            return;
        }

        pendingSharedInventorySourceId = source.getUniqueId();
        scheduleSharedInventoryDrain();
    }

    /**
     * Immediately synchronizes shared inventory from the first available
     * participant.
     */
    public void syncSharedInventoryFromFirstParticipant() {
        Player source = resolveSharedInventorySyncSource();
        pendingSharedInventorySourceId = null;
        if (source == null) {
            return;
        }

        syncInventoryFrom(source);
    }

    /**
     * Immediately synchronizes shared inventory from the provided source player.
     *
     * @param source preferred source player for synchronization
     */
    public void syncSharedInventoryFromSourceNow(Player source) {
        if (source == null || !isChallengeActive.test(source) || !isSharedInventoryEnabled.getAsBoolean()) {
            return;
        }

        if (!isRunningPhase.get()) {
            return;
        }

        if (syncingInventory) {
            pendingSharedInventorySourceId = source.getUniqueId();
            scheduleSharedInventoryDrain();
            return;
        }

        pendingSharedInventorySourceId = null;
        syncInventoryFrom(source);
    }

    private void scheduleSharedInventoryDrain() {
        if (sharedInventorySyncQueued) {
            return;
        }

        sharedInventorySyncQueued = true;
        Bukkit.getScheduler().runTask(plugin, this::drainSharedInventoryQueue);
    }

    private void drainSharedInventoryQueue() {
        if (!sharedInventorySyncQueued) {
            return;
        }

        if (!isSharedInventoryEnabled.getAsBoolean() || !isRunningPhase.get()) {
            sharedInventorySyncQueued = false;
            pendingSharedInventorySourceId = null;
            return;
        }

        if (syncingInventory) {
            Bukkit.getScheduler().runTaskLater(plugin, this::drainSharedInventoryQueue, 1L);
            return;
        }

        sharedInventorySyncQueued = false;
        Player syncSource = resolveSharedInventorySyncSource();
        pendingSharedInventorySourceId = null;
        if (syncSource == null) {
            return;
        }

        syncInventoryFrom(syncSource);
    }

    /**
     * Removes one matching item from all participants except the source player.
     *
     * @param material       material to consume from participant inventories
     * @param sourcePlayerId player UUID that initiated the consume action
     */
    public void consumeItemFromOtherParticipants(Material material, UUID sourcePlayerId) {
        for (Player participant : getOnlineActiveSharedInventoryParticipants()) {
            if (participant.getUniqueId().equals(sourcePlayerId)) {
                continue;
            }

            removeOneItem(participant.getInventory(), material);
            participant.updateInventory();
        }
    }

    /**
     * Removes one wearable item from others after a participant equips it.
     *
     * @param material       wearable material that was equipped
     * @param sourcePlayerId player UUID that equipped the wearable
     */
    public void consumeWearableFromOtherParticipants(Material material, UUID sourcePlayerId) {
        consumeWearableFromParticipants(material, sourcePlayerId, getOnlineActiveSharedInventoryParticipants());
    }

    /**
     * Removes one wearable item from a provided participant snapshot except the
     * source player.
     *
     * @param material       wearable material that was equipped
     * @param sourcePlayerId player UUID that equipped the wearable
     * @param participants   participant snapshot to apply removal against
     */
    public void consumeWearableFromParticipants(Material material, UUID sourcePlayerId, List<Player> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }

        for (Player participant : participants) {
            if (participant == null || participant.getUniqueId().equals(sourcePlayerId)) {
                continue;
            }

            PlayerInventory inventory = participant.getInventory();
            if (removeOneFromMainInventory(inventory, material)
                    || removeOneFromOffhand(inventory, material)
                    || removeOneFromArmorSlots(inventory, material)
                    || removeOneFromFallbackNonArmorSlots(inventory, material)
                    || removeOneFromCursor(participant, material)) {
                participant.updateInventory();
            }
        }
    }

    private boolean removeOneFromArmorSlots(PlayerInventory inventory, Material material) {
        ItemStack helmet = inventory.getHelmet();
        if (helmet != null && helmet.getType() == material) {
            return decrementArmorSlot(inventory, helmet, EquipmentSlotRef.HELMET);
        }

        ItemStack chestplate = inventory.getChestplate();
        if (chestplate != null && chestplate.getType() == material) {
            return decrementArmorSlot(inventory, chestplate, EquipmentSlotRef.CHESTPLATE);
        }

        ItemStack leggings = inventory.getLeggings();
        if (leggings != null && leggings.getType() == material) {
            return decrementArmorSlot(inventory, leggings, EquipmentSlotRef.LEGGINGS);
        }

        ItemStack boots = inventory.getBoots();
        if (boots != null && boots.getType() == material) {
            return decrementArmorSlot(inventory, boots, EquipmentSlotRef.BOOTS);
        }

        return false;
    }

    private boolean decrementArmorSlot(PlayerInventory inventory, ItemStack stack, EquipmentSlotRef slotRef) {
        int amount = stack.getAmount();
        if (amount <= 1) {
            switch (slotRef) {
                case HELMET -> inventory.setHelmet(null);
                case CHESTPLATE -> inventory.setChestplate(null);
                case LEGGINGS -> inventory.setLeggings(null);
                case BOOTS -> inventory.setBoots(null);
            }
        } else {
            stack.setAmount(amount - 1);
            switch (slotRef) {
                case HELMET -> inventory.setHelmet(stack);
                case CHESTPLATE -> inventory.setChestplate(stack);
                case LEGGINGS -> inventory.setLeggings(stack);
                case BOOTS -> inventory.setBoots(stack);
            }
        }
        return true;
    }

    private enum EquipmentSlotRef {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }

    /**
     * Schedules a wearable-equip synchronization pass for the source player.
     *
     * @param source player whose newly equipped wearables should be detected
     */
    public void requestWearableEquipSync(Player source) {
        if (!isChallengeActive.test(source) || !isSharedInventoryEnabled.getAsBoolean()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> detectNewlyEquippedWearables(source));
    }

    /**
     * Detects newly equipped wearables and mirrors consumption across participants.
     *
     * @param player player whose equipped wearable set should be diffed
     */
    public void detectNewlyEquippedWearables(Player player) {
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.getAsBoolean()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Map<Material, Integer> previous = equippedWearableCounts.getOrDefault(playerId, Map.of());
        Map<Material, Integer> current = getEquippedWearableCounts(player);

        for (Map.Entry<Material, Integer> entry : current.entrySet()) {
            Material material = entry.getKey();
            int delta = entry.getValue() - previous.getOrDefault(material, 0);
            for (int i = 0; i < delta; i++) {
                consumeWearableFromOtherParticipants(material, playerId);
            }
        }

        equippedWearableCounts.put(playerId, current);
    }

    /** Captures wearable snapshots for all current online participants. */
    public void snapshotEquippedWearablesForParticipants() {
        equippedWearableCounts.clear();
        for (Player participant : onlineParticipants.get()) {
            equippedWearableCounts.put(participant.getUniqueId(), getEquippedWearableCounts(participant));
        }
    }

    /**
     * Captures the wearable snapshot for a single player.
     *
     * @param player player whose wearable snapshot should be stored
     */
    public void capturePlayerWearableSnapshot(Player player) {
        equippedWearableCounts.put(player.getUniqueId(), getEquippedWearableCounts(player));
    }

    /**
     * Removes the cached wearable snapshot associated with a player id.
     *
     * @param playerId player UUID whose snapshot should be removed
     */
    public void removePlayerWearableSnapshot(UUID playerId) {
        equippedWearableCounts.remove(playerId);
    }

    /** Clears all cached wearable snapshots. */
    public void clearWearableSnapshots() {
        equippedWearableCounts.clear();
    }

    private Player resolveSharedInventorySyncSource() {
        List<Player> syncParticipants = getOnlineActiveSharedInventoryParticipants();
        if (syncParticipants.isEmpty()) {
            return null;
        }

        if (pendingSharedInventorySourceId != null) {
            for (Player participant : syncParticipants) {
                if (participant.getUniqueId().equals(pendingSharedInventorySourceId)) {
                    return participant;
                }
            }
        }

        return syncParticipants.get(0);
    }

    private List<Player> getOnlineActiveSharedInventoryParticipants() {
        List<Player> activeParticipants = new ArrayList<>();
        for (Player participant : onlineParticipants.get()) {
            if (participant.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            activeParticipants.add(participant);
        }

        activeParticipants.sort((first, second) -> first.getUniqueId().compareTo(second.getUniqueId()));
        return activeParticipants;
    }

    private void syncInventoryFrom(Player source) {
        List<Player> syncParticipants = getOnlineActiveSharedInventoryParticipants();
        if (syncParticipants.isEmpty()) {
            return;
        }

        UUID sourceId = source.getUniqueId();
        if (!syncParticipants.stream()
                .anyMatch(participant -> participant.getUniqueId().equals(sourceId))) {
            source = syncParticipants.get(0);
        }

        syncingInventory = true;
        try {
            PlayerInventory sourceInventory = source.getInventory();
            ItemStack[] storage = cloneContents(sourceInventory.getStorageContents());
            ItemStack[] extra = cloneContents(sourceInventory.getExtraContents());

            for (Player target : syncParticipants) {
                PlayerInventory targetInventory = target.getInventory();
                targetInventory.setStorageContents(cloneContents(storage));
                targetInventory.setExtraContents(cloneContents(extra));

                if (isDegradingInventoryEnabled.getAsBoolean()) {
                    enforceInventorySlotCap.accept(target);
                }

                target.updateInventory();
            }
        } finally {
            syncingInventory = false;
        }
    }

    private void reconcileSharedInventoryWithEquippedWearables(
            ItemStack[] storage, ItemStack[] extra, List<Player> syncParticipants) {
        Map<Material, Integer> equippedCounts = new HashMap<>();
        for (Player participant : syncParticipants) {
            for (ItemStack armorItem : participant.getInventory().getArmorContents()) {
                if (armorItem == null || armorItem.getType().isAir()) {
                    continue;
                }

                Material material = armorItem.getType();
                equippedCounts.put(material, equippedCounts.getOrDefault(material, 0) + 1);
            }
        }

        for (Map.Entry<Material, Integer> entry : equippedCounts.entrySet()) {
            Material material = entry.getKey();
            int remaining = entry.getValue();

            remaining -= removeMaterialFromItemArray(storage, material, remaining);
            if (remaining > 0) {
                removeMaterialFromItemArray(extra, material, remaining);
            }
        }
    }

    private int removeMaterialFromItemArray(ItemStack[] contents, Material material, int amount) {
        if (contents == null || amount <= 0) {
            return 0;
        }

        int removed = 0;
        for (int slot = 0; slot < contents.length && removed < amount; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int stackAmount = stack.getAmount();
            int toRemove = Math.min(stackAmount, amount - removed);
            int newAmount = stackAmount - toRemove;
            if (newAmount <= 0) {
                contents[slot] = null;
            } else {
                stack.setAmount(newAmount);
                contents[slot] = stack;
            }
            removed += toRemove;
        }

        return removed;
    }

    private void removeOneItem(PlayerInventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int amount = stack.getAmount();
            if (amount <= 1) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(amount - 1);
                inventory.setItem(slot, stack);
            }
            return;
        }
    }

    private boolean removeOneFromMainInventory(PlayerInventory inventory, Material material) {
        for (int slot = 0; slot < TOTAL_HOTBAR_AND_STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int amount = stack.getAmount();
            if (amount <= 1) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(amount - 1);
                inventory.setItem(slot, stack);
            }
            return true;
        }

        return false;
    }

    private boolean removeOneFromOffhand(PlayerInventory inventory, Material material) {
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand == null || offhand.getType() != material) {
            return false;
        }

        int amount = offhand.getAmount();
        if (amount <= 1) {
            inventory.setItemInOffHand(null);
        } else {
            offhand.setAmount(amount - 1);
            inventory.setItemInOffHand(offhand);
        }
        return true;
    }

    private boolean removeOneFromFallbackNonArmorSlots(PlayerInventory inventory, Material material) {
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (isArmorOrOffhandSlot(slot)) {
                continue;
            }

            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int amount = stack.getAmount();
            if (amount <= 1) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(amount - 1);
                inventory.setItem(slot, stack);
            }
            return true;
        }

        return false;
    }

    private boolean isArmorOrOffhandSlot(int slot) {
        return slot == HELMET_SLOT_INDEX
                || slot == CHESTPLATE_SLOT_INDEX
                || slot == LEGGINGS_SLOT_INDEX
                || slot == BOOTS_SLOT_INDEX
                || slot == OFFHAND_SLOT_INDEX;
    }

    private boolean removeOneFromCursor(Player participant, Material material) {
        InventoryView openInventory = participant.getOpenInventory();
        if (openInventory == null) {
            return false;
        }

        ItemStack cursor = openInventory.getCursor();
        if (cursor == null || cursor.getType() != material) {
            return false;
        }

        int amount = cursor.getAmount();
        if (amount <= 1) {
            openInventory.setCursor(null);
        } else {
            cursor.setAmount(amount - 1);
            openInventory.setCursor(cursor);
        }
        return true;
    }

    private Map<Material, Integer> getEquippedWearableCounts(Player player) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack armorItem : player.getInventory().getArmorContents()) {
            if (armorItem == null || armorItem.getType().isAir()) {
                continue;
            }

            Material material = armorItem.getType();
            counts.put(material, counts.getOrDefault(material, 0) + 1);
        }
        return counts;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) {
            return new ItemStack[0];
        }

        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            copy[i] = stack == null ? null : stack.clone();
        }
        return copy;
    }
}
