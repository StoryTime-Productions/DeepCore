package dev.deepcore.challenge.events;

import dev.deepcore.challenge.session.RunHealthCoordinatorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Routes shared-vitals related combat/regen/hunger events to the session
 * coordinator.
 */
public final class SharedVitalsListener implements Listener {
    private final RunHealthCoordinatorService runHealthCoordinatorService;

    /**
     * Creates a shared vitals listener.
     *
     * @param runHealthCoordinatorService run health/vitals coordinator service
     */
    public SharedVitalsListener(RunHealthCoordinatorService runHealthCoordinatorService) {
        this.runHealthCoordinatorService = runHealthCoordinatorService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        runHealthCoordinatorService.handleEntityRegainHealth(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        runHealthCoordinatorService.handleEntityDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        runHealthCoordinatorService.handleFoodLevelChange(event);
    }
}
