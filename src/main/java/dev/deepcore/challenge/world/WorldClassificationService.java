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
}
