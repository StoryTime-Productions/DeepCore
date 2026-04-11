package dev.deepcore.challenge.portal;

import dev.deepcore.challenge.session.PrepAreaService;
import dev.deepcore.challenge.session.SessionState;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Coordinates portal redirection and movement clamping behavior for session
 * phases.
 */
public final class PortalTransitCoordinatorService {
    private final SessionState sessionState;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final WorldClassificationService worldClassificationService;
    private final PortalRoutingService portalRoutingService;
    private final PrepAreaService prepAreaService;

    /**
     * Creates a portal transit coordinator service.
     *
     * @param sessionState               mutable session phase state
     * @param worldResetManagerSupplier  supplier for world reset manager
     * @param worldClassificationService world classification helper
     * @param portalRoutingService       linked-world portal routing service
     * @param prepAreaService            prep area clamp service
     */
    public PortalTransitCoordinatorService(
            SessionState sessionState,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            WorldClassificationService worldClassificationService,
            PortalRoutingService portalRoutingService,
            PrepAreaService prepAreaService) {
        this.sessionState = sessionState;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.worldClassificationService = worldClassificationService;
        this.portalRoutingService = portalRoutingService;
        this.prepAreaService = prepAreaService;
    }

    /**
     * Handles Paper portal-ready events and redirects eligible portal targets.
     *
     * @param event Paper entity portal-ready event
     */
    public void handleEntityPortalReady(io.papermc.paper.event.entity.EntityPortalReadyEvent event) {
        if (!sessionState.is(SessionState.Phase.RUNNING) || worldResetManagerSupplier.get() == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
            return;
        }

        org.bukkit.PortalType portalType = event.getPortalType();
        if (portalType != org.bukkit.PortalType.NETHER && portalType != org.bukkit.PortalType.ENDER) {
            return;
        }

        World fromWorld = player.getWorld();
        if (fromWorld == null || worldClassificationService.isLobbyOrLimboWorld(fromWorld)) {
            return;
        }

        World targetWorld = portalType == org.bukkit.PortalType.NETHER
                ? portalRoutingService.resolveLinkedPortalWorld(fromWorld)
                : portalRoutingService.resolveLinkedEndWorld(fromWorld);
        if (targetWorld == null) {
            return;
        }

        event.setTargetWorld(targetWorld);
    }

    /**
     * Handles player portal teleports and applies linked-world routing rules.
     *
     * @param event player portal event to route
     */
    public void handlePlayerPortal(PlayerPortalEvent event) {
        if (!sessionState.is(SessionState.Phase.RUNNING) || worldResetManagerSupplier.get() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
            return;
        }

        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }

        World fromWorld = player.getWorld();
        if (fromWorld == null || worldClassificationService.isLobbyOrLimboWorld(fromWorld)) {
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            World targetWorld = portalRoutingService.resolveLinkedPortalWorld(fromWorld);
            if (targetWorld == null) {
                return;
            }

            Location from = event.getFrom();
            double scale = fromWorld.getEnvironment() == World.Environment.NETHER ? 8.0D : (1.0D / 8.0D);
            Location target = portalRoutingService.buildLinkedPortalTarget(from, targetWorld, scale);

            if (target == null) {
                return;
            }

            event.setCanCreatePortal(true);
            event.setTo(target);
            return;
        }

        World targetWorld = portalRoutingService.resolveLinkedEndWorld(fromWorld);
        if (targetWorld == null) {
            return;
        }

        event.setCanCreatePortal(false);
        event.setTo(portalRoutingService.resolveEndPortalTarget(fromWorld, targetWorld));
    }

    /**
     * Handles player movement for prep-area clamping and end-portal transit checks.
     *
     * @param event player move event to inspect and potentially adjust
     */
    public void handlePlayerMove(PlayerMoveEvent event) {
        if (sessionState.is(SessionState.Phase.RUNNING)) {
            tryHandleEndPortalTransit(event.getPlayer());
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (worldClassificationService.isLobbyOrLimboWorld(to.getWorld())) {
            return;
        }

        if (!prepAreaService.isWithinPrepArea(to)) {
            event.setTo(prepAreaService.clampToPrepArea(to));
        }
    }

    private void tryHandleEndPortalTransit(Player player) {
        portalRoutingService.tryHandleEndPortalTransit(
                player, sessionState.is(SessionState.Phase.RUNNING), worldClassificationService::isLobbyOrLimboWorld);
    }
}
