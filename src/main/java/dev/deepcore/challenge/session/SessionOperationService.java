package dev.deepcore.challenge.session;

import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Provides a single operations facade over session-level service actions.
 */
public final class SessionOperationService {
    private final Supplier<SessionUiCoordinatorService> sessionUiCoordinatorSupplier;
    private final Supplier<DegradingInventoryTickerService> degradingInventoryTickerSupplier;
    private final Supplier<DegradingInventoryService> degradingInventoryServiceSupplier;
    private final Supplier<SharedInventorySyncService> sharedInventorySyncServiceSupplier;
    private final Supplier<RunHealthCoordinatorService> runHealthCoordinatorServiceSupplier;
    private final Supplier<PrepGuiCoordinatorService> prepGuiCoordinatorServiceSupplier;
    private final Supplier<ActionBarTickerService> actionBarTickerServiceSupplier;
    private final Supplier<PausedRunStateService> pausedRunStateServiceSupplier;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;

    /**
     * Creates a session operation facade service.
     *
     * @param sessionUiCoordinatorSupplier        supplier for session UI
     *                                            coordinator service
     * @param degradingInventoryTickerSupplier    supplier for degrading inventory
     *                                            ticker service
     * @param degradingInventoryServiceSupplier   supplier for degrading inventory
     *                                            service
     * @param sharedInventorySyncServiceSupplier  supplier for shared inventory sync
     *                                            service
     * @param runHealthCoordinatorServiceSupplier supplier for run health
     *                                            coordinator service
     * @param prepGuiCoordinatorServiceSupplier   supplier for prep GUI coordinator
     *                                            service
     * @param actionBarTickerServiceSupplier      supplier for action-bar ticker
     *                                            service
     * @param pausedRunStateServiceSupplier       supplier for paused-run state
     *                                            service
     * @param worldResetManagerSupplier           supplier for world reset manager
     */
    public SessionOperationService(
            Supplier<SessionUiCoordinatorService> sessionUiCoordinatorSupplier,
            Supplier<DegradingInventoryTickerService> degradingInventoryTickerSupplier,
            Supplier<DegradingInventoryService> degradingInventoryServiceSupplier,
            Supplier<SharedInventorySyncService> sharedInventorySyncServiceSupplier,
            Supplier<RunHealthCoordinatorService> runHealthCoordinatorServiceSupplier,
            Supplier<PrepGuiCoordinatorService> prepGuiCoordinatorServiceSupplier,
            Supplier<ActionBarTickerService> actionBarTickerServiceSupplier,
            Supplier<PausedRunStateService> pausedRunStateServiceSupplier,
            Supplier<WorldResetManager> worldResetManagerSupplier) {
        this.sessionUiCoordinatorSupplier = sessionUiCoordinatorSupplier;
        this.degradingInventoryTickerSupplier = degradingInventoryTickerSupplier;
        this.degradingInventoryServiceSupplier = degradingInventoryServiceSupplier;
        this.sharedInventorySyncServiceSupplier = sharedInventorySyncServiceSupplier;
        this.runHealthCoordinatorServiceSupplier = runHealthCoordinatorServiceSupplier;
        this.prepGuiCoordinatorServiceSupplier = prepGuiCoordinatorServiceSupplier;
        this.actionBarTickerServiceSupplier = actionBarTickerServiceSupplier;
        this.pausedRunStateServiceSupplier = pausedRunStateServiceSupplier;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
    }

    /** Starts the challenge action-bar task if the UI coordinator is available. */
    public void startActionBarTask() {
        SessionUiCoordinatorService sessionUiCoordinatorService = sessionUiCoordinatorSupplier.get();
        if (sessionUiCoordinatorService != null) {
            sessionUiCoordinatorService.startActionBarTask();
        }
    }

    /** Starts the lobby sidebar task if the UI coordinator is available. */
    public void startLobbySidebarTask() {
        SessionUiCoordinatorService sessionUiCoordinatorService = sessionUiCoordinatorSupplier.get();
        if (sessionUiCoordinatorService != null) {
            sessionUiCoordinatorService.startLobbySidebarTask();
        }
    }

    /**
     * Clears the lobby sidebar for the given player when supported.
     *
     * @param player player whose lobby sidebar should be cleared
     */
    public void clearLobbySidebar(Player player) {
        SessionUiCoordinatorService sessionUiCoordinatorService = sessionUiCoordinatorSupplier.get();
        if (sessionUiCoordinatorService != null) {
            sessionUiCoordinatorService.clearLobbySidebar(player);
        }
    }

    /**
     * Starts the degrading-inventory ticker and optionally resets slot state.
     *
     * @param resetSlots true to reset slot allowance before ticker start
     */
    public void startDegradingInventoryTask(boolean resetSlots) {
        DegradingInventoryTickerService degradingInventoryTickerService = degradingInventoryTickerSupplier.get();
        if (degradingInventoryTickerService != null) {
            degradingInventoryTickerService.start(resetSlots);
        }
    }

    /**
     * Enforces the active inventory slot cap for a player when enabled.
     *
     * @param player player whose inventory slot cap should be enforced
     */
    public void enforceInventorySlotCap(Player player) {
        DegradingInventoryService degradingInventoryService = degradingInventoryServiceSupplier.get();
        if (degradingInventoryService != null) {
            degradingInventoryService.enforceInventorySlotCap(player);
        }
    }

    /**
     * Clears locked barrier slots for a player when degrading inventory is active.
     *
     * @param player player whose locked barriers should be cleared
     */
    public void clearLockedBarrierSlots(Player player) {
        DegradingInventoryService degradingInventoryService = degradingInventoryServiceSupplier.get();
        if (degradingInventoryService != null) {
            degradingInventoryService.clearLockedBarrierSlots(player);
        }
    }

    /** Synchronizes shared inventory from the first online participant snapshot. */
    public void syncSharedInventoryFromFirstParticipant() {
        SharedInventorySyncService sharedInventorySyncService = sharedInventorySyncServiceSupplier.get();
        if (sharedInventorySyncService != null) {
            sharedInventorySyncService.syncSharedInventoryFromFirstParticipant();
        }
    }

    /** Captures wearable snapshots for all active challenge participants. */
    public void snapshotEquippedWearablesForParticipants() {
        SharedInventorySyncService sharedInventorySyncService = sharedInventorySyncServiceSupplier.get();
        if (sharedInventorySyncService != null) {
            sharedInventorySyncService.snapshotEquippedWearablesForParticipants();
        }
    }

    /** Synchronizes shared health from the first online participant state. */
    public void syncSharedHealthFromFirstParticipant() {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.syncSharedHealthFromFirstParticipant();
        }
    }

    /** Synchronizes shared hunger from the most-satiated online participant. */
    public void syncSharedHungerFromMostFilledParticipant() {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.syncSharedHungerFromMostFilledParticipant();
        }
    }

    /** Applies initial half-heart challenge settings for online participants. */
    public void applyInitialHalfHeartIfEnabled() {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.applyInitialHalfHeartIfEnabled();
        }
    }

    /**
     * Applies the initial half-heart setting to a specific player.
     *
     * @param player player who should receive initial half-heart settings
     */
    public void applyInitialHalfHeart(Player player) {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.applyInitialHalfHeart(player);
        }
    }

    /**
     * Restores default max health values for the provided player.
     *
     * @param player player whose max health should be restored
     */
    public void restoreDefaultMaxHealth(Player player) {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.restoreDefaultMaxHealth(player);
        }
    }

    /** Refreshes all currently open preparation GUIs if available. */
    public void refreshOpenPrepGuis() {
        PrepGuiCoordinatorService prepGuiCoordinatorService = prepGuiCoordinatorServiceSupplier.get();
        if (prepGuiCoordinatorService != null) {
            prepGuiCoordinatorService.refreshOpenPrepGuis();
        }
    }

    /** Clears action bars for all currently online players. */
    public void clearActionBar() {
        ActionBarTickerService actionBarTickerService = actionBarTickerServiceSupplier.get();
        if (actionBarTickerService != null) {
            actionBarTickerService.clearActionBar(Bukkit.getOnlinePlayers());
        }
    }

    /** Clears stored paused-run snapshots from the session state service. */
    public void clearPausedSnapshots() {
        PausedRunStateService pausedRunStateService = pausedRunStateServiceSupplier.get();
        if (pausedRunStateService != null) {
            pausedRunStateService.clearSnapshots();
        }
    }

    /**
     * Stashes player run state for lobby transfer with optional teleport.
     *
     * @param player          player whose run state should be stashed
     * @param teleportToLobby true to teleport player to lobby after stashing
     */
    public void stashRunStateForLobby(Player player, boolean teleportToLobby) {
        PausedRunStateService pausedRunStateService = pausedRunStateServiceSupplier.get();
        if (pausedRunStateService == null) {
            return;
        }

        pausedRunStateService.stashRunStateForLobby(player, teleportToLobby, () -> {
            WorldResetManager worldResetManager = worldResetManagerSupplier.get();
            return worldResetManager != null ? worldResetManager.getConfiguredLimboSpawn() : null;
        });
    }
}
