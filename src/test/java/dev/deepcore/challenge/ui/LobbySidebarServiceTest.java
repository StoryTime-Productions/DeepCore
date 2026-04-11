package dev.deepcore.challenge.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.LongFunction;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LobbySidebarServiceTest {

    @Test
    void applyLobbySidebar_returnsWhenScoreboardManagerUnavailable() {
        LobbySidebarService service = new LobbySidebarService("deepcore", "DeepCore");
        Player player = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(null);

            service.applyLobbySidebar(player, -1, -1, -1, -1, -1, -1, 0, "Prep", 0, ms -> "00:00");

            verify(player, never()).setScoreboard(any());
        }
    }

    @Test
    void applyLobbySidebar_rendersScoresAndUsesWorldLabelBranches() {
        LobbySidebarService service = new LobbySidebarService("deepcore", "DeepCore");
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);

        ScoreboardManager manager = mock(ScoreboardManager.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);

        when(manager.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective("deepcore", "dummy", "DeepCore")).thenReturn(objective);
        when(objective.getScore(any(String.class))).thenReturn(score);

        LongFunction<String> formatter = ms -> "T" + ms;

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(manager);

            service.applyLobbySidebar(player, 1000, 200, 300, 400, 500, 600, 2, "Running", 1, formatter);

            verify(objective).setDisplaySlot(DisplaySlot.SIDEBAR);
            verify(score, org.mockito.Mockito.atLeastOnce()).setScore(any(Integer.class));
            verify(objective).getScore(org.mockito.ArgumentMatchers.contains("NETHER"));
            verify(player).setScoreboard(scoreboard);
        }
    }

    @Test
    void clearLobbySidebar_switchesBackToMainScoreboardWhenObjectiveExists() {
        LobbySidebarService service = new LobbySidebarService("deepcore", "DeepCore");
        Player player = mock(Player.class);

        ScoreboardManager manager = mock(ScoreboardManager.class);
        Scoreboard playerBoard = mock(Scoreboard.class);
        Scoreboard mainBoard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);

        when(player.getScoreboard()).thenReturn(playerBoard);
        when(playerBoard.getObjective("deepcore")).thenReturn(objective);
        when(manager.getMainScoreboard()).thenReturn(mainBoard);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(manager);
            service.clearLobbySidebar(player);
            verify(player).setScoreboard(eq(mainBoard));
        }
    }
}
