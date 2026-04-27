package dev.deepcore.challenge.session;

import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Coordinates dragon-kill completion flow, run record persistence, and return
 * countdown.
 */
public final class RunCompletionService {
    private final SessionState sessionState;
    private final RunProgressService runProgressService;
    private final CompletionReturnService completionReturnService;
    private final Supplier<dev.deepcore.records.RunRecordsService> recordsServiceSupplier;
    private final Set<UUID> participants;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final Runnable endChallengeAndReturnToPrep;
    private final DeepCoreLogger log;

    /**
     * Creates a run completion service.
     *
     * @param sessionState                mutable session phase/timing state
     * @param runProgressService          run progress and split tracking service
     * @param completionReturnService     completion return countdown service
     * @param recordsServiceSupplier      supplier for records persistence service
     * @param participants                participant UUID set for current run
     * @param worldResetManagerSupplier   supplier for world reset manager
     * @param endChallengeAndReturnToPrep fallback action to return session to prep
     * @param log                         logger for completion lifecycle messages
     */
    public RunCompletionService(
            SessionState sessionState,
            RunProgressService runProgressService,
            CompletionReturnService completionReturnService,
            Supplier<dev.deepcore.records.RunRecordsService> recordsServiceSupplier,
            Set<UUID> participants,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            Runnable endChallengeAndReturnToPrep,
            DeepCoreLogger log) {
        this.sessionState = sessionState;
        this.runProgressService = runProgressService;
        this.completionReturnService = completionReturnService;
        this.recordsServiceSupplier = recordsServiceSupplier;
        this.participants = participants;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.endChallengeAndReturnToPrep = endChallengeAndReturnToPrep;
        this.log = log;
    }

    /**
     * Handles entity death events and triggers completion flow on dragon kill.
     *
     * @param event entity death event to inspect
     */
    public void handleEntityDeath(EntityDeathEvent event) {
        if (!sessionState.is(SessionState.Phase.RUNNING)) {
            return;
        }

        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        long dragonDeathTime = System.currentTimeMillis();
        runProgressService.markDragonKilled(dragonDeathTime);
        log.info("Victory! Ender Dragon defeated!");

        recordRunIfAvailable(dragonDeathTime);
        startCompletionReturnCountdown();
    }

    private void recordRunIfAvailable(long dragonDeathTime) {
        dev.deepcore.records.RunRecordsService recordsService = recordsServiceSupplier.get();
        if (recordsService == null || sessionState.timing().getRunStartMillis() <= 0L) {
            return;
        }

        long overallTimeMs = dragonDeathTime - sessionState.timing().getRunStartMillis();
        RunProgressService.SectionDurations sectionDurations = runProgressService.calculateSectionDurations(
                sessionState.timing().getRunStartMillis(), dragonDeathTime, overallTimeMs);

        recordsService.recordRun(
                overallTimeMs,
                sectionDurations.overworldToNetherMs(),
                sectionDurations.netherToBlazeRodsMs(),
                sectionDurations.blazeRodsToEndMs(),
                sectionDurations.netherToEndMs(),
                sectionDurations.endToDragonMs(),
                getParticipantNamesForRecord());
    }

    private void startCompletionReturnCountdown() {
        completionReturnService.start(
                10,
                () -> sessionState.is(SessionState.Phase.RUNNING) && runProgressService.isDragonKilled(),
                this::completeDragonWinFlow,
                secondsLeft -> log.info("Lobby in " + secondsLeft + "s..."));
    }

    private void completeDragonWinFlow() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null) {
            worldResetManager.resetThreeWorlds(Bukkit.getConsoleSender());
        } else {
            endChallengeAndReturnToPrep.run();
        }
    }

    private List<String> getParticipantNamesForRecord() {
        List<String> names = new ArrayList<>();
        for (UUID participantId : participants) {
            Player online = Bukkit.getPlayer(participantId);
            if (online != null) {
                names.add(online.getName());
                continue;
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayer(participantId);
            if (offline.getName() != null && !offline.getName().isBlank()) {
                names.add(offline.getName());
            }
        }
        Collections.sort(names);
        return names;
    }
}
