package dev.deepcore.challenge.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.session.PrepAreaService;
import dev.deepcore.challenge.session.SessionState;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

class PortalTransitCoordinatorServiceTest {

    @Test
    void handleEntityPortalReady_setsTargetWorldForEligibleNetherPortal() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        Supplier<WorldResetManager> worldReset = () -> mock(WorldResetManager.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);

        PortalTransitCoordinatorService service =
                new PortalTransitCoordinatorService(state, worldReset, worldClassifier, routing, prep);

        EntityPortalReadyEvent event = mock(EntityPortalReadyEvent.class);
        Player player = mock(Player.class);
        World fromWorld = mock(World.class);
        World targetWorld = mock(World.class);

        when(event.getEntity()).thenReturn(player);
        when(event.getPortalType()).thenReturn(PortalType.NETHER);
        when(player.getWorld()).thenReturn(fromWorld);
        when(worldClassifier.isLobbyOrLimboWorld(fromWorld)).thenReturn(false);
        when(routing.resolveLinkedPortalWorld(fromWorld)).thenReturn(targetWorld);

        service.handleEntityPortalReady(event);

        verify(event).setTargetWorld(targetWorld);
    }

    @Test
    void handleEntityPortalReady_setsTargetWorldForEligibleEndPortal() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        Supplier<WorldResetManager> worldReset = () -> mock(WorldResetManager.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);

        PortalTransitCoordinatorService service =
                new PortalTransitCoordinatorService(state, worldReset, worldClassifier, routing, prep);

        EntityPortalReadyEvent event = mock(EntityPortalReadyEvent.class);
        Player player = mock(Player.class);
        World fromWorld = mock(World.class);
        World targetWorld = mock(World.class);

        when(event.getEntity()).thenReturn(player);
        when(event.getPortalType()).thenReturn(PortalType.ENDER);
        when(player.getWorld()).thenReturn(fromWorld);
        when(worldClassifier.isLobbyOrLimboWorld(fromWorld)).thenReturn(false);
        when(routing.resolveLinkedEndWorld(fromWorld)).thenReturn(targetWorld);

        service.handleEntityPortalReady(event);

        verify(event).setTargetWorld(targetWorld);
    }

    @Test
    void handleEntityPortalReady_returnsForGuardConditions() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);

        PortalTransitCoordinatorService service =
                new PortalTransitCoordinatorService(state, () -> null, worldClassifier, routing, prep);

        EntityPortalReadyEvent event = mock(EntityPortalReadyEvent.class);
        service.handleEntityPortalReady(event);

        verify(event, never()).setTargetWorld(any());
    }

    @Test
    void handlePlayerPortal_routesNetherPortalWithScaledTarget() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        Player player = mock(Player.class);
        World fromWorld = mock(World.class);
        World targetWorld = mock(World.class);
        Location from = mock(Location.class);
        Location target = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        when(player.getWorld()).thenReturn(fromWorld);
        when(fromWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(worldClassifier.isLobbyOrLimboWorld(fromWorld)).thenReturn(false);
        when(routing.resolveLinkedPortalWorld(fromWorld)).thenReturn(targetWorld);
        when(event.getFrom()).thenReturn(from);
        when(routing.buildLinkedPortalTarget(from, targetWorld, 1.0D / 8.0D)).thenReturn(target);

        service.handlePlayerPortal(event);

        verify(event).setCanCreatePortal(true);
        verify(event).setTo(target);
    }

    @Test
    void handlePlayerPortal_routesEndPortalWithoutPortalCreation() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        Player player = mock(Player.class);
        World fromWorld = mock(World.class);
        World targetWorld = mock(World.class);
        Location target = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.END_PORTAL);
        when(player.getWorld()).thenReturn(fromWorld);
        when(worldClassifier.isLobbyOrLimboWorld(fromWorld)).thenReturn(false);
        when(routing.resolveLinkedEndWorld(fromWorld)).thenReturn(targetWorld);
        when(routing.resolveEndPortalTarget(fromWorld, targetWorld)).thenReturn(target);

        service.handlePlayerPortal(event);

        verify(event).setCanCreatePortal(false);
        verify(event).setTo(target);
    }

    @Test
    void handlePlayerMove_runningPhaseDelegatesEndPortalTransit() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Player player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);

        service.handlePlayerMove(event);

        verify(routing).tryHandleEndPortalTransit(eq(player), eq(true), any());
        verify(event, never()).setTo(any(Location.class));
    }

    @Test
    void handlePlayerMove_nonRunningDoesNotClampOutsidePrepArea() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Location to = mock(Location.class);
        Location clamped = mock(Location.class);
        World world = mock(World.class);

        when(event.getTo()).thenReturn(to);
        when(to.getWorld()).thenReturn(world);
        when(worldClassifier.isLobbyOrLimboWorld(world)).thenReturn(false);
        when(prep.isWithinPrepArea(to)).thenReturn(false);
        when(prep.clampToPrepArea(to)).thenReturn(clamped);

        service.handlePlayerMove(event);

        verify(event, never()).setTo(any(Location.class));
    }

    @Test
    void handlePlayerMove_nonRunning_returnsWhenTargetLocationMissing() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getTo()).thenReturn(null);

        service.handlePlayerMove(event);

        verify(event, never()).setTo(any(Location.class));
    }

    @Test
    void handlePlayerMove_nonRunning_returnsInLobbyWorld() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PortalRoutingService routing = mock(PortalRoutingService.class);
        PrepAreaService prep = mock(PrepAreaService.class);
        PortalTransitCoordinatorService service = new PortalTransitCoordinatorService(
                state, () -> mock(WorldResetManager.class), worldClassifier, routing, prep);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Location to = mock(Location.class);
        World world = mock(World.class);
        when(event.getTo()).thenReturn(to);
        when(to.getWorld()).thenReturn(world);
        when(worldClassifier.isLobbyOrLimboWorld(world)).thenReturn(true);

        service.handlePlayerMove(event);

        verify(event, never()).setTo(any(Location.class));
    }
}
