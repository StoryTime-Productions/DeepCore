package dev.deepcore.challenge.ui;

import java.util.Locale;
import java.util.function.LongFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** Renders and clears the lobby sidebar scoreboard for players. */
public final class LobbySidebarService {
    private final String objectiveName;
    private final String sidebarTitle;

    /**
     * Creates a lobby sidebar service.
     *
     * @param objectiveName scoreboard objective name used for sidebar registration
     * @param sidebarTitle  title shown at the top of the sidebar
     */
    public LobbySidebarService(String objectiveName, String sidebarTitle) {
        this.objectiveName = objectiveName;
        this.sidebarTitle = sidebarTitle;
    }

    /**
     * Applies lobby sidebar content with best-time, splits, and session status.
     *
     * @param player                player receiving sidebar updates
     * @param bestOverall           best recorded overall completion time in
     *                              milliseconds
     * @param bestOverworldToNether best recorded Overworld-to-Nether split in
     *                              milliseconds
     * @param bestNetherToBlaze     best recorded Nether-to-Blaze-Rods split in
     *                              milliseconds
     * @param bestBlazeToEnd        best recorded Blaze-Rods-to-End split in
     *                              milliseconds
     * @param bestNetherToEnd       best recorded Nether-to-End split in
     *                              milliseconds
     * @param bestEndToDragon       best recorded End-to-Dragon split in
     *                              milliseconds
     * @param onlineCount           number of online players
     * @param phaseText             current session phase label
     * @param readyCount            number of players currently marked ready
     * @param durationFormatter     formatter for record durations in milliseconds
     */
    public void applyLobbySidebar(
            Player player,
            long bestOverall,
            long bestOverworldToNether,
            long bestNetherToBlaze,
            long bestBlazeToEnd,
            long bestNetherToEnd,
            long bestEndToDragon,
            int onlineCount,
            String phaseText,
            int readyCount,
            LongFunction<String> durationFormatter) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(objectiveName, "dummy", sidebarTitle);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        applyBlankSidebarNumberFormat(objective);

        int score = 14;
        objective.getScore(ChatColor.DARK_GRAY.toString()).setScore(score--);
        objective.getScore(ChatColor.AQUA + "Best Time").setScore(score--);
        objective
                .getScore(ChatColor.WHITE + formatRecordDuration(bestOverall, durationFormatter))
                .setScore(score--);
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(score--);
        objective.getScore(ChatColor.GOLD + "Split Scores").setScore(score--);
        objective
                .getScore(ChatColor.AQUA + formatRecordDuration(bestOverworldToNether, durationFormatter)
                        + ChatColor.GRAY + " Overworld to Nether")
                .setScore(score--);
        objective
                .getScore(ChatColor.AQUA + formatRecordDuration(bestNetherToBlaze, durationFormatter) + ChatColor.GRAY
                        + " Nether to Blaze Rods")
                .setScore(score--);
        objective
                .getScore(ChatColor.AQUA + formatRecordDuration(bestBlazeToEnd, durationFormatter) + ChatColor.GRAY
                        + " Blaze Rods to End")
                .setScore(score--);
        objective
                .getScore(ChatColor.AQUA + formatRecordDuration(bestNetherToEnd, durationFormatter) + ChatColor.GRAY
                        + " Nether to End")
                .setScore(score--);
        objective
                .getScore(ChatColor.AQUA + formatRecordDuration(bestEndToDragon, durationFormatter) + ChatColor.GRAY
                        + " End to Dragon")
                .setScore(score--);
        objective.getScore(ChatColor.BLACK.toString()).setScore(score--);

        objective.getScore(ChatColor.YELLOW + "Phase: " + phaseText).setScore(score--);
        objective
                .getScore(ChatColor.YELLOW + "Ready: " + ChatColor.WHITE + readyCount + "/" + onlineCount)
                .setScore(score--);
        objective
                .getScore(ChatColor.YELLOW + "Lobby: " + ChatColor.WHITE + resolveLobbyLabel(player.getWorld()))
                .setScore(score--);

        player.setScoreboard(scoreboard);
    }

    /**
     * Clears the managed sidebar objective from the player's scoreboard.
     *
     * @param player player whose sidebar should be cleared
     */
    public void clearLobbySidebar(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void applyBlankSidebarNumberFormat(Objective objective) {
        if (objective == null) {
            return;
        }

        try {
            Class<?> numberFormatClass = Class.forName("org.bukkit.scoreboard.NumberFormat");
            Object blankFormat = numberFormatClass.getMethod("blank").invoke(null);
            objective.getClass().getMethod("setNumberFormat", numberFormatClass).invoke(objective, blankFormat);
        } catch (ReflectiveOperationException ignored) {
            // Older APIs may not support objective number formatting.
        }
    }

    private String resolveLobbyLabel(World world) {
        if (world == null) {
            return "SKYBLOCK";
        }

        World.Environment environment = world.getEnvironment();
        if (environment == World.Environment.NETHER) {
            return "NETHER";
        }

        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (worldName.contains("limbo") || worldName.contains("sky")) {
            return "SKYBLOCK";
        }

        return "OVERWORLD";
    }

    private String formatRecordDuration(long durationMs, LongFunction<String> durationFormatter) {
        if (durationMs < 0L) {
            return "--:--";
        }
        return durationFormatter.apply(durationMs);
    }
}
