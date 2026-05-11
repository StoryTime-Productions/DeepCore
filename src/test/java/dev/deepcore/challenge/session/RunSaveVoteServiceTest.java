package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RunSaveVoteServiceTest {

    @Test
    void castVote_emptyParticipants_returnsFalseAndSendsError() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player voter = mock(Player.class);
        RunSaveVoteService service = new RunSaveVoteService(plugin, log, List::of);

        boolean result = service.castVote(voter, mock(Runnable.class));

        assertFalse(result);
        verify(log).sendError(voter, "No active run participants to vote with.");
    }

    @Test
    void castVote_nonParticipant_returnsFalseAndSendsError() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player participant = mock(Player.class);
        when(participant.getUniqueId()).thenReturn(UUID.randomUUID());
        Player voter = mock(Player.class);
        when(voter.getUniqueId()).thenReturn(UUID.randomUUID());

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(participant));

        boolean result = service.castVote(voter, mock(Runnable.class));

        assertFalse(result);
        verify(log).sendError(voter, "You are not a participant in the current run.");
    }

    @Test
    void castVote_duplicateVote_returnsFalseAndWarns() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player voter = mock(Player.class);
        Player other = mock(Player.class);
        when(voter.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(voter.getName()).thenReturn("Player1");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(voter, other));
        Runnable callback = mock(Runnable.class);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            service.castVote(voter, callback);
            boolean result = service.castVote(voter, callback);
            assertFalse(result);
        }

        verify(log).sendWarn(voter, "You have already voted to save this run.");
    }

    @Test
    void castVote_unanimousVote_triggersCallbackAndClearsVotes() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p2.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p1.getName()).thenReturn("P1");
        when(p2.getName()).thenReturn("P2");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(p1, p2));
        Runnable callback = mock(Runnable.class);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);

            service.castVote(p1, callback);
            boolean result = service.castVote(p2, callback);

            assertTrue(result);
        }

        verify(callback).run();
    }

    @Test
    void castVote_singleParticipant_triggersCallbackImmediately() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player voter = mock(Player.class);
        when(voter.getUniqueId()).thenReturn(UUID.randomUUID());
        when(voter.getName()).thenReturn("Solo");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(voter));
        Runnable callback = mock(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            boolean result = service.castVote(voter, callback);
            assertTrue(result);
        }

        verify(callback).run();
    }

    @Test
    void castVote_firstVote_startsTimeoutTask() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p2.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p1.getName()).thenReturn("P1");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(p1, p2));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            service.castVote(p1, mock(Runnable.class));
        }

        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(60L * 20L));
    }

    @Test
    void castVote_subsequentVote_doesNotRestartTimeout() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player p3 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p2.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p3.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p1.getName()).thenReturn("P1");
        when(p2.getName()).thenReturn("P2");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(p1, p2, p3));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            service.castVote(p1, mock(Runnable.class));
            service.castVote(p2, mock(Runnable.class));
        }

        verify(scheduler, org.mockito.Mockito.times(1)).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
    }

    @Test
    void clearVotes_cancelsActiveTimeoutTask() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p2.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p1.getName()).thenReturn("P1");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(p1, p2));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            service.castVote(p1, mock(Runnable.class));
        }

        service.clearVotes();

        verify(task).cancel();
    }

    @Test
    void clearVotes_withNoActiveTask_doesNotThrow() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        RunSaveVoteService service = new RunSaveVoteService(plugin, log, List::of);

        service.clearVotes();
    }

    @Test
    void voteTimeout_expiry_clearsVotesAndBroadcasts() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p2.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p1.getName()).thenReturn("P1");

        RunSaveVoteService service = new RunSaveVoteService(plugin, log, () -> List.of(p1, p2));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        org.mockito.ArgumentCaptor<Runnable> taskCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskLater(eq(plugin), taskCaptor.capture(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
        Runnable callback = mock(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.broadcastMessage(any())).thenAnswer(inv -> null);
            service.castVote(p1, callback);
            taskCaptor.getValue().run();
        }

        verify(callback, never()).run();
    }
}
