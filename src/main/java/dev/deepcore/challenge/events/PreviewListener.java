package dev.deepcore.challenge.events;

import dev.deepcore.challenge.session.SessionPlayerLifecycleService;
import dev.deepcore.challenge.session.SessionTransitionOrchestratorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Routes preview and world-transition related events to the session
 * coordinator.
 */
public final class PreviewListener implements Listener {
    private final SessionTransitionOrchestratorService sessionTransitionOrchestratorService;
    private final SessionPlayerLifecycleService sessionPlayerLifecycleService;

    /**
     * Creates a preview listener.
     *
     * @param sessionTransitionOrchestratorService session transition orchestrator
     * @param sessionPlayerLifecycleService        player lifecycle coordinator
     *                                             service
     */
    public PreviewListener(
            SessionTransitionOrchestratorService sessionTransitionOrchestratorService,
            SessionPlayerLifecycleService sessionPlayerLifecycleService) {
        this.sessionTransitionOrchestratorService = sessionTransitionOrchestratorService;
        this.sessionPlayerLifecycleService = sessionPlayerLifecycleService;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        sessionTransitionOrchestratorService.handleWorldLoad(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sessionPlayerLifecycleService.handlePlayerChangedWorld(event);
    }
}
