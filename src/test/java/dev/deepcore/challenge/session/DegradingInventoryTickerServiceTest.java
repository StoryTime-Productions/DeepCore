package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DegradingInventoryTickerServiceTest {

    @Test
    void start_resetsSlotsAndCancelsWhenNotRunning() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        TaskGroup taskGroup = mock(TaskGroup.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);
        ChallengeManager manager = mock(ChallengeManager.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        DegradingInventoryService degrading = mock(DegradingInventoryService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        when(config.degradingIntervalSeconds()).thenReturn(5);
        when(config.degradingMinSlots()).thenReturn(100);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(200L));

        DegradingInventoryTickerService service = new DegradingInventoryTickerService(
                plugin, taskGroup, "degrading", state, manager, config, degrading, List::of, mock(Consumer.class), log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.start(true);

            verify(degrading).resetAllowedInventorySlots();
            verify(taskGroup).replace("degrading", task);

            Runnable tick = tickRef.get();
            assertNotNull(tick);
            tick.run();

            verify(taskGroup).cancel("degrading");
            verify(degrading, never()).reduceAllowedInventorySlots(any(Integer.class));
        }
    }

    @Test
    void start_reducesSlotsAndWarnsParticipantsWhenEligible() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        TaskGroup taskGroup = mock(TaskGroup.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        ChallengeManager manager = mock(ChallengeManager.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        DegradingInventoryService degrading = mock(DegradingInventoryService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        when(config.degradingIntervalSeconds()).thenReturn(20);
        when(config.degradingMinSlots()).thenReturn(5);
        when(manager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)).thenReturn(true);
        when(degrading.reduceAllowedInventorySlots(5)).thenReturn(true);
        when(degrading.getAllowedInventorySlots()).thenReturn(7);

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforce = mock(Consumer.class);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(400L), eq(400L));

        DegradingInventoryTickerService service = new DegradingInventoryTickerService(
                plugin, taskGroup, "degrading", state, manager, config, degrading, () -> List.of(p1, p2), enforce, log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.start(false);

            Runnable tick = tickRef.get();
            assertNotNull(tick);
            tick.run();

            verify(enforce).accept(p1);
            verify(enforce).accept(p2);
            verify(log).sendWarn(eq(p1), org.mockito.ArgumentMatchers.contains("7 slots"));
            verify(log).sendWarn(eq(p2), org.mockito.ArgumentMatchers.contains("7 slots"));
        }
    }
}
