package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SessionFailureServiceTest {

    @Test
    void hardcoreFailure_returnsEarlyForGuardConditions() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);
        ChallengeManager manager = mock(ChallengeManager.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        Runnable clearActionBar = mock(Runnable.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        SessionFailureService service = new SessionFailureService(
                state,
                manager,
                Set.of(UUID.randomUUID()),
                Set.of(UUID.randomUUID()),
                Set.of(),
                actionBar,
                clearActionBar,
                () -> null,
                log);

        service.handleHardcoreFailureIfNeeded();

        verify(actionBar, never()).stop();
        verify(clearActionBar, never()).run();
    }

    @Test
    void allPlayersDeadFailure_returnsEarlyWhenNotAllDeadOrHardcoreEnabled() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        ChallengeManager manager = mock(ChallengeManager.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        Runnable clearActionBar = mock(Runnable.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        UUID participant = UUID.randomUUID();
        when(manager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(false);

        SessionFailureService service = new SessionFailureService(
                state,
                manager,
                Set.of(participant),
                Set.of(),
                Set.of(),
                actionBar,
                clearActionBar,
                () -> mock(WorldResetManager.class),
                log);

        service.handleAllPlayersDeadFailureIfNeeded();

        verify(actionBar, never()).stop();
        verify(clearActionBar, never()).run();
    }

    @Test
    void hardcoreFailure_triggersResetFlowWhenConditionsMatch() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        ChallengeManager manager = mock(ChallengeManager.class);
        when(manager.isEnabled()).thenReturn(true);
        when(manager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(true);

        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        Runnable clearActionBar = mock(Runnable.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        Player player = mock(Player.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        SessionFailureService service = new SessionFailureService(
                state,
                manager,
                Set.of(UUID.randomUUID()),
                Set.of(UUID.randomUUID()),
                Set.of(),
                actionBar,
                clearActionBar,
                () -> worldResetManager,
                log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

            service.handleHardcoreFailureIfNeeded();

            verify(actionBar).stop();
            verify(clearActionBar).run();
            verify(player)
                    .sendTitle(
                            any(),
                            any(),
                            org.mockito.ArgumentMatchers.eq(10),
                            org.mockito.ArgumentMatchers.eq(70),
                            org.mockito.ArgumentMatchers.eq(20));
            verify(worldResetManager).resetThreeWorlds(console);
            verify(log).warn("Challenge Failed");
        }
    }

    @Test
    void allPlayersDeadFailure_triggersResetFlowWhenConditionsMatch() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);
        ChallengeManager manager = mock(ChallengeManager.class);
        when(manager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(false);
        when(manager.isEnabled()).thenReturn(true);

        UUID participant = UUID.randomUUID();
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        Runnable clearActionBar = mock(Runnable.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        Player player = mock(Player.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        SessionFailureService service = new SessionFailureService(
                state,
                manager,
                Set.of(participant),
                Set.of(participant),
                Set.of(participant),
                actionBar,
                clearActionBar,
                () -> worldResetManager,
                log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(player));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

            service.handleAllPlayersDeadFailureIfNeeded();

            verify(actionBar).stop();
            verify(clearActionBar).run();
            verify(worldResetManager).resetThreeWorlds(console);
            verify(log).info("All players are dead!");
        }
    }
}
