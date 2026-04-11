package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

/**
 * Coordinates world-rule synchronization and shared-vitals policy application.
 */
public final class SessionRulesCoordinatorService {
    private final ChallengeManager challengeManager;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final Supplier<RunHealthCoordinatorService> runHealthCoordinatorServiceSupplier;

    /**
     * Creates a session rules coordinator service.
     *
     * @param challengeManager                    challenge settings and component
     *                                            manager
     * @param worldResetManagerSupplier           supplier for current world reset
     *                                            manager
     * @param runHealthCoordinatorServiceSupplier supplier for run health
     *                                            coordinator
     */
    public SessionRulesCoordinatorService(
            ChallengeManager challengeManager,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            Supplier<RunHealthCoordinatorService> runHealthCoordinatorServiceSupplier) {
        this.challengeManager = challengeManager;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.runHealthCoordinatorServiceSupplier = runHealthCoordinatorServiceSupplier;
    }

    /** Synchronizes keep-inventory and related world policies across worlds. */
    public void syncWorldRules() {
        boolean keepInventory =
                challengeManager.isEnabled() && challengeManager.isComponentEnabled(ChallengeComponent.KEEP_INVENTORY);
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, keepInventory);
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null) {
            worldResetManager.enforceLobbyWorldPolicies();
        }
    }

    /** Applies shared-vitals synchronization if the feature is enabled. */
    public void applySharedVitalsIfEnabled() {
        RunHealthCoordinatorService runHealthCoordinatorService = runHealthCoordinatorServiceSupplier.get();
        if (runHealthCoordinatorService != null) {
            runHealthCoordinatorService.applySharedVitalsIfEnabled();
        }
    }
}
