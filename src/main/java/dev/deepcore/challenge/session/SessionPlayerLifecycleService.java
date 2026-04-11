package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.portal.RespawnRoutingService;
import dev.deepcore.challenge.preview.PreviewAnchorService;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates player lifecycle events across prep/countdown/run phases.
 */
public final class SessionPlayerLifecycleService {
    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final ChallengeManager challengeManager;
    private final SessionState sessionState;
    private final Set<UUID> readyPlayers;
    private final Set<UUID> participants;
    private final Set<UUID> eliminatedPlayers;
    private final Set<UUID> recentlyDeadPlayers;
    private final PlayerLobbyStateService playerLobbyStateService;
    private final PreviewAnchorService previewAnchorService;
    private final PreviewOrchestratorService previewOrchestratorService;
    private final WorldClassificationService worldClassificationService;
    private final PrepAreaService prepAreaService;
    private final PrepBookService prepBookService;
    private final PrepGuiCoordinatorService prepGuiCoordinatorService;
    private final PrepReadinessService prepReadinessService;
    private final RunPauseResumeService runPauseResumeService;
    private final RespawnRoutingService respawnRoutingService;
    private final SessionFailureService sessionFailureService;
    private final RunStatusService runStatusService;
    private final SharedInventorySyncService sharedInventorySyncService;
    private final BooleanSupplier isSharedInventoryEnabled;
    private final Predicate<Player> isChallengeActive;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Consumer<Player> enforceInventorySlotCap;
    private final Consumer<Player> clearLockedBarrierSlots;
    private final Consumer<Player> applyInitialHalfHeart;
    private final Consumer<Player> restoreDefaultMaxHealth;
    private final Runnable syncWorldRules;
    private final Runnable refreshLobbyPreview;
    private final Runnable refreshOpenPrepGuis;
    private final Runnable startRun;
    private final Runnable endChallengeAndReturnToPrep;
    private final Runnable syncSharedInventoryFromFirstParticipant;
    private final Runnable syncSharedHealthFromFirstParticipant;
    private final Runnable syncSharedHungerFromMostFilledParticipant;
    private final BooleanSupplier isDiscoPreviewBlockingChallengeStart;
    private final Runnable announceDiscoPreviewStartBlocked;

    /**
     * Creates a session player lifecycle coordination service.
     *
     * @param plugin                                    plugin scheduler and
     *                                                  lifecycle owner
     * @param log                                       challenge logger for
     *                                                  player/admin messaging
     * @param challengeManager                          challenge settings and
     *                                                  component manager
     * @param sessionState                              mutable session phase/state
     *                                                  container
     * @param readyPlayers                              players marked ready during
     *                                                  prep
     * @param participants                              active run participants
     * @param eliminatedPlayers                         hardcore-eliminated
     *                                                  participants
     * @param recentlyDeadPlayers                       recently dead participants
     *                                                  for failure checks
     * @param playerLobbyStateService                   lobby inventory/gamemode
     *                                                  state service
     * @param previewAnchorService                      lobby teleport anchor
     *                                                  resolver
     * @param previewOrchestratorService                lobby preview orchestration
     *                                                  service
     * @param worldClassificationService                world classification helper
     *                                                  service
     * @param prepAreaService                           prep area border and region
     *                                                  service
     * @param prepBookService                           prep guide book service
     * @param prepGuiCoordinatorService                 prep GUI interaction
     *                                                  coordinator
     * @param prepReadinessService                      readiness/countdown
     *                                                  coordinator service
     * @param runPauseResumeService                     pause/resume run coordinator
     *                                                  service
     * @param respawnRoutingService                     respawn routing service
     * @param sessionFailureService                     failure-condition transition
     *                                                  service
     * @param runStatusService                          run status/timing announcer
     *                                                  service
     * @param sharedInventorySyncService                shared inventory
     *                                                  synchronization service
     * @param isSharedInventoryEnabled                  supplier indicating shared
     *                                                  inventory mode
     * @param isChallengeActive                         predicate testing
     *                                                  participant challenge
     *                                                  activity
     * @param onlineParticipantsSupplier                supplier for currently
     *                                                  online participants
     * @param enforceInventorySlotCap                   consumer applying inventory
     *                                                  slot cap
     * @param clearLockedBarrierSlots                   consumer clearing locked
     *                                                  barrier slots
     * @param applyInitialHalfHeart                     consumer applying half-heart
     *                                                  health mode
     * @param restoreDefaultMaxHealth                   consumer restoring default
     *                                                  max health
     * @param syncWorldRules                            runnable that reapplies
     *                                                  world rule policies
     * @param refreshLobbyPreview                       runnable that refreshes
     *                                                  lobby preview state
     * @param refreshOpenPrepGuis                       runnable that refreshes open
     *                                                  prep GUIs
     * @param startRun                                  runnable that starts a
     *                                                  challenge run
     * @param endChallengeAndReturnToPrep               runnable that stops the
     *                                                  current challenge and
     *                                                  returns to prep
     * @param syncSharedInventoryFromFirstParticipant   runnable syncing shared
     *                                                  inventory
     * @param syncSharedHealthFromFirstParticipant      runnable syncing shared
     *                                                  health
     * @param syncSharedHungerFromMostFilledParticipant runnable syncing shared
     *                                                  hunger
     * @param isDiscoPreviewBlockingChallengeStart      supplier for disco-start
     *                                                  blocking state
     * @param announceDiscoPreviewStartBlocked          runnable announcing disco
     *                                                  start block
     */
    public SessionPlayerLifecycleService(
            JavaPlugin plugin,
            DeepCoreLogger log,
            ChallengeManager challengeManager,
            SessionState sessionState,
            Set<UUID> readyPlayers,
            Set<UUID> participants,
            Set<UUID> eliminatedPlayers,
            Set<UUID> recentlyDeadPlayers,
            PlayerLobbyStateService playerLobbyStateService,
            PreviewAnchorService previewAnchorService,
            PreviewOrchestratorService previewOrchestratorService,
            WorldClassificationService worldClassificationService,
            PrepAreaService prepAreaService,
            PrepBookService prepBookService,
            PrepGuiCoordinatorService prepGuiCoordinatorService,
            PrepReadinessService prepReadinessService,
            RunPauseResumeService runPauseResumeService,
            RespawnRoutingService respawnRoutingService,
            SessionFailureService sessionFailureService,
            RunStatusService runStatusService,
            SharedInventorySyncService sharedInventorySyncService,
            BooleanSupplier isSharedInventoryEnabled,
            Predicate<Player> isChallengeActive,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Consumer<Player> enforceInventorySlotCap,
            Consumer<Player> clearLockedBarrierSlots,
            Consumer<Player> applyInitialHalfHeart,
            Consumer<Player> restoreDefaultMaxHealth,
            Runnable syncWorldRules,
            Runnable refreshLobbyPreview,
            Runnable refreshOpenPrepGuis,
            Runnable startRun,
            Runnable endChallengeAndReturnToPrep,
            Runnable syncSharedInventoryFromFirstParticipant,
            Runnable syncSharedHealthFromFirstParticipant,
            Runnable syncSharedHungerFromMostFilledParticipant,
            BooleanSupplier isDiscoPreviewBlockingChallengeStart,
            Runnable announceDiscoPreviewStartBlocked) {
        this.plugin = plugin;
        this.log = log;
        this.challengeManager = challengeManager;
        this.sessionState = sessionState;
        this.readyPlayers = readyPlayers;
        this.participants = participants;
        this.eliminatedPlayers = eliminatedPlayers;
        this.recentlyDeadPlayers = recentlyDeadPlayers;
        this.playerLobbyStateService = playerLobbyStateService;
        this.previewAnchorService = previewAnchorService;
        this.previewOrchestratorService = previewOrchestratorService;
        this.worldClassificationService = worldClassificationService;
        this.prepAreaService = prepAreaService;
        this.prepBookService = prepBookService;
        this.prepGuiCoordinatorService = prepGuiCoordinatorService;
        this.prepReadinessService = prepReadinessService;
        this.runPauseResumeService = runPauseResumeService;
        this.respawnRoutingService = respawnRoutingService;
        this.sessionFailureService = sessionFailureService;
        this.runStatusService = runStatusService;
        this.sharedInventorySyncService = sharedInventorySyncService;
        this.isSharedInventoryEnabled = isSharedInventoryEnabled;
        this.isChallengeActive = isChallengeActive;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.enforceInventorySlotCap = enforceInventorySlotCap;
        this.clearLockedBarrierSlots = clearLockedBarrierSlots;
        this.applyInitialHalfHeart = applyInitialHalfHeart;
        this.restoreDefaultMaxHealth = restoreDefaultMaxHealth;
        this.syncWorldRules = syncWorldRules;
        this.refreshLobbyPreview = refreshLobbyPreview;
        this.refreshOpenPrepGuis = refreshOpenPrepGuis;
        this.startRun = startRun;
        this.endChallengeAndReturnToPrep = endChallengeAndReturnToPrep;
        this.syncSharedInventoryFromFirstParticipant = syncSharedInventoryFromFirstParticipant;
        this.syncSharedHealthFromFirstParticipant = syncSharedHealthFromFirstParticipant;
        this.syncSharedHungerFromMostFilledParticipant = syncSharedHungerFromMostFilledParticipant;
        this.isDiscoPreviewBlockingChallengeStart = isDiscoPreviewBlockingChallengeStart;
        this.announceDiscoPreviewStartBlocked = announceDiscoPreviewStartBlocked;
    }

    /**
     * Handles player joins and applies session-phase specific onboarding state.
     *
     * @param event join event for the connected player
     */
    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerLobbyStateService.enforceSurvivalOnWorldEntry(
                player,
                sessionState.is(SessionState.Phase.RUNNING),
                challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE),
                eliminatedPlayers);
        playerLobbyStateService.applyLobbyInventoryLoadoutIfInLobbyWorld(player);
        syncWorldRules.run();

        if (!sessionState.is(SessionState.Phase.RUNNING) && !previewOrchestratorService.hasLiveLobbyPreviewEntities()) {
            Bukkit.getScheduler().runTask(plugin, refreshLobbyPreview);
        }

        if (sessionState.is(SessionState.Phase.PAUSED)) {
            previewAnchorService.teleportToLobbyIfConfigured(player);
            playerLobbyStateService.applyLobbyInventoryLoadoutIfInLobbyWorld(player);
            log.sendInfo(player, "Challenge is currently paused.");
            return;
        }

        if (!sessionState.is(SessionState.Phase.RUNNING)) {
            previewAnchorService.teleportToLobbyIfConfigured(player);
            restoreDefaultMaxHealth.accept(player);
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> prepAreaService.applyBorder(
                                    player,
                                    sessionState.is(SessionState.Phase.RUNNING),
                                    worldClassificationService::isLobbyOrLimboWorld));
        }

        if (sessionState.is(SessionState.Phase.PREP)) {
            prepBookService.giveIfMissing(player);
            Bukkit.getScheduler().runTask(plugin, refreshOpenPrepGuis);
            return;
        }

        prepBookService.removeFromInventory(player);

        if (sessionState.is(SessionState.Phase.RUNNING)
                && isSharedInventoryEnabled.getAsBoolean()
                && participants.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, syncSharedInventoryFromFirstParticipant);
            Bukkit.getScheduler()
                    .runTask(plugin, () -> sharedInventorySyncService.capturePlayerWearableSnapshot(player));
        }

        if (sessionState.is(SessionState.Phase.RUNNING)
                && challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)
                && participants.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> enforceInventorySlotCap.accept(player));
        }

        if (sessionState.is(SessionState.Phase.RUNNING)
                && challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)
                && participants.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, syncSharedHealthFromFirstParticipant);
            Bukkit.getScheduler().runTask(plugin, syncSharedHungerFromMostFilledParticipant);
        }

        if (sessionState.is(SessionState.Phase.RUNNING)
                && challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)
                && participants.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> applyInitialHalfHeart.accept(player));
        }
    }

    /**
     * Handles player quits and updates readiness/countdown or pause state as
     * needed.
     *
     * @param event quit event for the disconnecting player
     */
    public void handlePlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sharedInventorySyncService.removePlayerWearableSnapshot(playerId);

        if ((sessionState.is(SessionState.Phase.RUNNING) || sessionState.is(SessionState.Phase.PAUSED))
                && participants.contains(playerId)
                && onlineParticipantsSupplier.get().size() <= 1) {
            Bukkit.getScheduler().runTask(plugin, this::stopChallengeIfNoParticipantsOnline);
            return;
        }

        if (sessionState.is(SessionState.Phase.RUNNING) && participants.contains(playerId)) {
            boolean paused = runPauseResumeService.pause(Bukkit.getConsoleSender(), false);
            if (paused) {
                log.warn(event.getPlayer().getName() + " left during the run. Challenge has been paused.");
            }
        }

        if (sessionState.is(SessionState.Phase.COUNTDOWN)) {
            Bukkit.getScheduler()
                    .runTask(plugin, () -> prepReadinessService.cancelCountdownIfNoPlayersOnline(participants));
            return;
        }

        if (sessionState.is(SessionState.Phase.PREP)) {
            readyPlayers.remove(playerId);
            prepGuiCoordinatorService.onPlayerLeft(playerId);
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> prepReadinessService.tryStartCountdown(
                                    readyPlayers,
                                    participants,
                                    isDiscoPreviewBlockingChallengeStart,
                                    announceDiscoPreviewStartBlocked,
                                    startRun));
        }
    }

    private void stopChallengeIfNoParticipantsOnline() {
        if (!sessionState.is(SessionState.Phase.RUNNING) && !sessionState.is(SessionState.Phase.PAUSED)) {
            return;
        }

        if (!onlineParticipantsSupplier.get().isEmpty()) {
            return;
        }

        log.warn("All participants left during the challenge. Stopping challenge and returning to prep.");
        endChallengeAndReturnToPrep.run();
    }

    /**
     * Handles participant death bookkeeping and failure-path transitions.
     *
     * @param event death event for a participant
     */
    public void handlePlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!isChallengeActive.test(player)) {
            return;
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
            clearLockedBarrierSlots.accept(player);
        }

        UUID playerId = player.getUniqueId();
        respawnRoutingService.recordDeathWorld(playerId, player.getWorld());
        boolean hardcore = challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE);
        if (hardcore) {
            eliminatedPlayers.add(playerId);
            sessionFailureService.handleHardcoreFailureIfNeeded();
        } else {
            recentlyDeadPlayers.add(playerId);
            Bukkit.getScheduler().runTaskLater(plugin, sessionFailureService::handleAllPlayersDeadFailureIfNeeded, 1L);
        }
    }

    /**
     * Handles participant respawn routing and post-respawn challenge sync actions.
     *
     * @param event respawn event for a participant
     */
    public void handlePlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (sessionState.is(SessionState.Phase.RUNNING) && participants.contains(playerId)) {
            Location runRespawn = respawnRoutingService.resolveRunRespawnLocation(playerId);
            if (runRespawn != null) {
                event.setRespawnLocation(runRespawn);
            }
        }

        if (!sessionState.is(SessionState.Phase.RUNNING)
                && worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
            Location lobbyRespawn = previewAnchorService.resolveLobbyRespawnLocation(player.getWorld());
            if (lobbyRespawn != null) {
                event.setRespawnLocation(lobbyRespawn);
            }

            if (!sessionState.is(SessionState.Phase.RUNNING)) {
                Bukkit.getScheduler()
                        .runTask(
                                plugin, () -> playerLobbyStateService.applyLobbyInventoryLoadoutIfInLobbyWorld(player));
            }
        }

        if (!isChallengeActive.test(player)) {
            return;
        }

        recentlyDeadPlayers.remove(playerId);
        if (eliminatedPlayers.contains(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!sessionState.is(SessionState.Phase.RUNNING) || !eliminatedPlayers.contains(playerId)) {
                    return;
                }
                player.setGameMode(GameMode.SPECTATOR);
                log.sendWarn(player, "You were eliminated by hardcore mode.");
            });
        }

        if (isSharedInventoryEnabled.getAsBoolean()) {
            Bukkit.getScheduler().runTask(plugin, syncSharedInventoryFromFirstParticipant);
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            Bukkit.getScheduler().runTask(plugin, syncSharedHealthFromFirstParticipant);
            Bukkit.getScheduler().runTask(plugin, syncSharedHungerFromMostFilledParticipant);
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
            Bukkit.getScheduler().runTask(plugin, () -> enforceInventorySlotCap.accept(player));
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)) {
            Bukkit.getScheduler().runTask(plugin, () -> applyInitialHalfHeart.accept(player));
        }
    }

    /**
     * Handles world transitions and updates lobby/run state for the player.
     *
     * @param event world-change event for a participant
     */
    public void handlePlayerChangedWorld(PlayerChangedWorldEvent event) {
        playerLobbyStateService.enforceSurvivalOnWorldEntry(
                event.getPlayer(),
                sessionState.is(SessionState.Phase.RUNNING),
                challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE),
                eliminatedPlayers);
        playerLobbyStateService.applyLobbyInventoryLoadoutIfInLobbyWorld(event.getPlayer());

        if (!sessionState.is(SessionState.Phase.RUNNING)) {
            prepAreaService.applyBorder(
                    event.getPlayer(),
                    sessionState.is(SessionState.Phase.RUNNING),
                    worldClassificationService::isLobbyOrLimboWorld);
            return;
        }

        if (!isChallengeActive.test(event.getPlayer())) {
            return;
        }

        long now = System.currentTimeMillis();
        runStatusService.onParticipantWorldChanged(
                event.getPlayer().getWorld().getEnvironment(),
                onlineParticipantsSupplier.get(),
                now,
                sessionState.is(SessionState.Phase.RUNNING));
    }
}
