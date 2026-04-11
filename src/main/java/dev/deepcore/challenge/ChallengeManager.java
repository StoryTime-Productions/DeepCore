package dev.deepcore.challenge;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stores and persists challenge enablement, mode, and component toggle state.
 */
public final class ChallengeManager {
    private static final String ENABLED_PATH = "challenge.enabled";
    private static final String MODE_PATH = "challenge.mode";
    private static final String COMPONENTS_PATH = "challenge.components";
    private static final String LEGACY_UNLIMITED_DEATHS_PATH = "challenge.components.unlimited_deaths";

    private final JavaPlugin plugin;
    private boolean enabled;
    private ChallengeMode mode;
    private final Map<ChallengeComponent, Boolean> componentToggles;

    /**
     * Creates a manager with default challenge settings.
     *
     * @param plugin plugin providing configuration storage
     */
    public ChallengeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
        this.mode = ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS;
        this.componentToggles = new EnumMap<>(ChallengeComponent.class);
        applyModeDefaults();
    }

    /**
     * Loads challenge settings from plugin configuration and applies migrations.
     */
    public void loadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(ENABLED_PATH, false);

        String configuredMode = config.getString(MODE_PATH, ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS.key());
        mode = ChallengeMode.fromKey(configuredMode).orElse(ChallengeMode.KEEP_INVENTORY_UNLIMITED_DEATHS);

        applyModeDefaults();
        for (ChallengeComponent component : ChallengeComponent.values()) {
            String path = componentPath(component);
            boolean configuredValue = config.getBoolean(path, componentToggles.get(component));
            componentToggles.put(component, configuredValue);
        }

        // Backward-compat migration: old unlimited_deaths=true means hardcore=false.
        if (config.isSet(LEGACY_UNLIMITED_DEATHS_PATH) && !config.isSet(componentPath(ChallengeComponent.HARDCORE))) {
            boolean legacyUnlimitedDeaths = config.getBoolean(LEGACY_UNLIMITED_DEATHS_PATH, true);
            componentToggles.put(ChallengeComponent.HARDCORE, !legacyUnlimitedDeaths);
        }

        saveToConfig();
    }

    /**
     * Persists current challenge state back to plugin configuration.
     */
    public void saveToConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set(ENABLED_PATH, enabled);
        config.set(MODE_PATH, mode.key());
        for (ChallengeComponent component : ChallengeComponent.values()) {
            config.set(componentPath(component), componentToggles.get(component));
        }
        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChallengeMode getMode() {
        return mode;
    }

    public boolean isComponentEnabled(ChallengeComponent component) {
        return componentToggles.getOrDefault(component, false);
    }

    public Map<ChallengeComponent, Boolean> getComponentToggles() {
        return new EnumMap<>(componentToggles);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveToConfig();
    }

    public void setMode(ChallengeMode mode) {
        this.mode = mode;
        applyModeDefaults();
        saveToConfig();
    }

    /**
     * Enables or disables a specific challenge component and persists settings.
     *
     * @param component challenge component to update
     * @param enabled   true to enable the component, false to disable it
     */
    public void setComponentEnabled(ChallengeComponent component, boolean enabled) {
        componentToggles.put(component, enabled);
        saveToConfig();
    }

    /**
     * Toggles a component on/off and persists the updated state.
     *
     * @param component challenge component to toggle
     */
    public void toggleComponent(ChallengeComponent component) {
        setComponentEnabled(component, !isComponentEnabled(component));
    }

    private void applyModeDefaults() {
        for (ChallengeComponent component : ChallengeComponent.values()) {
            componentToggles.put(component, mode.defaultComponents().contains(component));
        }
    }

    private String componentPath(ChallengeComponent component) {
        return COMPONENTS_PATH + "." + component.key();
    }

    /**
     * Resets all component toggles to defaults for the active challenge mode.
     */
    public void resetComponentsToModeDefaults() {
        applyModeDefaults();
        saveToConfig();
    }
}
