package dev.deepcore.challenge;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Map;
import org.bukkit.command.CommandSender;

/**
 * Facade for challenge command orchestration across manager/session/world
 * flows.
 */
public final class ChallengeAdminFacade {
    private final DeepCorePlugin plugin;
    private final ChallengeManager challengeManager;
    private final ChallengeSessionManager challengeSessionManager;
    private final WorldResetManager worldResetManager;
    private final DeepCoreLogger logger;

    /**
     * Creates an admin facade wiring challenge manager, session, and world reset
     * services.
     *
     * @param plugin                  plugin instance used for config operations
     * @param challengeManager        challenge configuration manager
     * @param challengeSessionManager session lifecycle coordinator
     * @param worldResetManager       world reset orchestration service
     * @param logger                  logger used for command and config operations
     */
    public ChallengeAdminFacade(
            DeepCorePlugin plugin,
            ChallengeManager challengeManager,
            ChallengeSessionManager challengeSessionManager,
            WorldResetManager worldResetManager,
            DeepCoreLogger logger) {
        this.plugin = plugin;
        this.challengeManager = challengeManager;
        this.challengeSessionManager = challengeSessionManager;
        this.worldResetManager = worldResetManager;
        this.logger = logger;
    }

    /**
     * Returns whether challenge settings can currently be edited.
     *
     * @return true when session state allows settings modifications
     */
    public boolean canEditSettings() {
        return challengeSessionManager.canEditSettings();
    }

    /**
     * Enables or disables challenge mode and reapplies world rules.
     *
     * @param enabled true to enable challenge mode, false to disable it
     */
    public void setEnabled(boolean enabled) {
        challengeManager.setEnabled(enabled);
        challengeSessionManager.syncWorldRules();
    }

    /**
     * Sets the active challenge mode and reapplies dependent state.
     *
     * @param mode challenge mode to activate
     */
    public void setMode(ChallengeMode mode) {
        challengeManager.setMode(mode);
        challengeSessionManager.syncWorldRules();
        challengeSessionManager.applySharedVitalsIfEnabled();
    }

    /** Resets component toggles to defaults of the active mode. */
    public void resetComponentsToModeDefaults() {
        challengeManager.resetComponentsToModeDefaults();
        challengeSessionManager.applySharedVitalsIfEnabled();
    }

    /**
     * Applies a component operation (`on`, `off`, or `toggle`).
     *
     * @param component challenge component to mutate
     * @param operation requested operation name
     * @throws IllegalArgumentException when operation is not one of on/off/toggle
     */
    public void applyComponentOperation(ChallengeComponent component, String operation) {
        switch (operation) {
            case "on" -> challengeManager.setComponentEnabled(component, true);
            case "off" -> challengeManager.setComponentEnabled(component, false);
            case "toggle" -> challengeManager.toggleComponent(component);
            default -> throw new IllegalArgumentException("Unsupported component operation: " + operation);
        }

        challengeSessionManager.syncWorldRules();
        challengeSessionManager.applySharedVitalsIfEnabled();
    }

    /**
     * Returns whether the session is currently in prep phase.
     *
     * @return true when the challenge session is in prep phase
     */
    public boolean isPrepPhase() {
        return challengeSessionManager.isPrepPhase();
    }

    /**
     * Returns whether the session is currently in running phase.
     *
     * @return true when the challenge session is actively running
     */
    public boolean isRunningPhase() {
        return challengeSessionManager.isRunningPhase();
    }

    /**
     * Returns whether the session is currently in paused phase.
     *
     * @return true when the challenge session is paused
     */
    public boolean isPausedPhase() {
        return challengeSessionManager.isPausedPhase();
    }

    /** Ends an active challenge and transitions back to prep state. */
    public void endChallengeAndReturnToPrep() {
        challengeSessionManager.endChallengeAndReturnToPrep();
    }

    /**
     * Stops challenge flow and triggers a world reset.
     *
     * @param sender command sender requesting the stop action
     */
    public void stopChallenge(CommandSender sender) {
        if (challengeSessionManager.isPrepPhase()) {
            worldResetManager.resetThreeWorlds(sender);
            return;
        }

        worldResetManager.selectRandomLobbyWorld();
        worldResetManager.resetThreeWorlds(sender);
    }

    /**
     * Attempts to pause the active challenge run.
     *
     * @param sender command sender requesting pause
     * @return true when pause was accepted and applied
     */
    public boolean pauseChallenge(CommandSender sender) {
        return challengeSessionManager.pauseChallenge(sender);
    }

    /**
     * Attempts to resume a paused challenge run.
     *
     * @param sender command sender requesting resume
     * @return true when resume was accepted and applied
     */
    public boolean resumeChallenge(CommandSender sender) {
        return challengeSessionManager.resumeChallenge(sender);
    }

    /**
     * Triggers an immediate world reset.
     *
     * @param sender command sender requesting world reset
     */
    public void resetWorlds(CommandSender sender) {
        worldResetManager.resetThreeWorlds(sender);
    }

    /**
     * Reloads config and applies settings immediately when in prep phase.
     *
     * @return true when reload and apply were fully completed
     */
    public boolean reloadConfigAndApply() {
        plugin.reloadConfig();
        logger.loadFromConfig();
        if (!challengeSessionManager.isPrepPhase()) {
            return false;
        }

        challengeManager.loadFromConfig();
        worldResetManager.ensureThreeWorldsLoaded();
        challengeSessionManager.syncWorldRules();
        challengeSessionManager.applySharedVitalsIfEnabled();
        challengeSessionManager.refreshLobbyPreview();
        return true;
    }

    /**
     * Returns whether challenge mode is enabled.
     *
     * @return true when challenge mode is currently enabled
     */
    public boolean isChallengeEnabled() {
        return challengeManager.isEnabled();
    }

    /**
     * Returns the current session phase name.
     *
     * @return lowercase session phase name
     */
    public String getPhaseName() {
        return challengeSessionManager.getPhaseName();
    }

    /**
     * Returns current ready player count.
     *
     * @return number of participants currently marked ready
     */
    public int getReadyCount() {
        return challengeSessionManager.getReadyCount();
    }

    /**
     * Returns current ready target count.
     *
     * @return number of participants required for ready completion
     */
    public int getReadyTargetCount() {
        return challengeSessionManager.getReadyTargetCount();
    }

    /**
     * Returns the active challenge mode.
     *
     * @return currently configured challenge mode
     */
    public ChallengeMode getMode() {
        return challengeManager.getMode();
    }

    /**
     * Returns current component toggle values.
     *
     * @return map of each challenge component and its enabled state
     */
    public Map<ChallengeComponent, Boolean> getComponentToggles() {
        return challengeManager.getComponentToggles();
    }

    /**
     * Returns whether a specific challenge component is enabled.
     *
     * @param component component to query
     * @return true when the requested component is enabled
     */
    public boolean isComponentEnabled(ChallengeComponent component) {
        return challengeManager.isComponentEnabled(component);
    }
}
