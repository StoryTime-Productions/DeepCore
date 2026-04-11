package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Schedules and applies degrading-inventory slot reductions during active runs.
 */
public final class DegradingInventoryTickerService {
    private final JavaPlugin plugin;
    private final TaskGroup taskGroup;
    private final String degradingTaskKey;
    private final SessionState sessionState;
    private final ChallengeManager challengeManager;
    private final ChallengeConfigView configView;
    private final DegradingInventoryService degradingInventoryService;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Consumer<Player> enforceInventorySlotCap;
    private final DeepCoreLogger log;

    /**
     * Creates a degrading inventory ticker service.
     *
     * @param plugin                     plugin scheduler and lifecycle owner
     * @param taskGroup                  grouped task lifecycle manager
     * @param degradingTaskKey           task key for degrading-inventory ticker
     * @param sessionState               mutable session phase/state container
     * @param challengeManager           challenge settings and component manager
     * @param configView                 challenge config value accessor
     * @param degradingInventoryService  degrading-inventory state service
     * @param onlineParticipantsSupplier supplier for currently online participants
     * @param enforceInventorySlotCap    consumer applying inventory slot limits
     * @param log                        challenge logger for player/admin messaging
     */
    public DegradingInventoryTickerService(
            JavaPlugin plugin,
            TaskGroup taskGroup,
            String degradingTaskKey,
            SessionState sessionState,
            ChallengeManager challengeManager,
            ChallengeConfigView configView,
            DegradingInventoryService degradingInventoryService,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Consumer<Player> enforceInventorySlotCap,
            DeepCoreLogger log) {
        this.plugin = plugin;
        this.taskGroup = taskGroup;
        this.degradingTaskKey = degradingTaskKey;
        this.sessionState = sessionState;
        this.challengeManager = challengeManager;
        this.configView = configView;
        this.degradingInventoryService = degradingInventoryService;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.enforceInventorySlotCap = enforceInventorySlotCap;
        this.log = log;
    }

    /**
     * Starts periodic degrading-inventory slot reduction for active runs.
     *
     * @param resetSlots true to reset allowed slot count before starting ticker
     */
    public void start(boolean resetSlots) {
        if (resetSlots) {
            degradingInventoryService.resetAllowedInventorySlots();
        }

        int intervalSeconds = Math.max(10, configView.degradingIntervalSeconds());
        int minSlots = Math.max(1, Math.min(35, configView.degradingMinSlots()));

        taskGroup.replace(
                degradingTaskKey,
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!sessionState.is(SessionState.Phase.RUNNING)
                                            || !challengeManager.isComponentEnabled(
                                                    ChallengeComponent.DEGRADING_INVENTORY)) {
                                        taskGroup.cancel(degradingTaskKey);
                                        return;
                                    }

                                    if (!degradingInventoryService.reduceAllowedInventorySlots(minSlots)) {
                                        return;
                                    }

                                    for (Player player : onlineParticipantsSupplier.get()) {
                                        enforceInventorySlotCap.accept(player);
                                        log.sendWarn(
                                                player,
                                                "Inventory capacity reduced to "
                                                        + degradingInventoryService.getAllowedInventorySlots()
                                                        + " slots.");
                                    }
                                },
                                intervalSeconds * 20L,
                                intervalSeconds * 20L));
    }
}
