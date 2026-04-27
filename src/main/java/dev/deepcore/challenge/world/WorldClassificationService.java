package dev.deepcore.challenge.world;

import dev.deepcore.challenge.config.ChallengeConfigView;
import java.util.function.Supplier;
import org.bukkit.World;

/**
 * Centralizes world classification rules for lobby/limbo detection.
 */
public final class WorldClassificationService {
    private final ChallengeConfigView configView;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;

    /**
     * Creates a world classification service.
     *
     * @param configView                challenge config value accessor
     * @param worldResetManagerSupplier supplier for current world reset manager
     */
    public WorldClassificationService(
            ChallengeConfigView configView, Supplier<WorldResetManager> worldResetManagerSupplier) {
        this.configView = configView;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
    }

    public boolean isLobbyOrLimboWorld(World world) {
        if (world == null) {
            return false;
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null && worldResetManager.isLobbyWorld(world)) {
            return true;
        }

        String configuredLimboName = configView.limboWorldName();
        String configuredLobbyOverworld = configView.lobbyOverworldWorldName();
        String configuredLobbyNether = configView.lobbyNetherWorldName();
        return world.getName().equalsIgnoreCase(configuredLimboName)
                || world.getName().equalsIgnoreCase(configuredLobbyOverworld)
                || world.getName().equalsIgnoreCase(configuredLobbyNether);
    }

    /**
     * Returns whether the world is the configured training world.
     *
     * @param world world to classify
     * @return true when this is the configured training world
     */
    public boolean isTrainingWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(configView.trainingWorldName());
    }

    /**
     * Returns whether prep borders should be suppressed in the given world.
     *
     * @param world world to classify
     * @return true when prep borders should not be shown
     */
    public boolean isPrepBorderExemptWorld(World world) {
        if (world == null) {
            return false;
        }

        if (isLobbyOrLimboWorld(world)) {
            return true;
        }

        return isTrainingWorld(world);
    }
}
