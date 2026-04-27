package dev.deepcore.challenge.session;

import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Manages prep-area border assignment and movement clamping.
 */
public final class PrepAreaService {
    private final int prepAreaDiameterBlocks;
    private final int prepAreaRadiusBlocks;

    /**
     * Creates a prep-area service for the configured border diameter.
     *
     * @param prepAreaDiameterBlocks prep border diameter in blocks
     */
    public PrepAreaService(int prepAreaDiameterBlocks) {
        this.prepAreaDiameterBlocks = prepAreaDiameterBlocks;
        this.prepAreaRadiusBlocks = prepAreaDiameterBlocks / 2;
    }

    /**
     * Applies prep borders to all online players when the run is not active.
     *
     * @param runningPhase whether the challenge is currently in running phase
     * @param isLobbyWorld predicate identifying lobby worlds where borders should
     *                     be cleared
     */
    public void applyBordersToOnlinePlayers(boolean runningPhase, Predicate<World> isLobbyWorld) {
        if (runningPhase) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyBorder(player, runningPhase, isLobbyWorld);
        }
    }

    /** Clears per-player world borders for all online players. */
    public void clearBorders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setWorldBorder(null);
        }
    }

    /**
     * Applies or clears the prep border for a single player.
     *
     * @param player       player whose per-player border should be updated
     * @param runningPhase whether the challenge is currently in running phase
     * @param isLobbyWorld predicate identifying lobby worlds where borders should
     *                     be cleared
     */
    public void applyBorder(Player player, boolean runningPhase, Predicate<World> isLobbyWorld) {
        player.setWorldBorder(null);
    }

    /**
     * Returns whether a location lies inside the configured prep area bounds.
     *
     * @param location location to test
     * @return true when location is inside prep bounds
     */
    public boolean isWithinPrepArea(Location location) {
        Location spawn = location.getWorld().getSpawnLocation();
        int minX = spawn.getBlockX() - prepAreaRadiusBlocks;
        int maxX = spawn.getBlockX() + prepAreaRadiusBlocks;
        int minZ = spawn.getBlockZ() - prepAreaRadiusBlocks;
        int maxZ = spawn.getBlockZ() + prepAreaRadiusBlocks;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Clamps a location to remain within the configured prep area bounds.
     *
     * @param location location to clamp
     * @return clamped location guaranteed to stay within prep bounds
     */
    public Location clampToPrepArea(Location location) {
        Location spawn = location.getWorld().getSpawnLocation();
        double minX = spawn.getBlockX() - prepAreaRadiusBlocks;
        double maxX = spawn.getBlockX() + prepAreaRadiusBlocks + 0.999D;
        double minZ = spawn.getBlockZ() - prepAreaRadiusBlocks;
        double maxZ = spawn.getBlockZ() + prepAreaRadiusBlocks + 0.999D;

        Location clamped = location.clone();
        clamped.setX(Math.max(minX, Math.min(maxX, location.getX())));
        clamped.setZ(Math.max(minZ, Math.min(maxZ, location.getZ())));
        return clamped;
    }
}
