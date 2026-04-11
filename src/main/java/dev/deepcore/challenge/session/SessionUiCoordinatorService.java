package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ui.LobbySidebarCoordinatorService;
import dev.deepcore.challenge.ui.LobbySidebarService;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates run action-bar updates and lobby sidebar refresh scheduling.
 */
public final class SessionUiCoordinatorService {
    private final JavaPlugin plugin;
    private final TaskGroup taskGroup;
    private final String lobbySidebarTaskKey;
    private final ActionBarTickerService actionBarTickerService;
    private final RunStatusService runStatusService;
    private final SessionState sessionState;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final ParticipantsView participantsView;
    private final SidebarModelFactory sidebarModelFactory;
    private final Supplier<dev.deepcore.records.RunRecordsService> recordsServiceSupplier;
    private final IntSupplier readyCountSupplier;
    private final LobbySidebarCoordinatorService lobbySidebarCoordinatorService;
    private final LobbySidebarService lobbySidebarService;

    /**
     * Creates a session UI coordinator service.
     *
     * @param plugin                         plugin scheduler and lifecycle owner
     * @param taskGroup                      grouped task lifecycle manager
     * @param lobbySidebarTaskKey            task key for lobby sidebar updates
     * @param actionBarTickerService         action-bar ticker service
     * @param runStatusService               run status/timing announcer service
     * @param sessionState                   mutable session phase/state container
     * @param onlineParticipantsSupplier     supplier for currently online
     *                                       participants
     * @param participantsView               participant roster and counts view
     * @param sidebarModelFactory            sidebar model builder
     * @param recordsServiceSupplier         supplier for run records service
     * @param readyCountSupplier             supplier for current ready player count
     * @param lobbySidebarCoordinatorService sidebar refresh coordinator service
     * @param lobbySidebarService            sidebar rendering/clear service
     */
    public SessionUiCoordinatorService(
            JavaPlugin plugin,
            TaskGroup taskGroup,
            String lobbySidebarTaskKey,
            ActionBarTickerService actionBarTickerService,
            RunStatusService runStatusService,
            SessionState sessionState,
            Supplier<List<Player>> onlineParticipantsSupplier,
            ParticipantsView participantsView,
            SidebarModelFactory sidebarModelFactory,
            Supplier<dev.deepcore.records.RunRecordsService> recordsServiceSupplier,
            IntSupplier readyCountSupplier,
            LobbySidebarCoordinatorService lobbySidebarCoordinatorService,
            LobbySidebarService lobbySidebarService) {
        this.plugin = plugin;
        this.taskGroup = taskGroup;
        this.lobbySidebarTaskKey = lobbySidebarTaskKey;
        this.actionBarTickerService = actionBarTickerService;
        this.runStatusService = runStatusService;
        this.sessionState = sessionState;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.participantsView = participantsView;
        this.sidebarModelFactory = sidebarModelFactory;
        this.recordsServiceSupplier = recordsServiceSupplier;
        this.readyCountSupplier = readyCountSupplier;
        this.lobbySidebarCoordinatorService = lobbySidebarCoordinatorService;
        this.lobbySidebarService = lobbySidebarService;
    }

    /** Starts the action-bar ticker for live run status updates. */
    public void startActionBarTask() {
        actionBarTickerService.start(
                () -> sessionState.is(SessionState.Phase.RUNNING),
                this::syncRunStatusFromParticipants,
                this::buildRunActionBarMessage,
                onlineParticipantsSupplier);
    }

    /** Starts periodic lobby sidebar refresh scheduling. */
    public void startLobbySidebarTask() {
        taskGroup.replace(
                lobbySidebarTaskKey, Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLobbySidebars, 0L, 40L));
    }

    /**
     * Clears the lobby sidebar for a specific player.
     *
     * @param player player whose lobby sidebar should be cleared
     */
    public void clearLobbySidebar(Player player) {
        lobbySidebarService.clearLobbySidebar(player);
    }

    private void syncRunStatusFromParticipants() {
        List<Player> onlineParticipants = onlineParticipantsSupplier.get();
        long now = System.currentTimeMillis();
        runStatusService.tickProgressFromParticipants(
                onlineParticipants, now, sessionState.is(SessionState.Phase.RUNNING));
    }

    private Component buildRunActionBarMessage() {
        return runStatusService.buildRunActionBarMessage(
                sessionState.timing().getRunStartMillis(),
                sessionState.timing().getAccumulatedPausedMillis(),
                sessionState.is(SessionState.Phase.PAUSED),
                sessionState.timing().getPausedStartedMillis());
    }

    private void refreshLobbySidebars() {
        int onlineCount = participantsView.onlineCount();
        SidebarModel sidebarModel = sidebarModelFactory.create(
                recordsServiceSupplier.get(), sessionState.getPhase(), readyCountSupplier.getAsInt(), onlineCount);
        lobbySidebarCoordinatorService.refreshForOnlinePlayers(Bukkit.getOnlinePlayers(), sidebarModel);
    }
}
