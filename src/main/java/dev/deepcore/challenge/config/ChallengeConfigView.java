package dev.deepcore.challenge.config;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed accessors for challenge/session configuration values.
 */
public final class ChallengeConfigView {
    private final JavaPlugin plugin;

    /**
     * Creates a typed configuration accessor for the plugin config.
     *
     * @param plugin plugin whose configuration should be queried
     */
    public ChallengeConfigView(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns whether countdown start requires all online players to be ready.
     *
     * @return true when countdown should wait for all online players
     */
    public boolean countdownRequiresAllReady() {
        return plugin.getConfig().getBoolean("challenge.countdown_requires_all_ready", true);
    }

    /**
     * Returns whether prep books are removed when countdown starts.
     *
     * @return true when prep books should be removed at countdown start
     */
    public boolean removeReadyBookOnCountdownStart() {
        return plugin.getConfig().getBoolean("challenge.remove_ready_book_on_countdown_start", true);
    }

    /**
     * Returns prep countdown duration in seconds.
     *
     * @return configured prep countdown length in seconds
     */
    public int prepCountdownSeconds() {
        return plugin.getConfig().getInt("prep.countdown-seconds", 10);
    }

    /**
     * Returns degrading inventory interval in seconds.
     *
     * @return configured degrading tick interval in seconds
     */
    public int degradingIntervalSeconds() {
        return plugin.getConfig().getInt("challenge.degrading.interval-seconds", 120);
    }

    /**
     * Returns minimum allowed inventory slots for degrading mode.
     *
     * @return lower bound for allowed inventory slots
     */
    public int degradingMinSlots() {
        return plugin.getConfig().getInt("challenge.degrading.min-slots", 5);
    }

    /**
     * Returns whether preview hologram rendering is enabled.
     *
     * @return true when lobby preview holograms are enabled
     */
    public boolean previewEnabled() {
        return plugin.getConfig().getBoolean("challenge.preview_hologram_enabled", true);
    }

    /**
     * Returns active radius for preview-related interactions.
     *
     * @return preview interaction radius in blocks
     */
    public double previewActiveRadius() {
        return plugin.getConfig().getDouble("challenge.preview_hologram_active_radius", 48.0D);
    }

    /**
     * Returns configured limbo world name.
     *
     * @return world name used as limbo fallback
     */
    public String limboWorldName() {
        return plugin.getConfig().getString("reset.limbo-world-name", "deepcore_limbo");
    }

    /**
     * Returns configured lobby overworld name.
     *
     * @return world name used for lobby overworld
     */
    public String lobbyOverworldWorldName() {
        return plugin.getConfig().getString("reset.lobby-overworld-world-name", "deepcore_lobby_overworld");
    }

    /**
     * Returns configured lobby nether world name.
     *
     * @return world name used for lobby nether
     */
    public String lobbyNetherWorldName() {
        return plugin.getConfig().getString("reset.lobby-nether-world-name", "deepcore_lobby_nether");
    }
}
