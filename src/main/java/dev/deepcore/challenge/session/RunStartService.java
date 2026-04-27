package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates run start transitions and participant preparation.
 */
public final class RunStartService {
    private final JavaPlugin plugin;
    private final SessionState sessionState;
    private final ChallengeManager challengeManager;
    private final Set<UUID> participants;
    private final Set<UUID> readyPlayers;
    private final PrepAreaService prepAreaService;
    private final WorldClassificationService worldClassificationService;
    private final PreviewOrchestratorService previewOrchestratorService;
    private final RunProgressService runProgressService;
    private final RunStatusService runStatusService;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final PrepBookService prepBookService;
    private final Consumer<Player> clearLobbySidebar;
    private final Runnable clearPausedSnapshots;
    private final Runnable syncWorldRules;
    private final Runnable startActionBarTask;
    private final Runnable snapshotEquippedWearablesForParticipants;
    private final Consumer<Player> enforceInventorySlotCap;
    private final Runnable startDegradingInventoryTaskWithReset;
    private final BooleanSupplier isSharedInventoryEnabled;
    private final Runnable syncSharedInventoryFromFirstParticipant;
    private final Runnable syncSharedHealthFromFirstParticipant;
    private final Runnable syncSharedHungerFromMostFilledParticipant;
    private final Runnable applyInitialHalfHeartIfEnabled;
    private final BooleanSupplier isDiscoPreviewBlockingChallengeStart;
    private final Runnable announceDiscoPreviewStartBlocked;
    private final Runnable refreshOpenPrepGuis;
    private final DeepCoreLogger log;

    /**
     * Creates a run start service.
     *
     * @param plugin                                    plugin scheduler and
     *                                                  lifecycle owner
     * @param sessionState                              mutable session phase/state
     *                                                  container
     * @param challengeManager                          challenge settings and
     *                                                  component manager
     * @param participants                              active run participants
     * @param readyPlayers                              players marked ready during
     *                                                  prep
     * @param prepAreaService                           prep area border and region
     *                                                  service
     * @param worldClassificationService                world classification helper
     *                                                  service
     * @param previewOrchestratorService                lobby preview orchestration
     *                                                  service
     * @param runProgressService                        run milestone progress
     *                                                  tracker
     * @param runStatusService                          run status/timing announcer
     *                                                  service
     * @param onlineParticipantsSupplier                supplier for currently
     *                                                  online participants
     * @param worldResetManagerSupplier                 supplier for current world
     *                                                  reset manager
     * @param prepBookService                           prep guide book service
     * @param clearLobbySidebar                         consumer clearing a player's
     *                                                  lobby sidebar
     * @param clearPausedSnapshots                      runnable clearing paused run
     *                                                  snapshots
     * @param syncWorldRules                            runnable that reapplies
     *                                                  world rule policies
     * @param startActionBarTask                        runnable starting action-bar
     *                                                  updates
     * @param snapshotEquippedWearablesForParticipants  runnable snapshotting
     *                                                  wearables
     * @param enforceInventorySlotCap                   consumer enforcing inventory
     *                                                  slot limits
     * @param startDegradingInventoryTaskWithReset      runnable starting degrading
     *                                                  inventory task
     * @param isSharedInventoryEnabled                  supplier indicating shared
     *                                                  inventory mode
     * @param syncSharedInventoryFromFirstParticipant   runnable syncing shared
     *                                                  inventory
     * @param syncSharedHealthFromFirstParticipant      runnable syncing shared
     *                                                  health
     * @param syncSharedHungerFromMostFilledParticipant runnable syncing shared
     *                                                  hunger
     * @param applyInitialHalfHeartIfEnabled            runnable applying half-heart
     *                                                  mode if enabled
     * @param isDiscoPreviewBlockingChallengeStart      supplier for disco-start
     *                                                  blocking state
     * @param announceDiscoPreviewStartBlocked          runnable announcing disco
     *                                                  start block
     * @param refreshOpenPrepGuis                       runnable refreshing prep
     *                                                  GUIs
     * @param log                                       challenge logger for
     *                                                  player/admin messaging
     */
    public RunStartService(
            JavaPlugin plugin,
            SessionState sessionState,
            ChallengeManager challengeManager,
            Set<UUID> participants,
            Set<UUID> readyPlayers,
            PrepAreaService prepAreaService,
            WorldClassificationService worldClassificationService,
            PreviewOrchestratorService previewOrchestratorService,
            RunProgressService runProgressService,
            RunStatusService runStatusService,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            PrepBookService prepBookService,
            Consumer<Player> clearLobbySidebar,
            Runnable clearPausedSnapshots,
            Runnable syncWorldRules,
            Runnable startActionBarTask,
            Runnable snapshotEquippedWearablesForParticipants,
            Consumer<Player> enforceInventorySlotCap,
            Runnable startDegradingInventoryTaskWithReset,
            BooleanSupplier isSharedInventoryEnabled,
            Runnable syncSharedInventoryFromFirstParticipant,
            Runnable syncSharedHealthFromFirstParticipant,
            Runnable syncSharedHungerFromMostFilledParticipant,
            Runnable applyInitialHalfHeartIfEnabled,
            BooleanSupplier isDiscoPreviewBlockingChallengeStart,
            Runnable announceDiscoPreviewStartBlocked,
            Runnable refreshOpenPrepGuis,
            DeepCoreLogger log) {
        this.plugin = plugin;
        this.sessionState = sessionState;
        this.challengeManager = challengeManager;
        this.participants = participants;
        this.readyPlayers = readyPlayers;
        this.prepAreaService = prepAreaService;
        this.worldClassificationService = worldClassificationService;
        this.previewOrchestratorService = previewOrchestratorService;
        this.runProgressService = runProgressService;
        this.runStatusService = runStatusService;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.prepBookService = prepBookService;
        this.clearLobbySidebar = clearLobbySidebar;
        this.clearPausedSnapshots = clearPausedSnapshots;
        this.syncWorldRules = syncWorldRules;
        this.startActionBarTask = startActionBarTask;
        this.snapshotEquippedWearablesForParticipants = snapshotEquippedWearablesForParticipants;
        this.enforceInventorySlotCap = enforceInventorySlotCap;
        this.startDegradingInventoryTaskWithReset = startDegradingInventoryTaskWithReset;
        this.isSharedInventoryEnabled = isSharedInventoryEnabled;
        this.syncSharedInventoryFromFirstParticipant = syncSharedInventoryFromFirstParticipant;
        this.syncSharedHealthFromFirstParticipant = syncSharedHealthFromFirstParticipant;
        this.syncSharedHungerFromMostFilledParticipant = syncSharedHungerFromMostFilledParticipant;
        this.applyInitialHalfHeartIfEnabled = applyInitialHalfHeartIfEnabled;
        this.isDiscoPreviewBlockingChallengeStart = isDiscoPreviewBlockingChallengeStart;
        this.announceDiscoPreviewStartBlocked = announceDiscoPreviewStartBlocked;
        this.refreshOpenPrepGuis = refreshOpenPrepGuis;
        this.log = log;
    }

    /**
     * Starts a challenge run after validating preconditions and preparing players.
     */
    public void startRun() {
        if (!validateRunStartPreconditions()) {
            return;
        }

        transitionToRunningPhase();
        prepareParticipantsForRun();
        launchRunTasksAndSync();

        log.info("Run started!");
    }

    private boolean validateRunStartPreconditions() {
        if (!isDiscoPreviewBlockingChallengeStart.getAsBoolean()) {
            return true;
        }

        sessionState.setPhase(SessionState.Phase.PREP);
        participants.clear();
        announceDiscoPreviewStartBlocked.run();
        prepAreaService.applyBordersToOnlinePlayers(
                sessionState.is(SessionState.Phase.RUNNING), worldClassificationService::isPrepBorderExemptWorld);
        Bukkit.getScheduler().runTask(plugin, refreshOpenPrepGuis);
        return false;
    }

    private void transitionToRunningPhase() {
        if (!challengeManager.isEnabled()) {
            challengeManager.setEnabled(true);
        }

        sessionState.setPhase(SessionState.Phase.RUNNING);
        sessionState.timing().beginRun(System.currentTimeMillis());
        clearPausedSnapshots.run();
        readyPlayers.clear();
        syncWorldRules.run();
        prepAreaService.clearBorders();
        previewOrchestratorService.clearLobbyPreviewEntities();
        runProgressService.reset();
        runStatusService.reset();
    }

    private void prepareParticipantsForRun() {
        teleportParticipantsToRunWorld();
        startActionBarTask.run();

        for (Player player : Bukkit.getOnlinePlayers()) {
            prepBookService.removeFromInventory(player);
            clearLobbySidebar.accept(player);
        }

        snapshotEquippedWearablesForParticipants.run();
    }

    private void launchRunTasksAndSync() {
        if (challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
            for (Player participant : onlineParticipantsSupplier.get()) {
                enforceInventorySlotCap.accept(participant);
            }
            startDegradingInventoryTaskWithReset.run();
        }

        if (isSharedInventoryEnabled.getAsBoolean()) {
            syncSharedInventoryFromFirstParticipant.run();
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            syncSharedHealthFromFirstParticipant.run();
            syncSharedHungerFromMostFilledParticipant.run();
        }

        applyInitialHalfHeartIfEnabled.run();
    }

    private void teleportParticipantsToRunWorld() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return;
        }

        org.bukkit.World runWorld = worldResetManager.getCurrentOverworld();
        if (runWorld == null) {
            log.warn("Could not resolve run overworld at run start; skipping participant teleport.");
            return;
        }

        Location targetSpawn = runWorld.getSpawnLocation().clone().add(0.5D, 1.0D, 0.5D);
        for (Player participant : onlineParticipantsSupplier.get()) {
            participant.teleport(targetSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }
}
