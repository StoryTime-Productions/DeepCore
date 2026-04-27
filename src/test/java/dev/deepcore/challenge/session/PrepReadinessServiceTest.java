package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PrepReadinessServiceTest {

    @Test
    void tryStartCountdown_returnsImmediatelyWhenNotInPrepPhase() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);
        ParticipantsView participantsView = mock(ParticipantsView.class);
        PrepAreaService prepAreaService = mock(PrepAreaService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PrepCountdownService prepCountdownService = mock(PrepCountdownService.class);
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        PrepReadinessService service = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);

        Set<UUID> readyPlayers = new HashSet<>();
        Set<UUID> participants = new HashSet<>();
        Runnable announceBlocked = mock(Runnable.class);
        Runnable startRun = mock(Runnable.class);

        service.tryStartCountdown(readyPlayers, participants, () -> false, announceBlocked, startRun);

        verify(announceBlocked, never()).run();
        verify(startRun, never()).run();
        verify(prepCountdownService, never())
                .startCountdown(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tryStartCountdown_announcesWhenDiscoPreviewBlocks() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.PREP);
        ParticipantsView participantsView = mock(ParticipantsView.class);
        PrepAreaService prepAreaService = mock(PrepAreaService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PrepCountdownService prepCountdownService = mock(PrepCountdownService.class);
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        PrepReadinessService service = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);

        Set<UUID> readyPlayers = new HashSet<>();
        Set<UUID> participants = new HashSet<>();
        Runnable announceBlocked = mock(Runnable.class);
        Runnable startRun = mock(Runnable.class);

        service.tryStartCountdown(readyPlayers, participants, () -> true, announceBlocked, startRun);

        verify(announceBlocked).run();
        verify(startRun, never()).run();
        verify(prepCountdownService, never())
                .startCountdown(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelCountdownIfNoPlayersOnline_returnsWhenNotInCountdownPhase() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.PREP);
        ParticipantsView participantsView = mock(ParticipantsView.class);
        PrepAreaService prepAreaService = mock(PrepAreaService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PrepCountdownService prepCountdownService = mock(PrepCountdownService.class);
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        PrepReadinessService service = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);

        Set<UUID> participants = new HashSet<>();
        participants.add(UUID.randomUUID());

        service.cancelCountdownIfNoPlayersOnline(participants);

        verify(prepCountdownService, never()).cancel();
    }

    @Test
    void tryStartCountdown_allReady_startsCountdownAndRemovesBooks() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.PREP);
        ParticipantsView participantsView = mock(ParticipantsView.class);
        PrepAreaService prepAreaService = mock(PrepAreaService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PrepCountdownService prepCountdownService = mock(PrepCountdownService.class);
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        PrepReadinessService service = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);

        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();
        Set<UUID> readyPlayers = new HashSet<>(Set.of(p1Id, p2Id));
        Set<UUID> participants = new HashSet<>();

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);

        when(participantsView.onlinePlayerIds()).thenReturn(Set.of(p1Id, p2Id));
        when(configView.countdownRequiresAllReady()).thenReturn(true);
        when(configView.removeReadyBookOnCountdownStart()).thenReturn(true);
        when(configView.prepCountdownSeconds()).thenReturn(5);

        Runnable announceBlocked = mock(Runnable.class);
        Runnable startRun = mock(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            service.tryStartCountdown(readyPlayers, participants, () -> false, announceBlocked, startRun);
        }

        org.junit.jupiter.api.Assertions.assertEquals(SessionState.Phase.COUNTDOWN, sessionState.getPhase());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(p1Id, p2Id), participants);
        verify(p1).closeInventory();
        verify(p2).closeInventory();
        verify(prepBookService).removeFromInventory(p1);
        verify(prepBookService).removeFromInventory(p2);
        verify(prepAreaService).applyBordersToOnlinePlayers(eq(false), any());
        verify(prepCountdownService).startCountdown(eq(5), any(), any(), eq(startRun), any());
        verify(announceBlocked, never()).run();
    }

    @Test
    void cancelCountdownIfNoPlayersOnline_resetsToPrepAndCancelsTimer() {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.COUNTDOWN);
        ParticipantsView participantsView = mock(ParticipantsView.class);
        PrepAreaService prepAreaService = mock(PrepAreaService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PrepCountdownService prepCountdownService = mock(PrepCountdownService.class);
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        PrepReadinessService service = new PrepReadinessService(
                configView,
                sessionState,
                participantsView,
                prepAreaService,
                prepBookService,
                prepCountdownService,
                worldClassificationService,
                log);

        Set<UUID> participants = new HashSet<>(Set.of(UUID.randomUUID()));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            service.cancelCountdownIfNoPlayersOnline(participants);
        }

        org.junit.jupiter.api.Assertions.assertEquals(SessionState.Phase.PREP, sessionState.getPhase());
        org.junit.jupiter.api.Assertions.assertTrue(participants.isEmpty());
        verify(prepCountdownService).cancel();
        verify(log).info("Countdown canceled - players left.");
    }
}
