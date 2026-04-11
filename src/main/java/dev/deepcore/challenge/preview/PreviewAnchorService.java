package dev.deepcore.challenge.preview;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves preview anchors and lobby teleport/respawn locations. */
public final class PreviewAnchorService {
    private static final String PREVIEW_BASE_Y_OFFSET_PATH = "challenge.preview_hologram_base_y_offset";
    private static final String PREVIEW_ANCHOR_ENABLED_PATH = "challenge.preview_hologram_anchor.enabled";
    private static final String PREVIEW_ANCHOR_X_PATH = "challenge.preview_hologram_anchor.x";
    private static final String PREVIEW_ANCHOR_Y_PATH = "challenge.preview_hologram_anchor.y";
    private static final String PREVIEW_ANCHOR_Z_PATH = "challenge.preview_hologram_anchor.z";
    private static final String PREVIEW_ANCHOR_WORLDS_PATH = "challenge.preview_hologram_anchor.worlds";

    private final JavaPlugin plugin;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;

    /**
     * Creates a preview anchor service.
     *
     * @param plugin                    plugin instance used for configuration
     *                                  access
     * @param worldResetManagerSupplier supplier for active world reset manager
     */
    public PreviewAnchorService(JavaPlugin plugin, Supplier<WorldResetManager> worldResetManagerSupplier) {
        this.plugin = plugin;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
    }

    /**
     * Resolves the configured preview anchor for the active lobby world.
     *
     * @return preview anchor location, or null when no lobby context is available
     */
    public Location resolvePreviewAnchor() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return null;
        }

        Location lobbySpawn = worldResetManager.getConfiguredLimboSpawn();
        if (lobbySpawn == null) {
            return null;
        }

        ConfigurationSection worldAnchors = plugin.getConfig().getConfigurationSection(PREVIEW_ANCHOR_WORLDS_PATH);
        if (worldAnchors != null && lobbySpawn.getWorld() != null) {
            String worldName = lobbySpawn.getWorld().getName();
            ConfigurationSection worldAnchor = worldAnchors.getConfigurationSection(worldName);
            if (worldAnchor == null) {
                worldAnchor = worldAnchors.getConfigurationSection(worldName.toLowerCase(Locale.ROOT));
            }

            if (worldAnchor != null) {
                boolean hasCoords = worldAnchor.contains("x") || worldAnchor.contains("y") || worldAnchor.contains("z");
                boolean worldAnchorEnabled = worldAnchor.getBoolean("enabled", hasCoords);
                if (worldAnchorEnabled) {
                    double anchorX = worldAnchor.getDouble("x", lobbySpawn.getX());
                    double anchorY = worldAnchor.getDouble("y", lobbySpawn.getY());
                    double anchorZ = worldAnchor.getDouble("z", lobbySpawn.getZ());
                    return new Location(lobbySpawn.getWorld(), anchorX, anchorY, anchorZ);
                }
            }
        }

        if (plugin.getConfig().getBoolean(PREVIEW_ANCHOR_ENABLED_PATH, false)) {
            double anchorX = plugin.getConfig().getDouble(PREVIEW_ANCHOR_X_PATH, lobbySpawn.getX());
            double anchorY = plugin.getConfig().getDouble(PREVIEW_ANCHOR_Y_PATH, lobbySpawn.getY());
            double anchorZ = plugin.getConfig().getDouble(PREVIEW_ANCHOR_Z_PATH, lobbySpawn.getZ());
            return new Location(lobbySpawn.getWorld(), anchorX, anchorY, anchorZ);
        }

        int yOffset = plugin.getConfig().getInt(PREVIEW_BASE_Y_OFFSET_PATH, 1);
        return lobbySpawn.clone().add(0.0D, yOffset, 0.0D);
    }

    /**
     * Returns the preferred lobby teleport location derived from preview anchor
     * settings.
     *
     * @return preferred lobby teleport location, or null when unavailable
     */
    public Location getPreferredLobbyTeleportLocation() {
        Location previewAnchor = resolvePreviewAnchor();
        if (previewAnchor != null) {
            return previewAnchor.clone().add(0.0D, 1.0D, 0.0D);
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return null;
        }
        return worldResetManager.getConfiguredLimboSpawn();
    }

    /**
     * Teleports a player to lobby when default limbo spawning is configured.
     *
     * @param player player to relocate to lobby
     */
    public void teleportToLobbyIfConfigured(Player player) {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null || !worldResetManager.shouldSpawnInLimboByDefault()) {
            return;
        }

        Location lobbySpawn = getPreferredLobbyTeleportLocation();
        if (lobbySpawn == null) {
            return;
        }

        World playerWorld = player.getWorld();
        if (playerWorld.equals(lobbySpawn.getWorld())) {
            return;
        }

        player.teleport(lobbySpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    /**
     * Resolves a lobby respawn location for deaths occurring in a lobby world.
     *
     * @param deathWorld world where the death occurred
     * @return respawn location in lobby context, or null when death world is
     *         unavailable
     */
    public Location resolveLobbyRespawnLocation(World deathWorld) {
        if (deathWorld == null) {
            return null;
        }

        Location preferredLobby = getPreferredLobbyTeleportLocation();
        if (preferredLobby != null
                && preferredLobby.getWorld() != null
                && preferredLobby.getWorld().getUID().equals(deathWorld.getUID())) {
            return preferredLobby;
        }

        return deathWorld.getSpawnLocation().clone().add(0.5D, 0.0D, 0.5D);
    }
}
