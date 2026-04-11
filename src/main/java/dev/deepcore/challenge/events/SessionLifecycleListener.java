package dev.deepcore.challenge.events;

import dev.deepcore.challenge.session.RunCompletionService;
import dev.deepcore.challenge.session.SessionPlayerLifecycleService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Routes session lifecycle events to the session coordinator.
 */
public final class SessionLifecycleListener implements Listener {
    private final SessionPlayerLifecycleService sessionPlayerLifecycleService;
    private final RunCompletionService runCompletionService;

    /**
     * Creates a session lifecycle listener.
     *
     * @param sessionPlayerLifecycleService player lifecycle coordinator service
     * @param runCompletionService          run completion event handling service
     */
    public SessionLifecycleListener(
            SessionPlayerLifecycleService sessionPlayerLifecycleService, RunCompletionService runCompletionService) {
        this.sessionPlayerLifecycleService = sessionPlayerLifecycleService;
        this.runCompletionService = runCompletionService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sessionPlayerLifecycleService.handlePlayerJoin(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionPlayerLifecycleService.handlePlayerQuit(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        runCompletionService.handleEntityDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        sessionPlayerLifecycleService.handlePlayerDeath(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        sessionPlayerLifecycleService.handlePlayerRespawn(event);
    }
}
