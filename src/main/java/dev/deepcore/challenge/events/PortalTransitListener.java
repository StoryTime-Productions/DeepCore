package dev.deepcore.challenge.events;

import dev.deepcore.challenge.portal.PortalTransitCoordinatorService;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/** Routes portal-related events to portal transit coordinator services. */
public final class PortalTransitListener implements Listener {
    private final PortalTransitCoordinatorService portalTransitCoordinatorService;

    /**
     * Creates a portal transit listener.
     *
     * @param portalTransitCoordinatorService portal transit coordinator service
     */
    public PortalTransitListener(PortalTransitCoordinatorService portalTransitCoordinatorService) {
        this.portalTransitCoordinatorService = portalTransitCoordinatorService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortalReady(EntityPortalReadyEvent event) {
        portalTransitCoordinatorService.handleEntityPortalReady(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        portalTransitCoordinatorService.handlePlayerPortal(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        portalTransitCoordinatorService.handlePlayerMove(event);
    }
}
