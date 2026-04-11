package dev.deepcore.challenge.events;

import dev.deepcore.challenge.inventory.InventoryMechanicsCoordinatorService;
import dev.deepcore.challenge.portal.PortalTransitCoordinatorService;
import dev.deepcore.challenge.session.PrepGuiCoordinatorService;
import dev.deepcore.challenge.session.RunCompletionService;
import dev.deepcore.challenge.session.RunHealthCoordinatorService;
import dev.deepcore.challenge.session.SessionPlayerLifecycleService;
import dev.deepcore.challenge.session.SessionTransitionOrchestratorService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers all challenge-session listeners using domain services.
 */
public final class ChallengeEventRegistrar {
    private final PortalTransitCoordinatorService portalTransitCoordinatorService;
    private final SessionPlayerLifecycleService sessionPlayerLifecycleService;
    private final RunCompletionService runCompletionService;
    private final InventoryMechanicsCoordinatorService inventoryMechanicsCoordinatorService;
    private final RunHealthCoordinatorService runHealthCoordinatorService;
    private final SessionTransitionOrchestratorService sessionTransitionOrchestratorService;
    private final PrepGuiCoordinatorService prepGuiCoordinatorService;

    /**
     * Creates a challenge event registrar.
     *
     * @param portalTransitCoordinatorService      portal transit coordination
     *                                             service
     * @param sessionPlayerLifecycleService        session player lifecycle service
     * @param runCompletionService                 run completion service
     * @param inventoryMechanicsCoordinatorService inventory mechanics coordination
     *                                             service
     * @param runHealthCoordinatorService          run health coordination service
     * @param sessionTransitionOrchestratorService session transition orchestration
     *                                             service
     * @param prepGuiCoordinatorService            prep GUI coordination service
     */
    public ChallengeEventRegistrar(
            PortalTransitCoordinatorService portalTransitCoordinatorService,
            SessionPlayerLifecycleService sessionPlayerLifecycleService,
            RunCompletionService runCompletionService,
            InventoryMechanicsCoordinatorService inventoryMechanicsCoordinatorService,
            RunHealthCoordinatorService runHealthCoordinatorService,
            SessionTransitionOrchestratorService sessionTransitionOrchestratorService,
            PrepGuiCoordinatorService prepGuiCoordinatorService) {
        this.portalTransitCoordinatorService = portalTransitCoordinatorService;
        this.sessionPlayerLifecycleService = sessionPlayerLifecycleService;
        this.runCompletionService = runCompletionService;
        this.inventoryMechanicsCoordinatorService = inventoryMechanicsCoordinatorService;
        this.runHealthCoordinatorService = runHealthCoordinatorService;
        this.sessionTransitionOrchestratorService = sessionTransitionOrchestratorService;
        this.prepGuiCoordinatorService = prepGuiCoordinatorService;
    }

    /**
     * Registers all challenge listeners against the provided plugin manager.
     *
     * @param plugin plugin whose manager receives listener registrations
     */
    public void registerAll(JavaPlugin plugin) {
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new PortalTransitListener(portalTransitCoordinatorService), plugin);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionLifecycleListener(sessionPlayerLifecycleService, runCompletionService), plugin);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new InventoryMechanicsListener(inventoryMechanicsCoordinatorService), plugin);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new SharedVitalsListener(runHealthCoordinatorService), plugin);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        new PreviewListener(sessionTransitionOrchestratorService, sessionPlayerLifecycleService),
                        plugin);
        plugin.getServer().getPluginManager().registerEvents(new PrepFlowListener(prepGuiCoordinatorService), plugin);
    }
}
