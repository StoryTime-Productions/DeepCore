package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class RunPauseResumeServiceTest {

    @Test
    void pause_returnsFalseWhenSessionIsNotRunning() {
        SessionState state = new SessionState();
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        RunPauseResumeService service = newService(
                state,
                () -> mock(WorldResetManager.class),
                log,
                () -> List.of(),
                () -> {},
                player -> {},
                mock(PausedRunStateService.class),
                mock(ActionBarTickerService.class),
                new TaskGroup(),
                "degrading",
                () -> {},
                mock(PrepAreaService.class),
                mock(ChallengeManager.class),
                () -> {},
                () -> {});

        boolean paused = service.pause(mock(CommandSender.class), true);

        assertFalse(paused);
        verify(log, never()).sendError(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void pause_returnsFalseWhenWorldResetManagerIsUnavailable() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        RunPauseResumeService service = newService(
                state,
                () -> null,
                log,
                () -> List.of(),
                () -> {},
                player -> {},
                mock(PausedRunStateService.class),
                mock(ActionBarTickerService.class),
                new TaskGroup(),
                "degrading",
                () -> {},
                mock(PrepAreaService.class),
                mock(ChallengeManager.class),
                () -> {},
                () -> {});

        boolean paused = service.pause(mock(CommandSender.class), false);

        assertFalse(paused);
        verify(log)
                .sendError(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.contains("world reset manager"));
    }

    @Test
    void pause_returnsFalseWhenLobbySpawnIsUnavailable() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        when(worldResetManager.getConfiguredLimboSpawn()).thenReturn(null);

        RunPauseResumeService service = newService(
                state,
                () -> worldResetManager,
                log,
                () -> List.of(),
                () -> {},
                player -> {},
                mock(PausedRunStateService.class),
                mock(ActionBarTickerService.class),
                new TaskGroup(),
                "degrading",
                () -> {},
                mock(PrepAreaService.class),
                mock(ChallengeManager.class),
                () -> {},
                () -> {});

        boolean paused = service.pause(mock(CommandSender.class), false);

        assertFalse(paused);
        verify(log).sendError(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.contains("lobby spawn"));
    }

    @Test
    void pause_snapshotsPlayersTransitionsPhaseAndCancelsDegradingTask() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        DeepCoreLogger log = mock(DeepCoreLogger.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        when(worldResetManager.getConfiguredLimboSpawn()).thenReturn(mock(Location.class));

        Player first = mock(Player.class);
        Player second = mock(Player.class);
        List<Player> participants = List.of(first, second);

        Runnable clearPausedSnapshots = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> stashRunStateForLobby = mock(Consumer.class);
        PausedRunStateService pausedState = mock(PausedRunStateService.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        Runnable clearActionBar = mock(Runnable.class);
        PrepAreaService prepArea = mock(PrepAreaService.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);

        TaskGroup taskGroup = new TaskGroup();
        BukkitTask degradingTask = mock(BukkitTask.class);
        taskGroup.replace("degrading", degradingTask);

        RunPauseResumeService service = newService(
                state,
                () -> worldResetManager,
                log,
                () -> participants,
                clearPausedSnapshots,
                stashRunStateForLobby,
                pausedState,
                actionBar,
                taskGroup,
                "degrading",
                clearActionBar,
                prepArea,
                challengeManager,
                mock(Runnable.class),
                mock(Runnable.class));

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("admin");

        boolean paused = service.pause(sender, true);

        assertTrue(paused);
        assertTrue(state.is(SessionState.Phase.PAUSED));
        verify(clearPausedSnapshots).run();
        verify(stashRunStateForLobby).accept(first);
        verify(stashRunStateForLobby).accept(second);
        verify(log).sendInfo(first, "Challenge paused. Your run state has been snapshotted.");
        verify(log).sendInfo(second, "Challenge paused. Your run state has been snapshotted.");
        verify(actionBar).stop();
        verify(degradingTask).cancel();
        verify(clearActionBar).run();
        verify(prepArea).clearBorders();
        verify(log).info("Challenge paused by admin.");
    }

    @Test
    void resume_returnsFalseWhenSessionIsNotPaused() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        RunPauseResumeService service = newService(
                state,
                () -> mock(WorldResetManager.class),
                mock(DeepCoreLogger.class),
                List::of,
                mock(Runnable.class),
                player -> {},
                mock(PausedRunStateService.class),
                mock(ActionBarTickerService.class),
                new TaskGroup(),
                "degrading",
                mock(Runnable.class),
                mock(PrepAreaService.class),
                mock(ChallengeManager.class),
                mock(Runnable.class),
                mock(Runnable.class));

        boolean resumed = service.resume(mock(CommandSender.class));

        assertFalse(resumed);
    }

    @Test
    void resume_restoresSnapshotsAndRestartsTasksWithComponentGate() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PAUSED);

        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player player = mock(Player.class);
        List<Player> participants = List.of(player);
        Runnable clearPausedSnapshots = mock(Runnable.class);
        PausedRunStateService pausedState = mock(PausedRunStateService.class);
        Runnable startActionBarTask = mock(Runnable.class);
        Runnable resumeDegradingTask = mock(Runnable.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true, false);

        RunPauseResumeService service = newService(
                state,
                () -> mock(WorldResetManager.class),
                log,
                () -> participants,
                clearPausedSnapshots,
                playerArg -> {},
                pausedState,
                mock(ActionBarTickerService.class),
                new TaskGroup(),
                "degrading",
                mock(Runnable.class),
                mock(PrepAreaService.class),
                challengeManager,
                startActionBarTask,
                resumeDegradingTask);

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("host");

        boolean resumedFirst = service.resume(sender);

        assertTrue(resumedFirst);
        assertTrue(state.is(SessionState.Phase.RUNNING));
        verify(pausedState).applySnapshotIfPresent(player);
        verify(log).sendInfo(player, "Challenge resumed.");
        verify(clearPausedSnapshots).run();
        verify(startActionBarTask).run();
        verify(resumeDegradingTask).run();
        verify(log).info("Challenge resumed by host.");

        state.setPhase(SessionState.Phase.PAUSED);
        boolean resumedSecond = service.resume(sender);

        assertTrue(resumedSecond);
        verify(startActionBarTask, org.mockito.Mockito.times(2)).run();
        verify(resumeDegradingTask, org.mockito.Mockito.times(1)).run();
    }

    private static RunPauseResumeService newService(
            SessionState sessionState,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            DeepCoreLogger log,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Runnable clearPausedSnapshots,
            Consumer<Player> stashRunStateForLobby,
            PausedRunStateService pausedRunStateService,
            ActionBarTickerService actionBarTickerService,
            TaskGroup taskGroup,
            String degradingTaskKey,
            Runnable clearActionBar,
            PrepAreaService prepAreaService,
            ChallengeManager challengeManager,
            Runnable startActionBarTask,
            Runnable resumeDegradingInventoryTask) {
        return new RunPauseResumeService(
                sessionState,
                worldResetManagerSupplier,
                log,
                onlineParticipantsSupplier,
                clearPausedSnapshots,
                stashRunStateForLobby,
                pausedRunStateService,
                actionBarTickerService,
                taskGroup,
                degradingTaskKey,
                clearActionBar,
                prepAreaService,
                challengeManager,
                startActionBarTask,
                resumeDegradingInventoryTask);
    }
}
