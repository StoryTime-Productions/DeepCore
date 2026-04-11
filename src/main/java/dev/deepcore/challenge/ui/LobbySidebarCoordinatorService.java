package dev.deepcore.challenge.ui;

import dev.deepcore.challenge.session.RunUiFormattingService;
import dev.deepcore.challenge.session.SidebarModel;
import dev.deepcore.challenge.world.WorldClassificationService;
import org.bukkit.entity.Player;

/**
 * Coordinates lobby sidebar rendering across online players.
 */
public final class LobbySidebarCoordinatorService {
    private final LobbySidebarService lobbySidebarService;
    private final WorldClassificationService worldClassificationService;
    private final RunUiFormattingService runUiFormattingService;

    /**
     * Creates a lobby sidebar coordinator service.
     *
     * @param lobbySidebarService        sidebar rendering service
     * @param worldClassificationService world classification helper
     * @param runUiFormattingService     run UI formatting helper
     */
    public LobbySidebarCoordinatorService(
            LobbySidebarService lobbySidebarService,
            WorldClassificationService worldClassificationService,
            RunUiFormattingService runUiFormattingService) {
        this.lobbySidebarService = lobbySidebarService;
        this.worldClassificationService = worldClassificationService;
        this.runUiFormattingService = runUiFormattingService;
    }

    /**
     * Refreshes lobby sidebars for online players using the supplied sidebar model.
     *
     * @param players online players to refresh
     * @param model   sidebar model snapshot used for rendering
     */
    public void refreshForOnlinePlayers(Iterable<? extends Player> players, SidebarModel model) {
        for (Player player : players) {
            if (!worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
                lobbySidebarService.clearLobbySidebar(player);
                continue;
            }

            lobbySidebarService.applyLobbySidebar(
                    player,
                    model.bestOverall(),
                    model.bestOverworldToNether(),
                    model.bestNetherToBlaze(),
                    model.bestBlazeToEnd(),
                    model.bestNetherToEnd(),
                    model.bestEndToDragon(),
                    model.onlineCount(),
                    model.phaseText(),
                    model.readyCount(),
                    runUiFormattingService::formatSplitDuration);
        }
    }
}
