package dev.deepcore.challenge.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.world.WorldResetManager;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class PreviewAnchorServiceTest {

    @Test
    void resolvePreviewAnchor_returnsNullWhenWorldResetManagerMissing() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewAnchorService service = new PreviewAnchorService(plugin, () -> null);
        assertNull(service.resolvePreviewAnchor());
    }

    @Test
    void resolvePreviewAnchor_prefersWorldSpecificAnchor_thenFallsBackToYOffset() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        WorldResetManager resetManager = mock(WorldResetManager.class);
        World lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("Lobby_World");
        Location lobbySpawn = new Location(lobbyWorld, 100.0D, 64.0D, 100.0D);
        when(resetManager.getConfiguredLimboSpawn()).thenReturn(lobbySpawn);

        ConfigurationSection worldAnchors = mock(ConfigurationSection.class);
        ConfigurationSection lobbyAnchor = mock(ConfigurationSection.class);

        when(config.getConfigurationSection("challenge.preview_hologram_anchor.worlds"))
                .thenReturn(worldAnchors);
        when(worldAnchors.getConfigurationSection("Lobby_World")).thenReturn(lobbyAnchor);
        when(lobbyAnchor.contains("x")).thenReturn(true);
        when(lobbyAnchor.getBoolean("enabled", true)).thenReturn(true);
        when(lobbyAnchor.getDouble("x", 100.0D)).thenReturn(120.0D);
        when(lobbyAnchor.getDouble("y", 64.0D)).thenReturn(80.0D);
        when(lobbyAnchor.getDouble("z", 100.0D)).thenReturn(140.0D);

        PreviewAnchorService service = new PreviewAnchorService(plugin, () -> resetManager);

        Location worldSpecific = service.resolvePreviewAnchor();
        assertEquals(120.0D, worldSpecific.getX());
        assertEquals(80.0D, worldSpecific.getY());
        assertEquals(140.0D, worldSpecific.getZ());

        when(worldAnchors.getConfigurationSection("Lobby_World")).thenReturn(null);
        when(worldAnchors.getConfigurationSection("lobby_world")).thenReturn(null);
        when(config.getBoolean("challenge.preview_hologram_anchor.enabled", false))
                .thenReturn(false);
        when(config.getInt("challenge.preview_hologram_base_y_offset", 1)).thenReturn(2);

        Location yOffsetAnchor = service.resolvePreviewAnchor();
        assertEquals(100.0D, yOffsetAnchor.getX());
        assertEquals(66.0D, yOffsetAnchor.getY());
        assertEquals(100.0D, yOffsetAnchor.getZ());
    }

    @Test
    void getPreferredTeleport_andTeleportToLobby_useConfiguredRules() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        WorldResetManager resetManager = mock(WorldResetManager.class);
        World lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("lobby");
        Location lobbySpawn = new Location(lobbyWorld, 0.0D, 64.0D, 0.0D);
        when(resetManager.getConfiguredLimboSpawn()).thenReturn(lobbySpawn);
        when(resetManager.shouldSpawnInLimboByDefault()).thenReturn(true);

        when(config.getConfigurationSection("challenge.preview_hologram_anchor.worlds"))
                .thenReturn(null);
        when(config.getBoolean("challenge.preview_hologram_anchor.enabled", false))
                .thenReturn(true);
        when(config.getDouble("challenge.preview_hologram_anchor.x", 0.0D)).thenReturn(4.0D);
        when(config.getDouble("challenge.preview_hologram_anchor.y", 64.0D)).thenReturn(70.0D);
        when(config.getDouble("challenge.preview_hologram_anchor.z", 0.0D)).thenReturn(8.0D);

        PreviewAnchorService service = new PreviewAnchorService(plugin, () -> resetManager);

        Location preferred = service.getPreferredLobbyTeleportLocation();
        assertEquals(4.0D, preferred.getX());
        assertEquals(71.0D, preferred.getY());
        assertEquals(8.0D, preferred.getZ());

        Player player = mock(Player.class);
        World otherWorld = mock(World.class);
        when(player.getWorld()).thenReturn(otherWorld);

        service.teleportToLobbyIfConfigured(player);
        verify(player)
                .teleport(
                        org.mockito.ArgumentMatchers.any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));

        when(player.getWorld()).thenReturn(lobbyWorld);
        service.teleportToLobbyIfConfigured(player);
        verify(player, org.mockito.Mockito.times(1))
                .teleport(
                        org.mockito.ArgumentMatchers.any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void resolveLobbyRespawnLocation_handlesNullAndWorldSpawnFallback() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        WorldResetManager resetManager = mock(WorldResetManager.class);
        when(resetManager.getConfiguredLimboSpawn()).thenReturn(null);

        PreviewAnchorService service = new PreviewAnchorService(plugin, () -> resetManager);

        assertNull(service.resolveLobbyRespawnLocation(null));

        World deathWorld = mock(World.class);
        World preferredWorld = mock(World.class);
        UUID deathId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(deathWorld.getUID()).thenReturn(deathId);
        when(preferredWorld.getUID()).thenReturn(otherId);

        Location deathSpawn = new Location(deathWorld, 10.0D, 64.0D, 20.0D);
        when(deathWorld.getSpawnLocation()).thenReturn(deathSpawn);

        when(resetManager.getConfiguredLimboSpawn()).thenReturn(new Location(preferredWorld, 1.0D, 2.0D, 3.0D));
        when(config.getConfigurationSection(anyString())).thenReturn(null);
        when(config.getBoolean("challenge.preview_hologram_anchor.enabled", false))
                .thenReturn(false);
        when(config.getInt("challenge.preview_hologram_base_y_offset", 1)).thenReturn(1);

        Location fallback = service.resolveLobbyRespawnLocation(deathWorld);
        assertEquals(10.5D, fallback.getX());
        assertEquals(64.0D, fallback.getY());
        assertEquals(20.5D, fallback.getZ());
    }
}
