package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PrepCountdownServiceTest {

    @Test
    void startCountdown_ticksThenCompletesAtZero() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        PrepCountdownService service = new PrepCountdownService(plugin);
        BooleanSupplier shouldCancel = () -> false;
        Runnable onCancel = mock(Runnable.class);
        Runnable onComplete = mock(Runnable.class);
        IntConsumer onTick = mock(IntConsumer.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            service.startCountdown(2, shouldCancel, onCancel, onComplete, onTick);

            Runnable tick = tickRef.get();
            assertNotNull(tick);
            tick.run();
            tick.run();
            tick.run();

            verify(onTick).accept(2);
            verify(onTick).accept(1);
            verify(onComplete).run();
            verify(onCancel, times(0)).run();
            verify(task, times(1)).cancel();
        }
    }

    @Test
    void startCountdown_cancelsWhenPredicateIsTrue() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        PrepCountdownService service = new PrepCountdownService(plugin);
        Runnable onCancel = mock(Runnable.class);
        Runnable onComplete = mock(Runnable.class);
        IntConsumer onTick = mock(IntConsumer.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            service.startCountdown(5, () -> true, onCancel, onComplete, onTick);

            Runnable tick = tickRef.get();
            assertNotNull(tick);
            tick.run();

            verify(onCancel).run();
            verify(onComplete, times(0)).run();
            verify(onTick, times(0)).accept(org.mockito.ArgumentMatchers.anyInt());
        }
    }
}
