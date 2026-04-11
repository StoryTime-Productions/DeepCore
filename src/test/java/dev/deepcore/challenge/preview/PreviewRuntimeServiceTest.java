package dev.deepcore.challenge.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PreviewRuntimeServiceTest {

    @Test
    void configBackedGetters_clampScaleAndSpinTicks() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        when(config.getDouble("challenge.preview_hologram_block_scale", 1.0D / 32.0D))
                .thenReturn(2.0D);
        when(config.getInt("challenge.preview_hologram_spin_update_ticks", 4)).thenReturn(30);

        PreviewRuntimeService service = new PreviewRuntimeService(plugin);
        assertEquals(1.0D, service.getPreviewBlockScale());
        assertEquals(20, service.getPreviewSpinUpdateTicks());

        when(config.getDouble("challenge.preview_hologram_block_scale", 1.0D / 32.0D))
                .thenReturn(0.001D);
        when(config.getInt("challenge.preview_hologram_spin_update_ticks", 4)).thenReturn(0);
        assertEquals(0.01D, service.getPreviewBlockScale());
        assertEquals(1, service.getPreviewSpinUpdateTicks());
    }

    @Test
    void previewSounds_returnEarlyWhenNoActiveLobbyViewers() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble("challenge.preview_hologram_active_radius", 48.0D))
                .thenReturn(48.0D);

        PreviewRuntimeService service = new PreviewRuntimeService(plugin);

        World previewWorld = mock(World.class);
        Location anchor = new Location(previewWorld, 0.0D, 64.0D, 0.0D);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            service.playPreviewConstructionTickSounds(anchor, 24);
            service.playPreviewConstructionCompleteSound(anchor);
            service.playPreviewDestroyStartSound(anchor);
        }
    }

    @Test
    void applyPreviewTransform_setsDisplayTransformation() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble("challenge.preview_hologram_block_overlap", 0.02D))
                .thenReturn(0.05D);

        PreviewRuntimeService service = new PreviewRuntimeService(plugin);
        BlockDisplay display = mock(BlockDisplay.class);

        service.applyPreviewTransform(display, 0.2D, 1.5F, 2.0D);

        verify(display).setTransformation(any(org.bukkit.util.Transformation.class));
    }

    @Test
    void previewSounds_playWhenViewerIsNearbyInSameWorld() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble("challenge.preview_hologram_active_radius", 48.0D))
                .thenReturn(48.0D);

        PreviewRuntimeService service = new PreviewRuntimeService(plugin);

        World previewWorld = mock(World.class);
        java.util.UUID worldId = java.util.UUID.randomUUID();
        when(previewWorld.getUID()).thenReturn(worldId);
        Location anchor = new Location(previewWorld, 0.0D, 64.0D, 0.0D);

        Player nearby = mock(Player.class);
        when(nearby.getWorld()).thenReturn(previewWorld);
        when(nearby.getLocation()).thenReturn(new Location(previewWorld, 1.0D, 64.0D, 1.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(nearby));

            service.playPreviewConstructionTickSounds(anchor, 24);
            service.playPreviewConstructionCompleteSound(anchor);
            service.playPreviewDestroyStartSound(anchor);
        }

        verify(previewWorld, org.mockito.Mockito.atLeastOnce())
                .playSound(any(Location.class), any(Sound.class), eq(SoundCategory.PLAYERS), anyFloat(), anyFloat());
    }
}
