package dev.deepcore.challenge.events;

import dev.deepcore.challenge.session.PrepGuiCoordinatorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Routes prep-book and prep-GUI interaction events to the session coordinator.
 */
public final class PrepFlowListener implements Listener {
    private final PrepGuiCoordinatorService prepGuiCoordinatorService;

    /**
     * Creates a prep flow listener.
     *
     * @param prepGuiCoordinatorService prep GUI coordination service
     */
    public PrepFlowListener(PrepGuiCoordinatorService prepGuiCoordinatorService) {
        this.prepGuiCoordinatorService = prepGuiCoordinatorService;
    }

    @EventHandler
    public void onPrepBookUse(PlayerInteractEvent event) {
        prepGuiCoordinatorService.handlePrepBookUse(event);
    }

    @EventHandler
    public void onPrepGuiClick(InventoryClickEvent event) {
        prepGuiCoordinatorService.handleProtectedPrepBookClick(event);
        if (event.isCancelled()) {
            return;
        }
        prepGuiCoordinatorService.handlePrepGuiClick(event);
    }

    @EventHandler
    public void onPrepGuiDrag(InventoryDragEvent event) {
        prepGuiCoordinatorService.handleProtectedPrepBookDrag(event);
        if (event.isCancelled()) {
            return;
        }
        prepGuiCoordinatorService.handlePrepGuiDrag(event);
    }

    @EventHandler
    public void onPrepBookDrop(PlayerDropItemEvent event) {
        prepGuiCoordinatorService.handleProtectedPrepBookDrop(event);
    }

    @EventHandler
    public void onPrepBookSwapHands(PlayerSwapHandItemsEvent event) {
        prepGuiCoordinatorService.handleProtectedPrepBookSwapHands(event);
    }

    @EventHandler
    public void onPrepGuiClose(InventoryCloseEvent event) {
        prepGuiCoordinatorService.handlePrepGuiClose(event);
    }
}
