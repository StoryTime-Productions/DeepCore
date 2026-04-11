package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeManagerTest {

    private JavaPlugin plugin;
    private YamlConfiguration config;
    private ChallengeManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        manager = new ChallengeManager(plugin);
    }

    @Test
    void loadFromConfig_readsEnabledModeAndComponentOverrides() {
        config.set("challenge.enabled", true);
        config.set("challenge.mode", "hardcore_no_refill");
        config.set("challenge.components.shared_inventory", true);

        manager.loadFromConfig();

        assertTrue(manager.isEnabled());
        assertEquals(ChallengeMode.HARDCORE_NO_REFILL, manager.getMode());
        assertTrue(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));
    }

    @Test
    void loadFromConfig_appliesLegacyUnlimitedDeathsMigration() {
        config.set("challenge.components.unlimited_deaths", true);

        manager.loadFromConfig();

        assertFalse(manager.isComponentEnabled(ChallengeComponent.HARDCORE));
    }

    @Test
    void setMode_resetsComponentDefaults() {
        manager.setMode(ChallengeMode.HARDCORE_NO_REFILL);

        assertTrue(manager.isComponentEnabled(ChallengeComponent.HARDCORE));
        assertFalse(manager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL));
        assertFalse(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));
    }

    @Test
    void toggleComponent_flipsStoredValue() {
        assertFalse(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));

        manager.toggleComponent(ChallengeComponent.SHARED_INVENTORY);
        assertTrue(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));

        manager.toggleComponent(ChallengeComponent.SHARED_INVENTORY);
        assertFalse(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));
    }

    @Test
    void saveToConfig_writesCurrentStateAndPersists() {
        manager.setEnabled(true);
        manager.setMode(ChallengeMode.HARDCORE_SHARED_HEALTH);
        manager.setComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY, true);

        assertTrue(config.getBoolean("challenge.enabled"));
        assertEquals("hardcore_shared_health", config.getString("challenge.mode"));
        assertTrue(config.getBoolean("challenge.components.degrading_inventory"));
        verify(plugin, atLeastOnce()).saveConfig();
    }

    @Test
    void resetComponentsToModeDefaults_restoresModeBaseline() {
        manager.setMode(ChallengeMode.HARDCORE_NO_REFILL);
        manager.setComponentEnabled(ChallengeComponent.SHARED_INVENTORY, true);

        manager.resetComponentsToModeDefaults();

        assertFalse(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY));
        assertTrue(manager.isComponentEnabled(ChallengeComponent.HARDCORE));
    }

    @Test
    void getComponentToggles_returnsDefensiveCopy() {
        Map<ChallengeComponent, Boolean> copy = manager.getComponentToggles();
        copy.put(ChallengeComponent.SHARED_HEALTH, true);

        assertFalse(manager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH));
    }
}
