package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SessionOperationServiceTest {

    @Test
    void delegatesToAvailableServices() {
        SessionUiCoordinatorService sessionUi = mock(SessionUiCoordinatorService.class);
        DegradingInventoryTickerService inventoryTicker = mock(DegradingInventoryTickerService.class);
        DegradingInventoryService degradingInventory = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedInventory = mock(SharedInventorySyncService.class);
        RunHealthCoordinatorService healthCoordinator = mock(RunHealthCoordinatorService.class);
        PrepGuiCoordinatorService prepGui = mock(PrepGuiCoordinatorService.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        PausedRunStateService pausedState = mock(PausedRunStateService.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        Player player = mock(Player.class);

        SessionOperationService service = new SessionOperationService(
                () -> sessionUi,
                () -> inventoryTicker,
                () -> degradingInventory,
                () -> sharedInventory,
                () -> healthCoordinator,
                () -> prepGui,
                () -> actionBar,
                () -> pausedState,
                () -> worldResetManager);

        service.startActionBarTask();
        service.startLobbySidebarTask();
        service.clearLobbySidebar(player);
        service.startDegradingInventoryTask(true);
        service.enforceInventorySlotCap(player);
        service.clearLockedBarrierSlots(player);
        service.syncSharedInventoryFromFirstParticipant();
        service.snapshotEquippedWearablesForParticipants();
        service.syncSharedHealthFromFirstParticipant();
        service.syncSharedHungerFromMostFilledParticipant();
        service.applyInitialHalfHeartIfEnabled();
        service.applyInitialHalfHeart(player);
        service.restoreDefaultMaxHealth(player);
        service.refreshOpenPrepGuis();
        service.clearPausedSnapshots();

        verify(sessionUi).startActionBarTask();
        verify(sessionUi).startLobbySidebarTask();
        verify(sessionUi).clearLobbySidebar(player);
        verify(inventoryTicker).start(true);
        verify(degradingInventory).enforceInventorySlotCap(player);
        verify(degradingInventory).clearLockedBarrierSlots(player);
        verify(sharedInventory).syncSharedInventoryFromFirstParticipant();
        verify(sharedInventory).snapshotEquippedWearablesForParticipants();
        verify(healthCoordinator).syncSharedHealthFromFirstParticipant();
        verify(healthCoordinator).syncSharedHungerFromMostFilledParticipant();
        verify(healthCoordinator).applyInitialHalfHeartIfEnabled();
        verify(healthCoordinator).applyInitialHalfHeart(player);
        verify(healthCoordinator).restoreDefaultMaxHealth(player);
        verify(prepGui).refreshOpenPrepGuis();
        verify(pausedState).clearSnapshots();
        verify(actionBar, never()).clearActionBar(any());
    }

    @Test
    void stashRunStateForLobby_resolvesConfiguredLimboSpawn() {
        PausedRunStateService pausedState = mock(PausedRunStateService.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        Player player = mock(Player.class);
        Location limboSpawn = mock(Location.class);

        when(worldResetManager.getConfiguredLimboSpawn()).thenReturn(limboSpawn);
        doAnswer(invocation -> {
                    Supplier<Location> supplier = invocation.getArgument(2);
                    assertSame(limboSpawn, supplier.get());
                    return null;
                })
                .when(pausedState)
                .stashRunStateForLobby(any(Player.class), anyBoolean(), any());

        SessionOperationService service = new SessionOperationService(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> pausedState,
                () -> worldResetManager);

        service.stashRunStateForLobby(player, true);

        verify(pausedState).stashRunStateForLobby(any(Player.class), anyBoolean(), any());
        verify(worldResetManager).getConfiguredLimboSpawn();
    }

    @Test
    void stashRunStateForLobby_handlesMissingPausedStateAndWorldResetManager() {
        Player player = mock(Player.class);

        SessionOperationService noPausedStateService = new SessionOperationService(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null);
        noPausedStateService.stashRunStateForLobby(player, false);

        PausedRunStateService pausedState = mock(PausedRunStateService.class);
        doAnswer(invocation -> {
                    Supplier<Location> supplier = invocation.getArgument(2);
                    assertNull(supplier.get());
                    return null;
                })
                .when(pausedState)
                .stashRunStateForLobby(any(Player.class), anyBoolean(), any());

        SessionOperationService noWorldResetService = new SessionOperationService(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> pausedState,
                () -> null);
        noWorldResetService.stashRunStateForLobby(player, false);

        verify(pausedState).stashRunStateForLobby(any(Player.class), anyBoolean(), any());
    }

    @Test
    void doesNothingWhenServiceSuppliersReturnNull() {
        Player player = mock(Player.class);

        SessionOperationService service = new SessionOperationService(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null);

        service.startActionBarTask();
        service.startLobbySidebarTask();
        service.clearLobbySidebar(player);
        service.startDegradingInventoryTask(false);
        service.enforceInventorySlotCap(player);
        service.clearLockedBarrierSlots(player);
        service.syncSharedInventoryFromFirstParticipant();
        service.snapshotEquippedWearablesForParticipants();
        service.syncSharedHealthFromFirstParticipant();
        service.syncSharedHungerFromMostFilledParticipant();
        service.applyInitialHalfHeartIfEnabled();
        service.applyInitialHalfHeart(player);
        service.restoreDefaultMaxHealth(player);
        service.refreshOpenPrepGuis();
        service.clearActionBar();
        service.clearPausedSnapshots();
    }
}
