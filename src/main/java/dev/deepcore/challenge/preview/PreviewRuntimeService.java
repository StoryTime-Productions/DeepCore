package dev.deepcore.challenge.preview;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Provides runtime helpers for preview scaling, transforms, and sounds. */
public final class PreviewRuntimeService {
    private static final String PREVIEW_BLOCK_SCALE_PATH = "challenge.preview_hologram_block_scale";
    private static final String PREVIEW_BLOCK_OVERLAP_PATH = "challenge.preview_hologram_block_overlap";
    private static final String PREVIEW_SPIN_UPDATE_TICKS_PATH = "challenge.preview_hologram_spin_update_ticks";
    private static final String PREVIEW_ACTIVE_RADIUS_PATH = "challenge.preview_hologram_active_radius";
    private static final float PREVIEW_SOUND_VOLUME = 0.30F;
    private static final int PREVIEW_SAMPLE_SIZE = 32;

    private final JavaPlugin plugin;

    /**
     * Creates a preview runtime service.
     *
     * @param plugin plugin instance used for config-backed runtime behavior
     */
    public PreviewRuntimeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the configured preview block scale within safe clamped bounds.
     *
     * @return clamped preview block scale factor
     */
    public double getPreviewBlockScale() {
        double scale = plugin.getConfig().getDouble(PREVIEW_BLOCK_SCALE_PATH, 1.0D / PREVIEW_SAMPLE_SIZE);
        return Math.max(0.01D, Math.min(1.0D, scale));
    }

    /**
     * Returns the configured preview spin interpolation tick interval.
     *
     * @return clamped interpolation tick interval used for preview spin updates
     */
    public int getPreviewSpinUpdateTicks() {
        return Math.max(1, Math.min(20, plugin.getConfig().getInt(PREVIEW_SPIN_UPDATE_TICKS_PATH, 4)));
    }

    private boolean hasActiveLobbyViewers(Location previewAnchor) {
        if (previewAnchor == null || previewAnchor.getWorld() == null) {
            return false;
        }

        double radius = Math.max(4.0D, plugin.getConfig().getDouble(PREVIEW_ACTIVE_RADIUS_PATH, 48.0D));
        double radiusSquared = radius * radius;
        World previewWorld = previewAnchor.getWorld();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getUID().equals(previewWorld.getUID())) {
                continue;
            }

            if (player.getLocation().distanceSquared(previewAnchor) <= radiusSquared) {
                return true;
            }
        }

        return false;
    }

    /**
     * Plays per-tick construction sounds for nearby active lobby viewers.
     *
     * @param previewAnchor   anchor location used for proximity filtering and sound
     *                        playback
     * @param spawnedThisTick number of blocks spawned this tick
     */
    public void playPreviewConstructionTickSounds(Location previewAnchor, int spawnedThisTick) {
        if (!hasActiveLobbyViewers(previewAnchor)) {
            return;
        }

        int pickupBursts = Math.min(3, Math.max(1, spawnedThisTick / 8));
        for (int i = 0; i < pickupBursts; i++) {
            float pickupPitch = 1.35F + ThreadLocalRandom.current().nextFloat() * 0.35F;
            playPreviewSound(previewAnchor, Sound.ENTITY_ITEM_PICKUP, pickupPitch);
        }

        float bubblePitch = 1.05F + ThreadLocalRandom.current().nextFloat() * 0.20F;
        playPreviewSound(previewAnchor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, bubblePitch);
    }

    /**
     * Plays completion sounds when the preview construction finishes.
     *
     * @param previewAnchor anchor location used for proximity filtering and sound
     *                      playback
     */
    public void playPreviewConstructionCompleteSound(Location previewAnchor) {
        if (!hasActiveLobbyViewers(previewAnchor)) {
            return;
        }

        playPreviewSound(previewAnchor, Sound.ENTITY_PLAYER_HURT, 0.85F);
        for (int i = 0; i < 3; i++) {
            float xpPitch = 1.30F + ThreadLocalRandom.current().nextFloat() * 0.35F;
            playPreviewSound(previewAnchor, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, xpPitch);
        }
    }

    /**
     * Plays the destroy-start sound cue for nearby lobby viewers.
     *
     * @param previewAnchor anchor location used for proximity filtering and sound
     *                      playback
     */
    public void playPreviewDestroyStartSound(Location previewAnchor) {
        if (!hasActiveLobbyViewers(previewAnchor)) {
            return;
        }

        playPreviewSound(previewAnchor, Sound.ENTITY_GENERIC_EXPLODE, 1.00F);
    }

    /**
     * Applies scale, rotation, and vertical offset transform to a preview display.
     *
     * @param display         block display entity to transform
     * @param blockScale      base block scale value
     * @param rotationRadians yaw rotation in radians
     * @param yOffset         vertical offset from anchor origin
     */
    public void applyPreviewTransform(BlockDisplay display, double blockScale, float rotationRadians, double yOffset) {
        double overlap = getPreviewBlockOverlap();
        double inflatedScale = blockScale * (1.0D + overlap);
        display.setTransformation(new Transformation(
                new Vector3f((float) (-inflatedScale * 0.5D), (float) yOffset, (float) (-inflatedScale * 0.5D)),
                new Quaternionf().rotateY(-rotationRadians),
                new Vector3f((float) inflatedScale, (float) inflatedScale, (float) inflatedScale),
                new Quaternionf()));
    }

    private double getPreviewBlockOverlap() {
        double overlap = plugin.getConfig().getDouble(PREVIEW_BLOCK_OVERLAP_PATH, 0.02D);
        return Math.max(0.0D, Math.min(0.25D, overlap));
    }

    private void playPreviewSound(Location previewAnchor, Sound sound, float pitch) {
        if (previewAnchor == null || previewAnchor.getWorld() == null) {
            return;
        }

        previewAnchor.getWorld().playSound(previewAnchor, sound, SoundCategory.PLAYERS, PREVIEW_SOUND_VOLUME, pitch);
    }
}
