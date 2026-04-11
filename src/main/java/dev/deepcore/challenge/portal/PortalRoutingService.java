package dev.deepcore.challenge.portal;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Resolves linked portal destinations and guards end-portal transit cooldowns.
 */
public final class PortalRoutingService {
    private static final long END_PORTAL_TRANSIT_COOLDOWN_MILLIS = 2000L;
    private static final int END_PLATFORM_CENTER_X = 100;
    private static final int END_PLATFORM_CENTER_Z = 0;
    private static final int END_PLATFORM_BLOCK_Y = 49;
    private static final int END_PLATFORM_RADIUS = 2;

    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final Map<UUID, Long> endPortalCooldownUntilMillis = new HashMap<>();

    /**
     * Creates a portal router bound to the current world reset manager provider.
     *
     * @param worldResetManagerSupplier supplier for the active world reset manager
     */
    public PortalRoutingService(Supplier<WorldResetManager> worldResetManagerSupplier) {
        this.worldResetManagerSupplier = worldResetManagerSupplier;
    }

    /** Clears all tracked end-portal cooldown timestamps. */
    public void clearTransitCooldowns() {
        endPortalCooldownUntilMillis.clear();
    }

    /**
     * Resolves the Nether/Overworld counterpart world for a portal transition.
     *
     * @param fromWorld world from which portal travel is originating
     * @return linked counterpart world, or null when not resolvable
     */
    public World resolveLinkedPortalWorld(World fromWorld) {
        if (fromWorld == null) {
            return null;
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null) {
            World activeOverworld = worldResetManager.getCurrentOverworld();
            if (activeOverworld != null) {
                World activeNether = Bukkit.getWorld(activeOverworld.getName() + "_nether");
                if (activeNether != null) {
                    if (fromWorld.getUID().equals(activeOverworld.getUID())) {
                        return activeNether;
                    }
                    if (fromWorld.getUID().equals(activeNether.getUID())) {
                        return activeOverworld;
                    }
                }
            }
        }

        String fromName = fromWorld.getName();
        if (fromWorld.getEnvironment() == World.Environment.NETHER || fromName.endsWith("_nether")) {
            String overworldName = fromName.endsWith("_nether")
                    ? fromName.substring(0, fromName.length() - "_nether".length())
                    : fromName;
            return Bukkit.getWorld(overworldName);
        }

        String netherName = fromName + "_nether";
        return Bukkit.getWorld(netherName);
    }

    /**
     * Resolves the End/Overworld counterpart world for a portal transition.
     *
     * @param fromWorld world from which portal travel is originating
     * @return linked counterpart world, or null when not resolvable
     */
    public World resolveLinkedEndWorld(World fromWorld) {
        if (fromWorld == null) {
            return null;
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager != null) {
            World activeOverworld = worldResetManager.getCurrentOverworld();
            if (activeOverworld != null) {
                World activeNether = Bukkit.getWorld(activeOverworld.getName() + "_nether");
                World activeEnd = Bukkit.getWorld(activeOverworld.getName() + "_the_end");
                if (activeEnd != null) {
                    if (fromWorld.getUID().equals(activeEnd.getUID())) {
                        return activeOverworld;
                    }
                    if (fromWorld.getUID().equals(activeOverworld.getUID())
                            || (activeNether != null && fromWorld.getUID().equals(activeNether.getUID()))) {
                        return activeEnd;
                    }
                }
            }
        }

        String fromName = fromWorld.getName();
        if (fromWorld.getEnvironment() == World.Environment.THE_END || fromName.endsWith("_the_end")) {
            String overworldName = fromName.endsWith("_the_end")
                    ? fromName.substring(0, fromName.length() - "_the_end".length())
                    : fromName;
            return Bukkit.getWorld(overworldName);
        }

        String baseName =
                fromName.endsWith("_nether") ? fromName.substring(0, fromName.length() - "_nether".length()) : fromName;
        String endName = baseName + "_the_end";
        return Bukkit.getWorld(endName);
    }

    /**
     * Builds a portal destination using horizontal coordinate scaling between
     * dimensions.
     *
     * @param source          source location used for coordinate mapping
     * @param targetWorld     destination world
     * @param horizontalScale scaling factor to apply to X/Z coordinates
     * @return calculated portal target location, or null when inputs are invalid
     */
    public Location buildLinkedPortalTarget(Location source, World targetWorld, double horizontalScale) {
        if (source == null || targetWorld == null) {
            return null;
        }

        double targetX = source.getX() * horizontalScale;
        double targetZ = source.getZ() * horizontalScale;
        int targetBlockX = (int) Math.floor(targetX);
        int targetBlockZ = (int) Math.floor(targetZ);

        int targetY = resolveSafePortalTargetY(targetWorld, targetBlockX, targetBlockZ);
        return new Location(targetWorld, targetX, targetY, targetZ, source.getYaw(), source.getPitch());
    }

    /**
     * Resolves a safe destination for End portal travel between linked worlds.
     *
     * @param fromWorld   source world where portal travel started
     * @param targetWorld destination world to enter
     * @return safe destination location in the target world, or null when
     *         unavailable
     */
    public Location resolveEndPortalTarget(World fromWorld, World targetWorld) {
        if (targetWorld == null) {
            return null;
        }

        if (fromWorld != null && fromWorld.getEnvironment() == World.Environment.THE_END) {
            return targetWorld.getSpawnLocation().clone().add(0.5D, 0.0D, 0.5D);
        }

        ensureEndSpawnPlatform(targetWorld);
        return new Location(
                targetWorld, END_PLATFORM_CENTER_X + 0.5D, END_PLATFORM_BLOCK_Y + 1.0D, END_PLATFORM_CENTER_Z + 0.5D);
    }

    /**
     * Applies End portal transit if the player is eligible and cooldown permits.
     *
     * @param player              player attempting End portal transit
     * @param runningPhase        whether the challenge is actively running
     * @param limboWorldPredicate predicate identifying worlds where transit is
     *                            blocked
     * @return true when transit was applied
     */
    public boolean tryHandleEndPortalTransit(
            Player player, boolean runningPhase, Predicate<World> limboWorldPredicate) {
        if (player == null || !runningPhase || limboWorldPredicate.test(player.getWorld())) {
            return false;
        }

        if (worldResetManagerSupplier.get() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = endPortalCooldownUntilMillis.getOrDefault(player.getUniqueId(), 0L);
        if (now < cooldownUntil || !isInsideEndPortal(player)) {
            return false;
        }

        World fromWorld = player.getWorld();
        World targetWorld = resolveLinkedEndWorld(fromWorld);
        Location target = resolveEndPortalTarget(fromWorld, targetWorld);
        if (target == null) {
            return false;
        }

        endPortalCooldownUntilMillis.put(player.getUniqueId(), now + END_PORTAL_TRANSIT_COOLDOWN_MILLIS);
        player.teleport(target, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        return true;
    }

    private int resolveSafePortalTargetY(World world, int blockX, int blockZ) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;

        // Nether roof bedrock can cause highest-block logic to place players above the
        // playable area. Search down from below the roof for two-block headroom.
        if (world.getEnvironment() == World.Environment.NETHER) {
            int searchTop = Math.min(maxY, 122);
            for (int y = searchTop; y >= minY + 1; y--) {
                Material feet = world.getBlockAt(blockX, y, blockZ).getType();
                Material head = world.getBlockAt(blockX, y + 1, blockZ).getType();
                Material below = world.getBlockAt(blockX, y - 1, blockZ).getType();

                boolean hasHeadroom = feet.isAir() && head.isAir();
                boolean safeFloor = below.isSolid() && below != Material.BEDROCK && below != Material.LAVA;
                if (hasHeadroom && safeFloor) {
                    return y;
                }
            }

            return Math.max(minY, Math.min(maxY, 64));
        }

        int highestY = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING);
        return Math.max(minY, Math.min(maxY, highestY + 1));
    }

    private void ensureEndSpawnPlatform(World endWorld) {
        if (endWorld == null || endWorld.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        for (int x = END_PLATFORM_CENTER_X - END_PLATFORM_RADIUS;
                x <= END_PLATFORM_CENTER_X + END_PLATFORM_RADIUS;
                x++) {
            for (int z = END_PLATFORM_CENTER_Z - END_PLATFORM_RADIUS;
                    z <= END_PLATFORM_CENTER_Z + END_PLATFORM_RADIUS;
                    z++) {
                endWorld.getBlockAt(x, END_PLATFORM_BLOCK_Y, z).setType(Material.OBSIDIAN, false);
                endWorld.getBlockAt(x, END_PLATFORM_BLOCK_Y + 1, z).setType(Material.AIR, false);
                endWorld.getBlockAt(x, END_PLATFORM_BLOCK_Y + 2, z).setType(Material.AIR, false);
                endWorld.getBlockAt(x, END_PLATFORM_BLOCK_Y + 3, z).setType(Material.AIR, false);
            }
        }
    }

    private boolean isInsideEndPortal(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        Material feet = world.getBlockAt(location).getType();
        Material below = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ())
                .getType();
        return feet == Material.END_PORTAL || below == Material.END_PORTAL;
    }
}
