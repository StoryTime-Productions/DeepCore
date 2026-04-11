package dev.deepcore.challenge.portal;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Resolves run-phase respawn destinations based on recorded death worlds. */
public final class RespawnRoutingService {
    private final Map<UUID, String> pendingRespawnWorldNames = new HashMap<>();
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final Function<World, World> resolveLinkedPortalWorld;
    private final Function<World, World> resolveLinkedEndWorld;

    /**
     * Creates a respawn routing service.
     *
     * @param worldResetManagerSupplier supplier for active world reset manager
     * @param resolveLinkedPortalWorld  resolver for Nether-to-overworld linked
     *                                  world routing
     * @param resolveLinkedEndWorld     resolver for End-to-overworld linked world
     *                                  routing
     */
    public RespawnRoutingService(
            Supplier<WorldResetManager> worldResetManagerSupplier,
            Function<World, World> resolveLinkedPortalWorld,
            Function<World, World> resolveLinkedEndWorld) {
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.resolveLinkedPortalWorld = resolveLinkedPortalWorld;
        this.resolveLinkedEndWorld = resolveLinkedEndWorld;
    }

    /** Clears all pending per-player respawn world mappings. */
    public void clearPendingRespawns() {
        pendingRespawnWorldNames.clear();
    }

    /**
     * Records the world where a player died for subsequent respawn routing.
     *
     * @param playerId   UUID of the player who died
     * @param deathWorld world where the death occurred
     */
    public void recordDeathWorld(UUID playerId, World deathWorld) {
        if (playerId == null || deathWorld == null) {
            return;
        }
        pendingRespawnWorldNames.put(playerId, deathWorld.getName());
    }

    /**
     * Resolves the target respawn location for a run participant.
     *
     * @param playerId UUID of the player requiring respawn routing
     * @return resolved respawn location, or null when no target world is available
     */
    public Location resolveRunRespawnLocation(UUID playerId) {
        World deathWorld = null;
        String deathWorldName = pendingRespawnWorldNames.remove(playerId);
        if (deathWorldName != null && !deathWorldName.isBlank()) {
            deathWorld = Bukkit.getWorld(deathWorldName);
        }

        if (deathWorld == null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                Location lastDeathLocation = player.getLastDeathLocation();
                if (lastDeathLocation != null) {
                    deathWorld = lastDeathLocation.getWorld();
                }
            }
        }

        World respawnWorld = deathWorld;
        if (respawnWorld != null && respawnWorld.getEnvironment() == World.Environment.NETHER) {
            respawnWorld = resolveLinkedPortalWorld.apply(respawnWorld);
        } else if (respawnWorld != null && respawnWorld.getEnvironment() == World.Environment.THE_END) {
            respawnWorld = resolveLinkedEndWorld.apply(respawnWorld);
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (respawnWorld == null && worldResetManager != null) {
            respawnWorld = worldResetManager.getCurrentOverworld();
        }

        if (respawnWorld == null) {
            return null;
        }

        return respawnWorld.getSpawnLocation().clone().add(0.5D, 0.0D, 0.5D);
    }
}
