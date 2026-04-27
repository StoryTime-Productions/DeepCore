package dev.deepcore.challenge.session;

import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Coordinates prep readiness checks and countdown transitions.
 */
public final class PrepReadinessService {
    private final ChallengeConfigView configView;
    private final SessionState sessionState;
    private final ParticipantsView participantsView;
    private final PrepAreaService prepAreaService;
    private final PrepBookService prepBookService;
    private final PrepCountdownService prepCountdownService;
    private final WorldClassificationService worldClassificationService;
    private final DeepCoreLogger log;

    /**
     * Creates a prep readiness service.
     *
     * @param configView                 typed challenge configuration view
     * @param sessionState               mutable session phase and timing state
     * @param participantsView           online/participant query helper
     * @param prepAreaService            prep area border service
     * @param prepBookService            prep book inventory service
     * @param prepCountdownService       countdown scheduling service
     * @param worldClassificationService world classification helper
     * @param log                        logger used for readiness and countdown
     *                                   messages
     */
    public PrepReadinessService(
            ChallengeConfigView configView,
            SessionState sessionState,
            ParticipantsView participantsView,
            PrepAreaService prepAreaService,
            PrepBookService prepBookService,
            PrepCountdownService prepCountdownService,
            WorldClassificationService worldClassificationService,
            DeepCoreLogger log) {
        this.configView = configView;
        this.sessionState = sessionState;
        this.participantsView = participantsView;
        this.prepAreaService = prepAreaService;
        this.prepBookService = prepBookService;
        this.prepCountdownService = prepCountdownService;
        this.worldClassificationService = worldClassificationService;
        this.log = log;
    }

    /**
     * Attempts to start the prep countdown when readiness conditions are met.
     *
     * @param readyPlayers                UUIDs currently marked ready
     * @param participants                mutable participant UUID set for the
     *                                    upcoming run
     * @param discoPreviewBlocking        supplier indicating whether disco preview
     *                                    blocks countdown
     * @param announceDiscoPreviewBlocked action that announces disco preview
     *                                    blocking
     * @param startRun                    action that starts the run after countdown
     *                                    completion
     */
    public void tryStartCountdown(
            Set<UUID> readyPlayers,
            Set<UUID> participants,
            BooleanSupplier discoPreviewBlocking,
            Runnable announceDiscoPreviewBlocked,
            Runnable startRun) {
        if (!sessionState.is(SessionState.Phase.PREP)) {
            return;
        }

        if (discoPreviewBlocking.getAsBoolean()) {
            announceDiscoPreviewBlocked.run();
            return;
        }

        Set<UUID> online = participantsView.onlinePlayerIds();
        boolean requireAllReady = configView.countdownRequiresAllReady();
        boolean canStart = requireAllReady ? !online.isEmpty() && readyPlayers.containsAll(online) : !online.isEmpty();
        if (!canStart) {
            return;
        }

        sessionState.setPhase(SessionState.Phase.COUNTDOWN);
        participants.clear();
        participants.addAll(online);

        String countdownMessage = requireAllReady
                ? ChatColor.GOLD + "All players are ready. Countdown started and cannot be canceled."
                : ChatColor.GOLD + "Countdown started.";
        String plainCountdownMessage = ChatColor.stripColor(countdownMessage);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.closeInventory();
            log.sendInfo(player, plainCountdownMessage);
            if (configView.removeReadyBookOnCountdownStart()) {
                prepBookService.removeFromInventory(player);
            }
        }

        prepAreaService.applyBordersToOnlinePlayers(
                sessionState.is(SessionState.Phase.RUNNING), worldClassificationService::isPrepBorderExemptWorld);

        startCountdown(participants, startRun);
    }

    /**
     * Cancels an active countdown when everyone has disconnected.
     *
     * @param participants mutable participant UUID set for the pending run
     */
    public void cancelCountdownIfNoPlayersOnline(Set<UUID> participants) {
        if (!sessionState.is(SessionState.Phase.COUNTDOWN)
                || !Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        prepCountdownService.cancel();
        sessionState.setPhase(SessionState.Phase.PREP);
        participants.clear();
        log.info("Countdown canceled - players left.");
    }

    private void startCountdown(Set<UUID> participants, Runnable startRun) {
        int countdownSeconds = Math.max(3, configView.prepCountdownSeconds());
        prepCountdownService.startCountdown(
                countdownSeconds,
                () -> !sessionState.is(SessionState.Phase.COUNTDOWN)
                        || Bukkit.getOnlinePlayers().isEmpty(),
                () -> cancelCountdownIfNoPlayersOnline(participants),
                startRun,
                secondsLeft -> log.info("Run starts in " + secondsLeft + "..."));
    }
}
