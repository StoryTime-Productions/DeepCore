package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import dev.deepcore.records.RunRecordsService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

class RunCompletionServiceTest {

    @Test
    void handleEntityDeath_ignoresWhenNotRunning() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.PREP);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> null,
                Set.of(),
                () -> null,
                mock(Runnable.class),
                log);

        EntityDeathEvent event = mock(EntityDeathEvent.class);

        service.handleEntityDeath(event);

        verify(runProgressService, never()).markDragonKilled(any(Long.class));
        verify(completionReturnService, never()).start(anyInt(), any(), any(), any());
    }

    @Test
    void handleEntityDeath_ignoresWhenEntityIsNotDragon() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> null,
                Set.of(),
                () -> null,
                mock(Runnable.class),
                log);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(org.bukkit.entity.Player.class));

        service.handleEntityDeath(event);

        verify(runProgressService, never()).markDragonKilled(any(Long.class));
        verify(completionReturnService, never()).start(anyInt(), any(), any(), any());
    }

    @Test
    void handleEntityDeath_dragonKillStartsReturnCountdownAndFallbackCompletes() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Runnable fallback = mock(Runnable.class);

        @SuppressWarnings("unchecked")
        Supplier<dev.deepcore.records.RunRecordsService> recordsSupplier = () -> null;
        Supplier<WorldResetManager> worldResetSupplier = () -> null;

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                recordsSupplier,
                Set.of(UUID.randomUUID()),
                worldResetSupplier,
                fallback,
                log);

        final Runnable[] onComplete = {null};
        org.mockito.Mockito.doAnswer(invocation -> {
                    onComplete[0] = invocation.getArgument(2);
                    Consumer<Integer> onSecond = invocation.getArgument(3);
                    onSecond.accept(10);
                    return null;
                })
                .when(completionReturnService)
                .start(anyInt(), any(), any(), any());

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(EnderDragon.class));

        service.handleEntityDeath(event);

        verify(runProgressService).markDragonKilled(any(Long.class));
        verify(completionReturnService).start(anyInt(), any(), any(), any());
        verify(log).info("Victory! Ender Dragon defeated!");
        verify(log).info("Lobby in 10s...");

        onComplete[0].run();
        verify(fallback).run();
    }

    @Test
    void handleEntityDeath_recordsRunWhenRecordsServiceAndRunStartAreAvailable() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);
        sessionState.timing().beginRun(1_000L);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);
        RunRecordsService recordsService = mock(RunRecordsService.class);

        when(runProgressService.calculateSectionDurations(anyLong(), anyLong(), anyLong()))
                .thenReturn(new RunProgressService.SectionDurations(100L, 200L, 300L, 400L, 500L));

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> recordsService,
                Set.of(),
                () -> null,
                mock(Runnable.class),
                mock(DeepCoreLogger.class));

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(EnderDragon.class));

        service.handleEntityDeath(event);

        verify(recordsService).recordRun(anyLong(), eq(100L), eq(200L), eq(300L), eq(400L), eq(500L), eq(List.of()));
    }

    @Test
    void handleEntityDeath_skipsRecordWhenRunStartIsUnset() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);
        RunRecordsService recordsService = mock(RunRecordsService.class);

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> recordsService,
                Set.of(),
                () -> null,
                mock(Runnable.class),
                mock(DeepCoreLogger.class));

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(EnderDragon.class));

        service.handleEntityDeath(event);

        verify(recordsService, never())
                .recordRun(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void handleEntityDeath_countdownPredicateReflectsPhaseAndDragonState() {
        SessionState sessionState = new SessionState();
        sessionState.setPhase(SessionState.Phase.RUNNING);

        RunProgressService runProgressService = mock(RunProgressService.class);
        CompletionReturnService completionReturnService = mock(CompletionReturnService.class);

        final BooleanSupplier[] shouldContinue = {null};
        org.mockito.Mockito.doAnswer(invocation -> {
                    shouldContinue[0] = invocation.getArgument(1);
                    return null;
                })
                .when(completionReturnService)
                .start(anyInt(), any(), any(), any());

        RunCompletionService service = newService(
                sessionState,
                runProgressService,
                completionReturnService,
                () -> null,
                Set.of(),
                () -> null,
                mock(Runnable.class),
                mock(DeepCoreLogger.class));

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(EnderDragon.class));
        when(runProgressService.isDragonKilled()).thenReturn(true, false);

        service.handleEntityDeath(event);

        org.junit.jupiter.api.Assertions.assertTrue(shouldContinue[0].getAsBoolean());
        sessionState.setPhase(SessionState.Phase.PREP);
        org.junit.jupiter.api.Assertions.assertFalse(shouldContinue[0].getAsBoolean());
    }

    private static RunCompletionService newService(
            SessionState sessionState,
            RunProgressService runProgressService,
            CompletionReturnService completionReturnService,
            Supplier<dev.deepcore.records.RunRecordsService> recordsServiceSupplier,
            Set<UUID> participants,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            Runnable endChallengeAndReturnToPrep,
            DeepCoreLogger log) {
        return new RunCompletionService(
                sessionState,
                runProgressService,
                completionReturnService,
                recordsServiceSupplier,
                participants,
                worldResetManagerSupplier,
                endChallengeAndReturnToPrep,
                log);
    }
}
