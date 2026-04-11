package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class ActionBarTickerServiceTest {

    @Test
    void start_ticksAndBroadcastsWhenShouldTickIsTrue() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        Player first = mock(Player.class);
        Player second = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        ActionBarTickerService service = new ActionBarTickerService(plugin);
        BooleanSupplier shouldTick = () -> true;
        Runnable tickWork = mock(Runnable.class);
        Supplier<Component> messageSupplier = () -> Component.text("run");
        Supplier<List<Player>> recipients = () -> List.of(first, second);

        service.start(shouldTick, tickWork, messageSupplier, recipients);

        Runnable tick = tickRef.get();
        assertNotNull(tick);
        tick.run();

        verify(tickWork).run();
        verify(first).sendActionBar(Component.text("run"));
        verify(second).sendActionBar(Component.text("run"));
    }

    @Test
    void start_skipsWorkWhenShouldTickIsFalse() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        Player player = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        AtomicReference<Runnable> tickRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    tickRef.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));

        ActionBarTickerService service = new ActionBarTickerService(plugin);
        Runnable tickWork = mock(Runnable.class);

        service.start(() -> false, tickWork, Component::empty, () -> List.of(player));

        Runnable tick = tickRef.get();
        assertNotNull(tick);
        tick.run();

        verify(tickWork, times(0)).run();
        verify(player, times(0)).sendActionBar(any(Component.class));
    }

    @Test
    void start_replacesPreviousTickerTask() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask firstTask = mock(BukkitTask.class);
        BukkitTask secondTask = mock(BukkitTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L)))
                .thenReturn(firstTask)
                .thenReturn(secondTask);

        ActionBarTickerService service = new ActionBarTickerService(plugin);

        service.start(() -> true, () -> {}, Component::empty, List::of);
        service.start(() -> true, () -> {}, Component::empty, List::of);

        verify(firstTask).cancel();
    }

    @Test
    void clearActionBar_sendsEmptyActionBarToAllPlayers() {
        Plugin plugin = mock(Plugin.class);
        ActionBarTickerService service = new ActionBarTickerService(plugin);
        Player first = mock(Player.class);
        Player second = mock(Player.class);

        service.clearActionBar(List.of(first, second));

        verify(first).sendActionBar(Component.empty());
        verify(second).sendActionBar(Component.empty());
    }
}
