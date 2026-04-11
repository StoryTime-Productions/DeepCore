package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class CompletionReturnServiceTest {

    @Test
    void start_notifiesRemainingSecondsAndCompletesAtZero() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        CompletionReturnService service = new CompletionReturnService(plugin);
        BooleanSupplier shouldContinue = () -> true;
        Runnable onComplete = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<Integer> onSecondRemaining = mock(Consumer.class);

        service.start(2, shouldContinue, onComplete, onSecondRemaining);

        Runnable tick = tickRef.get();
        assertNotNull(tick);
        tick.run();
        tick.run();
        tick.run();

        verify(onSecondRemaining).accept(2);
        verify(onSecondRemaining).accept(1);
        verify(onComplete).run();
        verify(task, times(1)).cancel();
    }

    @Test
    void start_stopsImmediatelyWhenShouldContinueIsFalse() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        CompletionReturnService service = new CompletionReturnService(plugin);
        Runnable onComplete = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<Integer> onSecondRemaining = mock(Consumer.class);

        service.start(5, () -> false, onComplete, onSecondRemaining);

        Runnable tick = tickRef.get();
        assertNotNull(tick);
        tick.run();

        verify(task).cancel();
        verify(onComplete, times(0)).run();
        verify(onSecondRemaining, times(0)).accept(any(Integer.class));
    }

    @Test
    void stop_cancelsActiveTaskWhenPresent() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        doAnswer(invocation -> task).when(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        CompletionReturnService service = new CompletionReturnService(plugin);
        service.start(1, () -> true, mock(Runnable.class), seconds -> {});

        service.stop();

        verify(task).cancel();
    }
}
