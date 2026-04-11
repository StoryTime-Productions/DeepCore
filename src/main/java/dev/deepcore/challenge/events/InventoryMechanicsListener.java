package dev.deepcore.challenge.events;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.deepcore.challenge.inventory.InventoryMechanicsCoordinatorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

/**
 * Routes inventory-related mechanics events to the session coordinator.
 */
public final class InventoryMechanicsListener implements Listener {
    private final InventoryMechanicsCoordinatorService inventoryMechanicsCoordinatorService;

    /**
     * Creates an inventory mechanics listener.
     *
     * @param inventoryMechanicsCoordinatorService inventory mechanics coordinator
     */
    public InventoryMechanicsListener(InventoryMechanicsCoordinatorService inventoryMechanicsCoordinatorService) {
        this.inventoryMechanicsCoordinatorService = inventoryMechanicsCoordinatorService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        inventoryMechanicsCoordinatorService.handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        inventoryMechanicsCoordinatorService.handleInventoryCreative(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        inventoryMechanicsCoordinatorService.handleInventoryDrag(event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerDropItem(event);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerSwapHandItems(event);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerItemConsume(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        inventoryMechanicsCoordinatorService.handleCraftItem(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerItemHeld(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDurabilityDamage(PlayerItemDamageEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerItemDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerBucketEmpty(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerBucketFill(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerArmorChanged(PlayerArmorChangeEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerArmorChanged(event);
    }

    @EventHandler
    public void onPotentialWearableUse(PlayerInteractEvent event) {
        inventoryMechanicsCoordinatorService.handlePotentialWearableUse(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpecialInventoryMutatingInteract(PlayerInteractEvent event) {
        inventoryMechanicsCoordinatorService.handleSpecialInventoryMutatingInteract(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpecialInventoryMutatingEntityInteract(PlayerInteractEntityEvent event) {
        inventoryMechanicsCoordinatorService.handleSpecialInventoryMutatingEntityInteract(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerEditBook(PlayerEditBookEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerEditBook(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerTakeLecternBook(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketEntity(PlayerBucketEntityEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerBucketEntity(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLodestoneBreak(BlockBreakEvent event) {
        inventoryMechanicsCoordinatorService.handleLodestoneBreak(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        inventoryMechanicsCoordinatorService.handleProjectileLaunch(event);
    }

    @EventHandler
    public void onEnderEyeUse(PlayerInteractEvent event) {
        inventoryMechanicsCoordinatorService.handleEnderEyeUse(event);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        inventoryMechanicsCoordinatorService.handleEntityPickupItem(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        inventoryMechanicsCoordinatorService.handleEntityResurrect(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        inventoryMechanicsCoordinatorService.handlePlayerPickupArrow(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        inventoryMechanicsCoordinatorService.handleBlockPlace(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        inventoryMechanicsCoordinatorService.handleBlockMultiPlace(event);
    }
}
