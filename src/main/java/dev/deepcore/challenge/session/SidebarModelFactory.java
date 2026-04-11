package dev.deepcore.challenge.session;

import org.bukkit.ChatColor;

/**
 * Builds SidebarModel instances from current session and records state.
 */
public final class SidebarModelFactory {
    /**
     * Creates a sidebar model snapshot for lobby sidebar rendering.
     *
     * @param recordsService run records service used to read best split times
     * @param phase          current session phase
     * @param readyCount     number of ready players
     * @param onlineCount    number of online players
     * @return sidebar model snapshot for lobby rendering
     */
    public SidebarModel create(
            dev.deepcore.records.RunRecordsService recordsService,
            SessionState.Phase phase,
            int readyCount,
            int onlineCount) {
        long bestOverall = recordsService != null ? recordsService.getBestOverallTime() : -1L;
        long bestOverworldToNether =
                recordsService != null ? recordsService.getBestSectionTime("overworld_to_nether") : -1L;
        long bestNetherToBlaze =
                recordsService != null ? recordsService.getBestSectionTime("nether_to_blaze_rods") : -1L;
        long bestBlazeToEnd = recordsService != null ? recordsService.getBestSectionTime("blaze_rods_to_end") : -1L;
        long bestNetherToEnd = recordsService != null ? recordsService.getBestSectionTime("nether_to_end") : -1L;
        long bestEndToDragon = recordsService != null ? recordsService.getBestSectionTime("end_to_dragon") : -1L;

        String phaseText =
                switch (phase) {
                    case PREP -> ChatColor.GREEN + "Prep";
                    case COUNTDOWN -> ChatColor.GOLD + "Countdown";
                    case PAUSED -> ChatColor.YELLOW + "Paused";
                    case RUNNING -> ChatColor.RED + "Running";
                };

        return new SidebarModel(
                bestOverall,
                bestOverworldToNether,
                bestNetherToBlaze,
                bestBlazeToEnd,
                bestNetherToEnd,
                bestEndToDragon,
                onlineCount,
                phaseText,
                readyCount);
    }
}
