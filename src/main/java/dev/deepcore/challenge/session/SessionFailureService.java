package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Evaluates run-failure conditions and performs reset flow when triggered.
 */
public final class SessionFailureService {
    private final SessionState sessionState;
    private final ChallengeManager challengeManager;
    private final Set<UUID> participants;
    private final Set<UUID> eliminatedPlayers;
    private final Set<UUID> recentlyDeadPlayers;
    private final ActionBarTickerService actionBarTickerService;
    private final Runnable clearActionBar;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final DeepCoreLogger log;

    /**
     * Creates a session failure service.
     *
     * @param sessionState              mutable session phase/state container
     * @param challengeManager          challenge settings and component manager
     * @param participants              active run participants
     * @param eliminatedPlayers         hardcore-eliminated participants
     * @param recentlyDeadPlayers       recently dead participants for failure
     *                                  checks
     * @param actionBarTickerService    action-bar ticker service
     * @param clearActionBar            runnable clearing action bar text for
     *                                  participants
     * @param worldResetManagerSupplier supplier for current world reset manager
     * @param log                       challenge logger for player/admin messaging
     */
    public SessionFailureService(
            SessionState sessionState,
            ChallengeManager challengeManager,
            Set<UUID> participants,
            Set<UUID> eliminatedPlayers,
            Set<UUID> recentlyDeadPlayers,
            ActionBarTickerService actionBarTickerService,
            Runnable clearActionBar,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            DeepCoreLogger log) {
        this.sessionState = sessionState;
        this.challengeManager = challengeManager;
        this.participants = participants;
        this.eliminatedPlayers = eliminatedPlayers;
        this.recentlyDeadPlayers = recentlyDeadPlayers;
        this.actionBarTickerService = actionBarTickerService;
        this.clearActionBar = clearActionBar;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.log = log;
    }

    /** Evaluates and handles hardcore-mode failure reset conditions. */
    public void handleHardcoreFailureIfNeeded() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (!sessionState.is(SessionState.Phase.RUNNING) || worldResetManager == null) {
            return;
        }

        if (!challengeManager.isEnabled() || !challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)) {
            return;
        }

        if (participants.isEmpty() || eliminatedPlayers.isEmpty()) {
            return;
        }

        triggerFailureReset(worldResetManager, ChatColor.YELLOW + "Resetting worlds...", false);
    }

    /** Evaluates and handles all-players-dead failure reset conditions. */
    public void handleAllPlayersDeadFailureIfNeeded() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (!sessionState.is(SessionState.Phase.RUNNING) || worldResetManager == null) {
            return;
        }

        if (!challengeManager.isEnabled() || challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)) {
            return;
        }

        if (participants.isEmpty() || recentlyDeadPlayers.isEmpty()) {
            return;
        }

        if (!recentlyDeadPlayers.containsAll(participants)) {
            return;
        }

        triggerFailureReset(worldResetManager, ChatColor.YELLOW + "All players are dead!", true);
    }

    private void triggerFailureReset(WorldResetManager worldResetManager, String subtitle, boolean allDeadMessage) {
        actionBarTickerService.stop();
        clearActionBar.run();

        log.warn("====================");
        log.warn("Challenge Failed");
        if (allDeadMessage) {
            log.info("All players are dead!");
        }
        log.warn("Resetting worlds...");
        log.warn("====================");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(ChatColor.RED + "Challenge Failed", subtitle, 10, 70, 20);
        }

        worldResetManager.resetThreeWorlds(Bukkit.getConsoleSender());
    }
}
