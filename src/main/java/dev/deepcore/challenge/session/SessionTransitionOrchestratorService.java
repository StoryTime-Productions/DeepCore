package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.portal.PortalRoutingService;
import dev.deepcore.challenge.portal.RespawnRoutingService;
import dev.deepcore.challenge.preview.PreviewAnchorService;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.vitals.SharedVitalsService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.challenge.world.WorldStorageService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates top-level session transitions between prep and run phases.
 */
public final class SessionTransitionOrchestratorService {
    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final SessionState sessionState;
    private final ChallengeManager challengeManager;
    private final Set<UUID> readyPlayers;
    private final Set<UUID> participants;
    private final Set<UUID> eliminatedPlayers;
    private final Set<UUID> recentlyDeadPlayers;
    private final DegradingInventoryService degradingInventoryService;
    private final SharedVitalsService sharedVitalsService;
    private final RunProgressService runProgressService;
    private final RunStatusService runStatusService;
    private final PrepCountdownService prepCountdownService;
    private final TaskGroup taskGroup;
    private final String degradingInventoryTaskKey;
    private final String lobbySidebarTaskKey;
    private final ActionBarTickerService actionBarTickerService;
    private final CompletionReturnService completionReturnService;
    private final PausedRunStateService pausedRunStateService;
    private final PrepAreaService prepAreaService;
    private final WorldClassificationService worldClassificationService;
    private final PreviewOrchestratorService previewOrchestratorService;
    private final PreviewAnchorService previewAnchorService;
    private final PrepBookService prepBookService;
    private final PortalRoutingService portalRoutingService;
    private final RespawnRoutingService respawnRoutingService;
    private final SharedInventorySyncService sharedInventorySyncService;
    private final WorldStorageService worldStorageService;
    private final RunHealthCoordinatorService runHealthCoordinatorService;
    private final Runnable syncWorldRules;
    private final Runnable refreshLobbyPreview;
    private final Runnable refreshOpenPrepGuis;
    private final Runnable startLobbySidebarTask;
    private final Runnable clearPausedSnapshots;
    private final Consumer<Player> clearLobbySidebar;
    private final Consumer<Player> stashRunStateForLobby;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;

    /**
     * Creates the session transition orchestrator service.
     *
     * @param plugin                      plugin scheduler and lifecycle owner
     * @param log                         challenge logger for player/admin
     *                                    messaging
     * @param sessionState                mutable session phase/state container
     * @param challengeManager            challenge configuration and toggles
     *                                    manager
     * @param readyPlayers                players marked ready during prep
     * @param participants                active run participants
     * @param eliminatedPlayers           hardcore-eliminated participants
     * @param recentlyDeadPlayers         recently dead participants for failure
     *                                    checks
     * @param degradingInventoryService   degrading-inventory state service
     * @param sharedVitalsService         shared health/hunger synchronization
     *                                    service
     * @param runProgressService          run milestone progress tracker
     * @param runStatusService            run status/timing announcer service
     * @param prepCountdownService        prep countdown scheduler service
     * @param taskGroup                   grouped task lifecycle manager
     * @param degradingInventoryTaskKey   task key for degrading-inventory ticker
     * @param lobbySidebarTaskKey         task key for lobby sidebar ticker
     * @param actionBarTickerService      action-bar ticker service
     * @param completionReturnService     completion return/teleport service
     * @param pausedRunStateService       paused-run snapshot state service
     * @param prepAreaService             prep area border and region service
     * @param worldClassificationService  world classification helper service
     * @param previewOrchestratorService  lobby preview orchestration service
     * @param previewAnchorService        lobby anchor/respawn resolver service
     * @param prepBookService             prep guide book service
     * @param portalRoutingService        portal transit routing service
     * @param respawnRoutingService       respawn routing service
     * @param sharedInventorySyncService  shared inventory synchronization service
     * @param worldStorageService         world storage directory manager
     * @param runHealthCoordinatorService run health/vitals coordinator service
     * @param syncWorldRules              runnable that reapplies world rule
     *                                    policies
     * @param refreshLobbyPreview         runnable that refreshes lobby preview
     *                                    state
     * @param refreshOpenPrepGuis         runnable that refreshes open prep GUIs
     * @param startLobbySidebarTask       runnable that starts lobby sidebar updates
     * @param clearPausedSnapshots        runnable that clears paused run snapshots
     * @param clearLobbySidebar           consumer clearing a player's lobby sidebar
     * @param stashRunStateForLobby       consumer stashing a player's run state for
     *                                    lobby
     * @param worldResetManagerSupplier   supplier for current world reset manager
     */
    public SessionTransitionOrchestratorService(
            JavaPlugin plugin,
            DeepCoreLogger log,
            SessionState sessionState,
            ChallengeManager challengeManager,
            Set<UUID> readyPlayers,
            Set<UUID> participants,
            Set<UUID> eliminatedPlayers,
            Set<UUID> recentlyDeadPlayers,
            DegradingInventoryService degradingInventoryService,
            SharedVitalsService sharedVitalsService,
            RunProgressService runProgressService,
            RunStatusService runStatusService,
            PrepCountdownService prepCountdownService,
            TaskGroup taskGroup,
            String degradingInventoryTaskKey,
            String lobbySidebarTaskKey,
            ActionBarTickerService actionBarTickerService,
            CompletionReturnService completionReturnService,
            PausedRunStateService pausedRunStateService,
            PrepAreaService prepAreaService,
            WorldClassificationService worldClassificationService,
            PreviewOrchestratorService previewOrchestratorService,
            PreviewAnchorService previewAnchorService,
            PrepBookService prepBookService,
            PortalRoutingService portalRoutingService,
            RespawnRoutingService respawnRoutingService,
            SharedInventorySyncService sharedInventorySyncService,
            WorldStorageService worldStorageService,
            RunHealthCoordinatorService runHealthCoordinatorService,
            Runnable syncWorldRules,
            Runnable refreshLobbyPreview,
            Runnable refreshOpenPrepGuis,
            Runnable startLobbySidebarTask,
            Runnable clearPausedSnapshots,
            Consumer<Player> clearLobbySidebar,
            Consumer<Player> stashRunStateForLobby,
            Supplier<WorldResetManager> worldResetManagerSupplier) {
        this.plugin = plugin;
        this.log = log;
        this.sessionState = sessionState;
        this.challengeManager = challengeManager;
        this.readyPlayers = readyPlayers;
        this.participants = participants;
        this.eliminatedPlayers = eliminatedPlayers;
        this.recentlyDeadPlayers = recentlyDeadPlayers;
        this.degradingInventoryService = degradingInventoryService;
        this.sharedVitalsService = sharedVitalsService;
        this.runProgressService = runProgressService;
        this.runStatusService = runStatusService;
        this.prepCountdownService = prepCountdownService;
        this.taskGroup = taskGroup;
        this.degradingInventoryTaskKey = degradingInventoryTaskKey;
        this.lobbySidebarTaskKey = lobbySidebarTaskKey;
        this.actionBarTickerService = actionBarTickerService;
        this.completionReturnService = completionReturnService;
        this.pausedRunStateService = pausedRunStateService;
        this.prepAreaService = prepAreaService;
        this.worldClassificationService = worldClassificationService;
        this.previewOrchestratorService = previewOrchestratorService;
        this.previewAnchorService = previewAnchorService;
        this.prepBookService = prepBookService;
        this.portalRoutingService = portalRoutingService;
        this.respawnRoutingService = respawnRoutingService;
        this.sharedInventorySyncService = sharedInventorySyncService;
        this.worldStorageService = worldStorageService;
        this.runHealthCoordinatorService = runHealthCoordinatorService;
        this.syncWorldRules = syncWorldRules;
        this.refreshLobbyPreview = refreshLobbyPreview;
        this.refreshOpenPrepGuis = refreshOpenPrepGuis;
        this.startLobbySidebarTask = startLobbySidebarTask;
        this.clearPausedSnapshots = clearPausedSnapshots;
        this.clearLobbySidebar = clearLobbySidebar;
        this.stashRunStateForLobby = stashRunStateForLobby;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
    }

    /** Initializes prep-mode runtime state and lobby preview infrastructure. */
    public void initialize() {
        syncWorldRules.run();
        worldStorageService.ensureAllWorldStorageDirectories();
        startLobbySidebarTask.run();
        previewOrchestratorService.removeLobbyBlockDisplayEntities();
        for (Player player : Bukkit.getOnlinePlayers()) {
            previewAnchorService.teleportToLobbyIfConfigured(player);
            if (!worldClassificationService.isTrainingWorld(player.getWorld())) {
                prepBookService.giveIfMissing(player);
            } else {
                prepBookService.removeFromInventory(player);
            }
        }
        prepAreaService.applyBordersToOnlinePlayers(
                sessionState.is(SessionState.Phase.RUNNING), worldClassificationService::isPrepBorderExemptWorld);
        previewOrchestratorService.removeLobbyBlockDisplayEntities();
        refreshLobbyPreview.run();
    }

    /** Shuts down active tasks and clears transient session runtime state. */
    public void shutdown() {
        if (sessionState.is(SessionState.Phase.RUNNING)) {
            for (Player participant : Bukkit.getOnlinePlayers()) {
                if (participants.contains(participant.getUniqueId())) {
                    stashRunStateForLobby.accept(participant);
                }
            }
        }

        prepCountdownService.cancel();
        taskGroup.cancel(degradingInventoryTaskKey);
        actionBarTickerService.stop();
        taskGroup.cancel(lobbySidebarTaskKey);
        completionReturnService.stop();
        clearPausedSnapshots.run();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearLobbySidebar.accept(player);
            runHealthCoordinatorService.restoreDefaultMaxHealth(player);
        }
        prepAreaService.clearBorders();
        previewOrchestratorService.clearLobbyPreviewEntities();
        portalRoutingService.clearTransitCooldowns();
        respawnRoutingService.clearPendingRespawns();
        sharedInventorySyncService.clearWearableSnapshots();
    }

    /** Resets all run state and returns the session to prep mode defaults. */
    public void resetForNewRun() {
        prepCountdownService.cancel();
        taskGroup.cancel(degradingInventoryTaskKey);
        actionBarTickerService.stop();
        completionReturnService.stop();

        sessionState.setPhase(SessionState.Phase.PREP);
        readyPlayers.clear();
        participants.clear();
        eliminatedPlayers.clear();
        recentlyDeadPlayers.clear();
        degradingInventoryService.resetAllowedInventorySlots();
        sharedVitalsService.resetSyncFlags();
        sessionState.timing().reset();
        runProgressService.reset();
        runStatusService.reset();
        clearPausedSnapshots.run();
        portalRoutingService.clearTransitCooldowns();
        respawnRoutingService.clearPendingRespawns();
        sharedInventorySyncService.clearWearableSnapshots();

        challengeManager.loadFromConfig();
        syncWorldRules.run();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            if (!worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
                previewAnchorService.teleportToLobbyIfConfigured(player);
            }
            clearLobbySidebar.accept(player);
            runHealthCoordinatorService.restoreDefaultMaxHealth(player);
            if (!worldClassificationService.isTrainingWorld(player.getWorld())) {
                prepBookService.giveIfMissing(player);
            } else {
                prepBookService.removeFromInventory(player);
            }
        }

        prepAreaService.applyBordersToOnlinePlayers(
                sessionState.is(SessionState.Phase.RUNNING), worldClassificationService::isPrepBorderExemptWorld);
        refreshLobbyPreview.run();
        Bukkit.getScheduler().runTask(plugin, refreshOpenPrepGuis);
    }

    /** Ends an active challenge and transitions all players back to prep mode. */
    public void endChallengeAndReturnToPrep() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null) {
            worldResetManager.selectRandomLobbyWorld();
        }

        resetForNewRun();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        log.info("Waiting for players...");
    }

    /**
     * Ensures a player has the prep book while the session is in prep phase.
     *
     * @param player player who should receive prep book when in prep phase
     */
    public void ensurePrepBook(Player player) {
        if (sessionState.is(SessionState.Phase.PREP)
                && !worldClassificationService.isTrainingWorld(player.getWorld())) {
            prepBookService.giveIfMissing(player);
        } else if (worldClassificationService.isTrainingWorld(player.getWorld())) {
            prepBookService.removeFromInventory(player);
        }
    }

    /**
     * Handles world load events by preparing storage and refreshing previews.
     *
     * @param event world load event carrying the loaded world context
     */
    public void handleWorldLoad(WorldLoadEvent event) {
        worldStorageService.ensureWorldStorageDirectories(event.getWorld());
        syncWorldRules.run();
        if (!sessionState.is(SessionState.Phase.RUNNING)) {
            previewOrchestratorService.removeLobbyBlockDisplayEntities();
            Bukkit.getScheduler().runTaskLater(plugin, refreshLobbyPreview, 1L);
        }
    }

    /** Clears paused run snapshots held by the paused-run state service. */
    public void clearPausedSnapshots() {
        pausedRunStateService.clearSnapshots();
    }
}
