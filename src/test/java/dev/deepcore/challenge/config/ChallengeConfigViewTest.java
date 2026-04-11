package dev.deepcore.challenge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ChallengeConfigViewTest {

    @Test
    void accessors_returnDefaultsWhenConfigKeysMissing() {
        YamlConfiguration config = new YamlConfiguration();
        ChallengeConfigView view = newView(config);

        assertTrue(view.countdownRequiresAllReady());
        assertTrue(view.removeReadyBookOnCountdownStart());
        assertEquals(10, view.prepCountdownSeconds());
        assertEquals(120, view.degradingIntervalSeconds());
        assertEquals(5, view.degradingMinSlots());
        assertTrue(view.previewEnabled());
        assertEquals(48.0D, view.previewActiveRadius());
        assertEquals("deepcore_limbo", view.limboWorldName());
        assertEquals("deepcore_lobby_overworld", view.lobbyOverworldWorldName());
        assertEquals("deepcore_lobby_nether", view.lobbyNetherWorldName());
    }

    @Test
    void accessors_returnConfiguredValuesWhenPresent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("challenge.countdown_requires_all_ready", false);
        config.set("challenge.remove_ready_book_on_countdown_start", false);
        config.set("prep.countdown-seconds", 42);
        config.set("challenge.degrading.interval-seconds", 30);
        config.set("challenge.degrading.min-slots", 9);
        config.set("challenge.preview_hologram_enabled", false);
        config.set("challenge.preview_hologram_active_radius", 96.5D);
        config.set("reset.limbo-world-name", "limbo_custom");
        config.set("reset.lobby-overworld-world-name", "lobby_custom_overworld");
        config.set("reset.lobby-nether-world-name", "lobby_custom_nether");

        ChallengeConfigView view = newView(config);

        assertFalse(view.countdownRequiresAllReady());
        assertFalse(view.removeReadyBookOnCountdownStart());
        assertEquals(42, view.prepCountdownSeconds());
        assertEquals(30, view.degradingIntervalSeconds());
        assertEquals(9, view.degradingMinSlots());
        assertFalse(view.previewEnabled());
        assertEquals(96.5D, view.previewActiveRadius());
        assertEquals("limbo_custom", view.limboWorldName());
        assertEquals("lobby_custom_overworld", view.lobbyOverworldWorldName());
        assertEquals("lobby_custom_nether", view.lobbyNetherWorldName());
    }

    private static ChallengeConfigView newView(YamlConfiguration config) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        return new ChallengeConfigView(plugin);
    }
}
