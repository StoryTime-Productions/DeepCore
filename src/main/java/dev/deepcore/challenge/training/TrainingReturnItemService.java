package dev.deepcore.challenge.training;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages the return-to-lobby hotbar item for training world players.
 */
public final class TrainingReturnItemService implements Listener {
    static final int RETURN_ITEM_SLOT = 8; // 9th slot (0-indexed)
    private static final Material RETURN_ITEM_MATERIAL = Material.FEATHER;
    private static final String RETURN_ITEM_DISPLAY_NAME = ChatColor.AQUA + "Return to Lobby";
    private static final String RETURN_ITEM_MARKER = "training_return_item";

    private final JavaPlugin plugin;
    private final TrainingManager trainingManager;
    private final NamespacedKey returnItemKey;
    private final Set<UUID> playersInTraining = new HashSet<>();
    private BukkitTask restoreTask;

    /**
     * Creates a training return item service.
     *
     * @param plugin          plugin instance for scheduling and events
     * @param trainingManager training manager to call return-to-lobby action
     * @param returnItemKey   namespaced key for marking return items
     */
    public TrainingReturnItemService(JavaPlugin plugin, TrainingManager trainingManager, NamespacedKey returnItemKey) {
        this.plugin = plugin;
        this.trainingManager = trainingManager;
        this.returnItemKey = returnItemKey;
    }

    /**
     * Registers event listeners and starts the restore task.
     */
    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startRestoreTask();
    }

    /**
     * Stops the restore task and cleans up.
     */
    public void shutdown() {
        if (restoreTask != null) {
            restoreTask.cancel();
            restoreTask = null;
        }
        playersInTraining.clear();
    }

    /**
     * Marks a player as being in training and provides them with the return item.
     *
     * @param player player entering training
     */
    public void onPlayerEnterTraining(Player player) {
        playersInTraining.add(player.getUniqueId());
        giveReturnItem(player);
    }

    /**
     * Unmarked a player as being in training and removes the return item.
     *
     * @param player player leaving training
     */
    public void onPlayerLeaveTraining(Player player) {
        playersInTraining.remove(player.getUniqueId());
        removeReturnItem(player);
    }

    /**
     * Handles right-click interactions with the return item.
     *
     * @param event interact event to check
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onReturnItemClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !isReturnItem(item)) {
            return;
        }

        event.setCancelled(true);
        trainingManager.leaveTraining(event.getPlayer());
    }

    /**
     * Prevents dropping the return item.
     *
     * @param event drop event to check
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReturnItemDrop(PlayerDropItemEvent event) {
        if (!isReturnItem(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().updateInventory();
    }

    /**
     * Prevents moving the return item in inventory.
     *
     * @param event inventory click event to check
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReturnItemInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        int hotbarButton = event.getHotbarButton();

        boolean isCurrentItemReturn = isReturnItem(currentItem);
        boolean isCursorReturn = isReturnItem(cursor);
        boolean isHotbarReturn =
                hotbarButton >= 0 && isReturnItem(player.getInventory().getItem(hotbarButton));

        if (isCurrentItemReturn || isCursorReturn || isHotbarReturn) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /**
     * Prevents dragging the return item in inventory.
     *
     * @param event inventory drag event to check
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReturnItemInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (isReturnItem(event.getOldCursor())) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        for (ItemStack item : event.getNewItems().values()) {
            if (isReturnItem(item)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
    }

    private void giveReturnItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existing = inventory.getItem(RETURN_ITEM_SLOT);

        if (isReturnItem(existing)) {
            return;
        }

        ItemStack returnItem = createReturnItem();
        if (existing != null && !existing.getType().isAir()) {
            player.getWorld().dropItemNaturally(player.getLocation(), existing.clone());
        }

        inventory.setItem(RETURN_ITEM_SLOT, returnItem);
        player.updateInventory();
    }

    private void removeReturnItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItem(RETURN_ITEM_SLOT);

        if (isReturnItem(item)) {
            inventory.setItem(RETURN_ITEM_SLOT, null);
            player.updateInventory();
        }
    }

    private ItemStack createReturnItem() {
        ItemStack item = new ItemStack(RETURN_ITEM_MATERIAL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(RETURN_ITEM_DISPLAY_NAME);
            meta.setLore(List.of(ChatColor.GRAY + "Right-click to return to lobby"));
            meta.getPersistentDataContainer().set(returnItemKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }

    boolean isReturnItem(ItemStack item) {
        if (item == null || item.getType() != RETURN_ITEM_MATERIAL) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(returnItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void startRestoreTask() {
        if (restoreTask != null) {
            restoreTask.cancel();
        }

        restoreTask = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            for (UUID playerId : new HashSet<>(playersInTraining)) {
                                Player player = plugin.getServer().getPlayer(playerId);
                                if (player == null) {
                                    playersInTraining.remove(playerId);
                                    continue;
                                }

                                if (trainingManager.isInActiveAttempt(player)) {
                                    continue;
                                }
                                ItemStack item = player.getInventory().getItem(RETURN_ITEM_SLOT);
                                if (!isReturnItem(item)) {
                                    giveReturnItem(player);
                                }
                            }
                        },
                        0L,
                        20L);
    }
}
