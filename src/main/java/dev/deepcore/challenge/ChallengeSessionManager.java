package dev.deepcore.challenge;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.events.ChallengeEventRegistrar;
import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.challenge.inventory.InventoryMechanicsCoordinatorService;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.portal.PortalRoutingService;
import dev.deepcore.challenge.portal.PortalTransitCoordinatorService;
import dev.deepcore.challenge.portal.RespawnRoutingService;
import dev.deepcore.challenge.preview.PreviewAnchorService;
import dev.deepcore.challenge.preview.PreviewEntityService;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.preview.PreviewRuntimeService;
import dev.deepcore.challenge.preview.PreviewSampleService;
import dev.deepcore.challenge.session.ActionBarTickerService;
import dev.deepcore.challenge.session.CompletionReturnService;
import dev.deepcore.challenge.session.DegradingInventoryTickerService;
import dev.deepcore.challenge.session.ParticipantsView;
import dev.deepcore.challenge.session.PausedRunStateService;
import dev.deepcore.challenge.session.PlayerLobbyStateService;
import dev.deepcore.challenge.session.PrepAreaService;
import dev.deepcore.challenge.session.PrepCountdownService;
import dev.deepcore.challenge.session.PrepGuiCoordinatorService;
import dev.deepcore.challenge.session.PrepGuiFlowService;
import dev.deepcore.challenge.session.PrepReadinessService;
import dev.deepcore.challenge.session.PrepSettingsService;
import dev.deepcore.challenge.session.RunCompletionService;
import dev.deepcore.challenge.session.RunHealthCoordinatorService;
import dev.deepcore.challenge.session.RunPauseResumeService;
import dev.deepcore.challenge.session.RunProgressService;
import dev.deepcore.challenge.session.RunStartGuardService;
import dev.deepcore.challenge.session.RunStartService;
import dev.deepcore.challenge.session.RunStatusService;
import dev.deepcore.challenge.session.RunUiFormattingService;
import dev.deepcore.challenge.session.SessionFailureService;
import dev.deepcore.challenge.session.SessionOperationService;
import dev.deepcore.challenge.session.SessionParticipantContextService;
import dev.deepcore.challenge.session.SessionPlayerLifecycleService;
import dev.deepcore.challenge.session.SessionRulesCoordinatorService;
import dev.deepcore.challenge.session.SessionState;
import dev.deepcore.challenge.session.SessionTransitionOrchestratorService;
import dev.deepcore.challenge.session.SessionUiCoordinatorService;
import dev.deepcore.challenge.session.SidebarModelFactory;
import dev.deepcore.challenge.session.TaskGroup;
import dev.deepcore.challenge.ui.LobbySidebarCoordinatorService;
import dev.deepcore.challenge.ui.LobbySidebarService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.ui.PrepGuiRenderer;
import dev.deepcore.challenge.vitals.SharedVitalsService;
import dev.deepcore.challenge.world.ChallengeSessionWorldBridge;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.challenge.world.WorldStorageService;
import dev.deepcore.logging.DeepCoreLogger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates challenge session state, player flow, and run lifecycle events.
 */
public final class ChallengeSessionManager implements ChallengeSessionWorldBridge {
    private static final String PREP_GUI_TITLE =
            ChatColor.BLACK + " " + ChatColor.BOLD + "DeepCore Prep" + ChatColor.RESET + ChatColor.AQUA + " ";
    private static final double HALF_HEART_HEALTH = 1.0D;
    private static final double DEFAULT_MAX_HEALTH = 20.0D;
    private static final int PREP_AREA_DIAMETER_BLOCKS = 5;
    private static final DateTimeFormatter RUN_HISTORY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String LOBBY_SIDEBAR_OBJECTIVE_NAME = "deepcore_lobby";
    private static final String LOBBY_SIDEBAR_TITLE =
            ChatColor.AQUA + " " + ChatColor.BOLD + "DeepCore" + ChatColor.RESET + ChatColor.AQUA + " ";
    private static final String TASK_DEGRADING_INVENTORY = "degrading-inventory";
    private static final String TASK_LOBBY_SIDEBAR = "lobby-sidebar";

    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final ChallengeManager challengeManager;
    private final ChallengeConfigView configView;

    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> recentlyDeadPlayers = new HashSet<>();
    private final Map<UUID, Map<Material, Integer>> equippedWearableCounts = new HashMap<>();
    private final LobbySidebarService lobbySidebarService;
    private final PausedRunStateService pausedRunStateService;
    private final PrepAreaService prepAreaService;
    private final ParticipantsView participantsView;
    private final SessionParticipantContextService sessionParticipantContextService;
    private final SessionOperationService sessionOperationService;
    private final PlayerLobbyStateService playerLobbyStateService;
    private final PrepCountdownService prepCountdownService;
    private final PrepReadinessService prepReadinessService;
    private final PrepSettingsService prepSettingsService;
    private final PrepGuiCoordinatorService prepGuiCoordinatorService;
    private final PrepGuiFlowService prepGuiFlowService;
    private final PrepGuiRenderer prepGuiRenderer;
    private final PrepBookService prepBookService;
    private final DegradingInventoryService degradingInventoryService;
    private final SharedInventorySyncService sharedInventorySyncService;
    private final InventoryMechanicsCoordinatorService inventoryMechanicsCoordinatorService;
    private final SharedVitalsService sharedVitalsService;
    private final PreviewAnchorService previewAnchorService;
    private final PreviewOrchestratorService previewOrchestratorService;
    private final PortalRoutingService portalRoutingService;
    private final PortalTransitCoordinatorService portalTransitCoordinatorService;
    private final RespawnRoutingService respawnRoutingService;
    private final ActionBarTickerService actionBarTickerService;
    private final RunHealthCoordinatorService runHealthCoordinatorService;
    private final SessionRulesCoordinatorService sessionRulesCoordinatorService;
    private final RunStartGuardService runStartGuardService;
    private final RunCompletionService runCompletionService;
    private final RunProgressService runProgressService;
    private final RunStartService runStartService;
    private final RunUiFormattingService runUiFormattingService;
    private final RunStatusService runStatusService;
    private final SessionFailureService sessionFailureService;
    private final SessionPlayerLifecycleService sessionPlayerLifecycleService;
    private final SessionTransitionOrchestratorService sessionTransitionOrchestratorService;
    private final ChallengeEventRegistrar challengeEventRegistrar;
    private final RunPauseResumeService runPauseResumeService;
    private final DegradingInventoryTickerService degradingInventoryTickerService;
    private final SessionUiCoordinatorService sessionUiCoordinatorService;
    private final SidebarModelFactory sidebarModelFactory;
    private final WorldClassificationService worldClassificationService;
    private final WorldStorageService worldStorageService;
    private final LobbySidebarCoordinatorService lobbySidebarCoordinatorService;
    private final CompletionReturnService completionReturnService;
    private final TaskGroup taskGroup;
    private WorldResetManager worldResetManager;
    private dev.deepcore.records.RunRecordsService recordsService;

    private final SessionState sessionState;

    /**
     * Creates a session manager bound to plugin services and challenge state.
     *
     * @param plugin           plugin root instance used for service wiring and
     *                         scheduling
     * @param challengeManager challenge settings and component manager
     */
    public ChallengeSessionManager(JavaPlugin plugin, ChallengeManager challengeManager) {
        this.plugin = plugin;
        this.log = ((DeepCorePlugin) plugin).getDeepCoreLogger();
        this.challengeManager = challengeManager;
        this.configView = new ChallengeConfigView(plugin);
        this.participantsView = new ParticipantsView();
        this.sessionState = new SessionState();
        this.sessionParticipantContextService =
                new SessionParticipantContextService(challengeManager, sessionState, participantsView, participants);
        this.runStartGuardService = new RunStartGuardService(() -> worldResetManager, log);
        this.sessionOperationService = new SessionOperationService(
                this::getSessionUiCoordinatorService,
                this::getDegradingInventoryTickerService,
                this::getDegradingInventoryService,
                this::getSharedInventorySyncService,
                this::getRunHealthCoordinatorService,
                this::getPrepGuiCoordinatorService,
                this::getActionBarTickerService,
                this::getPausedRunStateService,
                () -> worldResetManager);
        NamespacedKey lockedInventoryBarrierKey = new NamespacedKey(plugin, "locked-inventory-barrier");
        this.degradingInventoryService = new DegradingInventoryService(
                lockedInventoryBarrierKey,
                () -> challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY),
                sessionParticipantContextService::isChallengeActive);
        this.lobbySidebarService = new LobbySidebarService(LOBBY_SIDEBAR_OBJECTIVE_NAME, LOBBY_SIDEBAR_TITLE);
        this.pausedRunStateService = new PausedRunStateService(DEFAULT_MAX_HEALTH);
        this.prepAreaService = new PrepAreaService(PREP_AREA_DIAMETER_BLOCKS);
        this.prepCountdownService = new PrepCountdownService(plugin);
        this.prepGuiRenderer = new PrepGuiRenderer();
        this.prepBookService = new PrepBookService(plugin);
        PreviewSampleService previewSampleService = new PreviewSampleService(plugin, log);
        this.previewAnchorService = new PreviewAnchorService(plugin, () -> worldResetManager);
        PreviewRuntimeService previewRuntimeService = new PreviewRuntimeService(plugin);
        PreviewEntityService previewEntityService = new PreviewEntityService();
        this.previewOrchestratorService = new PreviewOrchestratorService(
                plugin,
                configView,
                previewSampleService,
                previewAnchorService,
                previewRuntimeService,
                previewEntityService,
                () -> worldResetManager,
                this::isRunningPhase);
        this.portalRoutingService = new PortalRoutingService(() -> worldResetManager);
        this.actionBarTickerService = new ActionBarTickerService(plugin);
        this.sharedVitalsService =
                new SharedVitalsService(plugin, sessionParticipantContextService::getPlayersForSharedVitals);
        this.runHealthCoordinatorService = new RunHealthCoordinatorService(
                challengeManager,
                () -> sharedVitalsService,
                sessionParticipantContextService::getOnlineParticipants,
                sessionParticipantContextService::isChallengeActive,
                () -> challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY),
                sessionOperationService::clearLockedBarrierSlots,
                HALF_HEART_HEALTH,
                DEFAULT_MAX_HEALTH);
        this.sessionRulesCoordinatorService = new SessionRulesCoordinatorService(
                challengeManager, () -> worldResetManager, this::getRunHealthCoordinatorService);
        this.runProgressService = new RunProgressService();
        this.runUiFormattingService = new RunUiFormattingService();
        this.runStatusService = new RunStatusService(runProgressService, runUiFormattingService, log);
        this.taskGroup = new TaskGroup();
        this.degradingInventoryTickerService = new DegradingInventoryTickerService(
                plugin,
                taskGroup,
                TASK_DEGRADING_INVENTORY,
                sessionState,
                challengeManager,
                configView,
                degradingInventoryService,
                sessionParticipantContextService::getOnlineParticipants,
                sessionOperationService::enforceInventorySlotCap,
                log);
        this.sessionFailureService = new SessionFailureService(
                sessionState,
                challengeManager,
                participants,
                eliminatedPlayers,
                recentlyDeadPlayers,
                actionBarTickerService,
                sessionOperationService::clearActionBar,
                () -> worldResetManager,
                log);
        this.worldClassificationService = new WorldClassificationService(configView, () -> worldResetManager);
        this.portalTransitCoordinatorService = new PortalTransitCoordinatorService(
                sessionState,
                () -> worldResetManager,
                worldClassificationService,
                portalRoutingService,
                prepAreaService);
        this.worldStorageService = new WorldStorageService(log);
        this.playerLobbyStateService = new PlayerLobbyStateService(worldClassificationService, prepBookService);
        this.runStartService = new RunStartService(
                plugin,
                sessionState,
                challengeManager,
                participants,
                readyPlayers,
                prepAreaService,
                worldClassificationService,
                previewOrchestratorService,
                runProgressService,
                runStatusService,
                sessionParticipantContextService::getOnlineParticipants,
                () -> worldResetManager,
                prepBookService,
                sessionOperationService::clearLobbySidebar,
                sessionOperationService::clearPausedSnapshots,
                this::syncWorldRules,
                sessionOperationService::startActionBarTask,
                sessionOperationService::snapshotEquippedWearablesForParticipants,
                sessionOperationService::enforceInventorySlotCap,
                () -> sessionOperationService.startDegradingInventoryTask(true),
                sessionParticipantContextService::isSharedInventoryEnabled,
                sessionOperationService::syncSharedInventoryFromFirstParticipant,
                sessionOperationService::syncSharedHealthFromFirstParticipant,
                sessionOperationService::syncSharedHungerFromMostFilledParticipant,
                sessionOperationService::applyInitialHalfHeartIfEnabled,
                runStartGuardService::isDiscoPreviewBlockingChallengeStart,
                runStartGuardService::announceDiscoPreviewStartBlocked,
                sessionOperationService::refreshOpenPrepGuis,
                log);
        this.runPauseResumeService = new RunPauseResumeService(
                sessionState,
                () -> worldResetManager,
                log,
                sessionParticipantContextService::getOnlineParticipants,
                sessionOperationService::clearPausedSnapshots,
                participant -> sessionOperationService.stashRunStateForLobby(participant, true),
                pausedRunStateService,
                actionBarTickerService,
                taskGroup,
                TASK_DEGRADING_INVENTORY,
                sessionOperationService::clearActionBar,
                prepAreaService,
                challengeManager,
                sessionOperationService::startActionBarTask,
                () -> sessionOperationService.startDegradingInventoryTask(false));
        this.sidebarModelFactory = new SidebarModelFactory();
        this.prepReadinessService = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);
        this.prepSettingsService =
                new PrepSettingsService(challengeManager, this::syncWorldRules, this::applySharedVitalsIfEnabled);
        this.prepGuiFlowService =
                new PrepGuiFlowService(prepSettingsService, challengeManager, prepGuiRenderer, () -> recordsService);
        this.prepGuiCoordinatorService = new PrepGuiCoordinatorService(
                plugin,
                log,
                sessionState,
                readyPlayers,
                participants,
                participantsView,
                challengeManager,
                prepGuiRenderer,
                prepBookService,
                prepGuiFlowService,
                prepReadinessService,
                previewOrchestratorService,
                () -> worldResetManager,
                () -> recordsService,
                runUiFormattingService,
                runStartGuardService::isDiscoPreviewBlockingChallengeStart,
                runStartGuardService::announceDiscoPreviewStartBlocked,
                this::startRun,
                PREP_GUI_TITLE,
                RUN_HISTORY_DATE_FORMATTER);
        this.lobbySidebarCoordinatorService = new LobbySidebarCoordinatorService(
                lobbySidebarService, worldClassificationService, runUiFormattingService);
        this.sessionUiCoordinatorService = new SessionUiCoordinatorService(
                plugin,
                taskGroup,
                TASK_LOBBY_SIDEBAR,
                actionBarTickerService,
                runStatusService,
                sessionState,
                sessionParticipantContextService::getOnlineParticipants,
                participantsView,
                sidebarModelFactory,
                () -> recordsService,
                () -> readyPlayers.size(),
                lobbySidebarCoordinatorService,
                lobbySidebarService);
        this.completionReturnService = new CompletionReturnService(plugin);
        this.runCompletionService = new RunCompletionService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> recordsService,
                participants,
                () -> worldResetManager,
                this::endChallengeAndReturnToPrep,
                log);
        this.respawnRoutingService = new RespawnRoutingService(
                () -> worldResetManager,
                portalRoutingService::resolveLinkedPortalWorld,
                portalRoutingService::resolveLinkedEndWorld);
        this.sharedInventorySyncService = new SharedInventorySyncService(
                plugin,
                sessionParticipantContextService::isChallengeActive,
                sessionParticipantContextService::isSharedInventoryEnabled,
                sessionParticipantContextService::isRunningPhase,
                sessionParticipantContextService::getActiveParticipants,
                () -> challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY),
                degradingInventoryService::enforceInventorySlotCap,
                equippedWearableCounts);
        this.sessionPlayerLifecycleService = new SessionPlayerLifecycleService(
                plugin,
                log,
                challengeManager,
                sessionState,
                readyPlayers,
                participants,
                eliminatedPlayers,
                recentlyDeadPlayers,
                playerLobbyStateService,
                previewAnchorService,
                previewOrchestratorService,
                worldClassificationService,
                prepAreaService,
                prepBookService,
                prepGuiCoordinatorService,
                prepReadinessService,
                runPauseResumeService,
                respawnRoutingService,
                sessionFailureService,
                runStatusService,
                sharedInventorySyncService,
                sessionParticipantContextService::isSharedInventoryEnabled,
                sessionParticipantContextService::isChallengeActive,
                sessionParticipantContextService::getOnlineParticipants,
                sessionOperationService::enforceInventorySlotCap,
                sessionOperationService::clearLockedBarrierSlots,
                sessionOperationService::applyInitialHalfHeart,
                sessionOperationService::restoreDefaultMaxHealth,
                this::syncWorldRules,
                this::refreshLobbyPreview,
                sessionOperationService::refreshOpenPrepGuis,
                this::startRun,
                this::endChallengeAndReturnToPrep,
                sessionOperationService::syncSharedInventoryFromFirstParticipant,
                sessionOperationService::syncSharedHealthFromFirstParticipant,
                sessionOperationService::syncSharedHungerFromMostFilledParticipant,
                runStartGuardService::isDiscoPreviewBlockingChallengeStart,
                runStartGuardService::announceDiscoPreviewStartBlocked);
        this.inventoryMechanicsCoordinatorService = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingInventoryService,
                sharedInventorySyncService,
                sessionParticipantContextService::isChallengeActive,
                sessionParticipantContextService::isSharedInventoryEnabled,
                sessionParticipantContextService::getOnlineParticipants,
                sessionOperationService::enforceInventorySlotCap,
                log);
        this.sessionTransitionOrchestratorService = new SessionTransitionOrchestratorService(
                plugin,
                log,
                sessionState,
                challengeManager,
                readyPlayers,
                participants,
                eliminatedPlayers,
                recentlyDeadPlayers,
                degradingInventoryService,
                sharedVitalsService,
                runProgressService,
                runStatusService,
                prepCountdownService,
                taskGroup,
                TASK_DEGRADING_INVENTORY,
                TASK_LOBBY_SIDEBAR,
                actionBarTickerService,
                completionReturnService,
                pausedRunStateService,
                prepAreaService,
                worldClassificationService,
                previewOrchestratorService,
                previewAnchorService,
                prepBookService,
                portalRoutingService,
                respawnRoutingService,
                sharedInventorySyncService,
                worldStorageService,
                runHealthCoordinatorService,
                this::syncWorldRules,
                this::refreshLobbyPreview,
                sessionOperationService::refreshOpenPrepGuis,
                sessionOperationService::startLobbySidebarTask,
                sessionOperationService::clearPausedSnapshots,
                sessionOperationService::clearLobbySidebar,
                participant -> sessionOperationService.stashRunStateForLobby(participant, true),
                () -> worldResetManager);
        this.challengeEventRegistrar = new ChallengeEventRegistrar(
                portalTransitCoordinatorService,
                sessionPlayerLifecycleService,
                runCompletionService,
                inventoryMechanicsCoordinatorService,
                runHealthCoordinatorService,
                sessionTransitionOrchestratorService,
                prepGuiCoordinatorService);
    }

    /**
     * Initializes prep-mode state for currently online players.
     */
    public void initialize() {
        sessionTransitionOrchestratorService.initialize();
    }

    public void setWorldResetManager(WorldResetManager worldResetManager) {
        this.worldResetManager = worldResetManager;
    }

    public void setRecordsService(dev.deepcore.records.RunRecordsService recordsService) {
        this.recordsService = recordsService;
    }

    /**
     * Registers challenge listener classes bound to extracted domain services.
     */
    public void registerEventListeners() {
        challengeEventRegistrar.registerAll(plugin);
    }

    /**
     * Applies world gamerule and policy settings based on challenge configuration.
     */
    public void syncWorldRules() {
        sessionRulesCoordinatorService.syncWorldRules();
    }

    /**
     * Returns true when challenge settings are currently editable.
     *
     * @return true when the session is in prep phase
     */
    public boolean canEditSettings() {
        return sessionState.is(SessionState.Phase.PREP);
    }

    /**
     * Returns the current challenge phase name in lowercase form.
     *
     * @return lowercase session phase name
     */
    public String getPhaseName() {
        return sessionState.phaseNameLowercase();
    }

    public int getReadyCount() {
        return readyPlayers.size();
    }

    public int getReadyTargetCount() {
        return participantsView.onlineCount();
    }

    public boolean isPrepPhase() {
        return sessionState.is(SessionState.Phase.PREP);
    }

    public boolean isRunningPhase() {
        return sessionState.is(SessionState.Phase.RUNNING);
    }

    public boolean isPausedPhase() {
        return sessionState.is(SessionState.Phase.PAUSED);
    }

    /**
     * Stops scheduled tasks and clears transient state during plugin shutdown.
     */
    public void shutdown() {
        sessionTransitionOrchestratorService.shutdown();
    }

    /**
     * Resets run-specific state and returns all players to prep flow.
     */
    public void resetForNewRun() {
        sessionTransitionOrchestratorService.resetForNewRun();
    }

    /**
     * Ends the current run and transitions back to prep mode.
     */
    public void endChallengeAndReturnToPrep() {
        sessionTransitionOrchestratorService.endChallengeAndReturnToPrep();
    }

    /**
     * Ensures the prep guide book is present in a player's inventory.
     *
     * @param player player to receive the prep guide book
     */
    public void ensurePrepBook(Player player) {
        sessionTransitionOrchestratorService.ensurePrepBook(player);
    }

    /**
     * Rebuilds the lobby preview hologram if no destroy animation is in progress.
     */
    public void refreshLobbyPreview() {
        previewOrchestratorService.refreshLobbyPreview();
    }

    private void startRun() {
        runStartService.startRun();
    }

    /**
     * Pauses an active challenge and stores participant run snapshots.
     *
     * @param sender command sender initiating the pause
     * @return true when pause operations complete successfully
     */
    public boolean pauseChallenge(CommandSender sender) {
        return runPauseResumeService.pause(sender, true);
    }

    /**
     * Resumes a paused challenge and restores participant snapshots.
     *
     * @param sender command sender initiating the resume
     * @return true when resume operations complete successfully
     */
    public boolean resumeChallenge(CommandSender sender) {
        return runPauseResumeService.resume(sender);
    }

    /**
     * Synchronizes shared health/hunger immediately when shared-health mode is
     * enabled.
     */
    public void applySharedVitalsIfEnabled() {
        sessionRulesCoordinatorService.applySharedVitalsIfEnabled();
    }

    public Location getPreferredLobbyTeleportLocation() {
        return previewAnchorService.getPreferredLobbyTeleportLocation();
    }

    private RunHealthCoordinatorService getRunHealthCoordinatorService() {
        return runHealthCoordinatorService;
    }

    private PrepGuiCoordinatorService getPrepGuiCoordinatorService() {
        return prepGuiCoordinatorService;
    }

    private SessionUiCoordinatorService getSessionUiCoordinatorService() {
        return sessionUiCoordinatorService;
    }

    private DegradingInventoryTickerService getDegradingInventoryTickerService() {
        return degradingInventoryTickerService;
    }

    private DegradingInventoryService getDegradingInventoryService() {
        return degradingInventoryService;
    }

    private SharedInventorySyncService getSharedInventorySyncService() {
        return sharedInventorySyncService;
    }

    private ActionBarTickerService getActionBarTickerService() {
        return actionBarTickerService;
    }

    private PausedRunStateService getPausedRunStateService() {
        return pausedRunStateService;
    }
}
