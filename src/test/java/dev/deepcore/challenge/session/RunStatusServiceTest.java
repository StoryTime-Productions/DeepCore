package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunStatusServiceTest {

    private RunProgressService progress;
    private RunUiFormattingService formatting;
    private DeepCoreLogger log;
    private RunStatusService service;

    @BeforeEach
    void setUp() {
        progress = mock(RunProgressService.class);
        formatting = mock(RunUiFormattingService.class);
        log = mock(DeepCoreLogger.class);
        service = new RunStatusService(progress, formatting, log);

        when(progress.snapshotForDisplay(anyInt()))
                .thenReturn(new RunProgressService.RunProgressSnapshot("Enter the Nether", false, false, false, false));
        when(progress.resolveElapsedReferenceTime(anyLong())).thenReturn(10_000L);
        when(formatting.formatElapsedTime(anyLong(), anyLong(), anyLong(), anyBoolean(), anyLong()))
                .thenReturn("00:10");
        when(formatting.buildRunActionBarMessage(any(), any())).thenReturn(Component.text("ok"));
    }

    @Test
    void onParticipantWorldChanged_marksMilestonesAndCountsBlazeRods() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(6);
        when(progress.maybeMarkBlazeObjectiveReached(true, 6, 1_000L)).thenReturn(OptionalLong.empty());

        service.onParticipantWorldChanged(World.Environment.NETHER, players, 1_000L, true);

        verify(progress).markNetherReached(1_000L);
        verify(progress).countTeamBlazeRods(players);
    }

    @Test
    void onParticipantWorldChanged_endEnvironment_marksEndMilestone() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(1);
        when(progress.maybeMarkBlazeObjectiveReached(false, 1, 1_200L)).thenReturn(OptionalLong.empty());

        service.onParticipantWorldChanged(World.Environment.THE_END, players, 1_200L, false);

        verify(progress).markEndReached(1_200L);
    }

    @Test
    void onParticipantWorldChanged_otherEnvironment_countsButDoesNotMarkMilestones() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(3);
        when(progress.maybeMarkBlazeObjectiveReached(false, 3, 1_300L)).thenReturn(OptionalLong.empty());

        service.onParticipantWorldChanged(World.Environment.NORMAL, players, 1_300L, false);

        verify(progress, never()).markNetherReached(anyLong());
        verify(progress, never()).markEndReached(anyLong());
        verify(progress).countTeamBlazeRods(players);
    }

    @Test
    void tickProgressFromParticipants_updatesProgressAndBlazeObjective() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(7);
        when(progress.maybeMarkBlazeObjectiveReached(true, 7, 2_000L)).thenReturn(OptionalLong.of(500L));
        when(formatting.formatSplitDuration(500L)).thenReturn("00:00");

        service.tickProgressFromParticipants(players, 2_000L, true);

        verify(progress).updateMilestonesFromParticipants(players, 2_000L);
        verify(log).info(any());
    }

    @Test
    void tickProgressFromParticipants_withoutSplit_doesNotLogObjectiveCompletion() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(2);
        when(progress.maybeMarkBlazeObjectiveReached(true, 2, 2_100L)).thenReturn(OptionalLong.empty());

        service.tickProgressFromParticipants(players, 2_100L, true);

        verify(progress).updateMilestonesFromParticipants(players, 2_100L);
        verify(log, never()).info(any());
    }

    @Test
    void buildRunActionBarMessage_buildsComponentFromSnapshotAndElapsedTime() {
        Component component = service.buildRunActionBarMessage(1_000L, 0L, false, 0L);

        assertNotNull(component);
        verify(progress).snapshotForDisplay(anyInt());
        verify(formatting).buildRunActionBarMessage("Enter the Nether", "00:10");
    }

    @Test
    void reset_resetsInternalBlazeRodObservationState() {
        List<Player> players = List.of(mock(Player.class));
        when(progress.countTeamBlazeRods(players)).thenReturn(4);
        when(progress.maybeMarkBlazeObjectiveReached(true, 4, 2_400L)).thenReturn(OptionalLong.empty());

        service.tickProgressFromParticipants(players, 2_400L, true);
        service.buildRunActionBarMessage(1_000L, 0L, false, 0L);

        service.reset();
        service.buildRunActionBarMessage(1_000L, 0L, false, 0L);

        verify(progress).countTeamBlazeRods(players);
        verify(progress, times(1)).snapshotForDisplay(4);
        verify(progress, times(1)).snapshotForDisplay(0);
    }
}
