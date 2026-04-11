package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ui.LobbySidebarCoordinatorService;
import dev.deepcore.challenge.ui.LobbySidebarService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SessionUiCoordinatorServiceTest {

    @Test
    void startActionBarTask_wiresTickerAndInvokesRunStatusPipelines() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        TaskGroup taskGroup = mock(TaskGroup.class);
        ActionBarTickerService ticker = mock(ActionBarTickerService.class);
        RunStatusService status = mock(RunStatusService.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        state.timing().beginRun(1_000L);

        Player player = mock(Player.class);
        Supplier<List<Player>> participants = () -> List.of(player);

        ParticipantsView participantsView = mock(ParticipantsView.class);
        SidebarModelFactory sidebarFactory = mock(SidebarModelFactory.class);
        LobbySidebarCoordinatorService coordinator = mock(LobbySidebarCoordinatorService.class);
        LobbySidebarService sidebarService = mock(LobbySidebarService.class);

        when(status.buildRunActionBarMessage(any(Long.class), any(Long.class), any(Boolean.class), any(Long.class)))
                .thenReturn(Component.text("run"));

        SessionUiCoordinatorService service = new SessionUiCoordinatorService(
                plugin,
                taskGroup,
                "lobby",
                ticker,
                status,
                state,
                participants,
                participantsView,
                sidebarFactory,
                () -> null,
                () -> 0,
                coordinator,
                sidebarService);

        AtomicReference<Runnable> tickWork = new AtomicReference<>();
        AtomicReference<Supplier<Component>> messageSupplier = new AtomicReference<>();
        doAnswer(invocation -> {
                    BooleanSupplier shouldTick = invocation.getArgument(0);
                    tickWork.set(invocation.getArgument(1));
                    messageSupplier.set(invocation.getArgument(2));
                    Supplier<List<Player>> recipients = invocation.getArgument(3);
                    org.junit.jupiter.api.Assertions.assertTrue(shouldTick.getAsBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals(
                            1, recipients.get().size());
                    return null;
                })
                .when(ticker)
                .start(any(), any(), any(), any());

        service.startActionBarTask();

        Runnable work = tickWork.get();
        Supplier<Component> msg = messageSupplier.get();
        assertNotNull(work);
        assertNotNull(msg);

        work.run();
        msg.get();

        verify(status).tickProgressFromParticipants(eq(List.of(player)), any(Long.class), eq(true));
        verify(status).buildRunActionBarMessage(any(Long.class), any(Long.class), eq(false), any(Long.class));
    }

    @Test
    void startLobbySidebarTask_schedulesRefreshAndClearsSidebar() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        TaskGroup taskGroup = mock(TaskGroup.class);
        ActionBarTickerService ticker = mock(ActionBarTickerService.class);
        RunStatusService status = mock(RunStatusService.class);
        SessionState state = new SessionState();

        ParticipantsView participantsView = mock(ParticipantsView.class);
        SidebarModelFactory sidebarFactory = mock(SidebarModelFactory.class);
        LobbySidebarCoordinatorService coordinator = mock(LobbySidebarCoordinatorService.class);
        LobbySidebarService sidebarService = mock(LobbySidebarService.class);

        when(participantsView.onlineCount()).thenReturn(2);
        SidebarModel model = new SidebarModel(-1, -1, -1, -1, -1, -1, 2, "Prep", 1);
        when(sidebarFactory.create(any(), eq(SessionState.Phase.PREP), eq(1), eq(2)))
                .thenReturn(model);

        SessionUiCoordinatorService service = new SessionUiCoordinatorService(
                plugin,
                taskGroup,
                "lobby",
                ticker,
                status,
                state,
                List::of,
                participantsView,
                sidebarFactory,
                () -> null,
                () -> 1,
                coordinator,
                sidebarService);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        AtomicReference<Runnable> refresh = new AtomicReference<>();
        doAnswer(invocation -> {
                    refresh.set(invocation.getArgument(1));
                    return task;
                })
                .when(scheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(40L));

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));

            service.startLobbySidebarTask();
            verify(taskGroup).replace("lobby", task);

            Runnable refreshTask = refresh.get();
            assertNotNull(refreshTask);
            refreshTask.run();

            verify(coordinator).refreshForOnlinePlayers(eq(Set.of(p1, p2)), eq(model));

            service.clearLobbySidebar(p1);
            verify(sidebarService).clearLobbySidebar(p1);
        }
    }
}
