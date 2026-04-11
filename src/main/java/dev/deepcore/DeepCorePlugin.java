package dev.deepcore;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.ChallengeRuntime;
import dev.deepcore.challenge.ChallengeRuntimeInitializer;
import dev.deepcore.logging.DeepCoreLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point that wires challenge, reset, logging, and records
 * services.
 */
public final class DeepCorePlugin extends JavaPlugin {
    private ChallengeRuntime challengeRuntime;
    private DeepCoreLogger deepCoreLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        deepCoreLogger = new DeepCoreLogger(this);
        deepCoreLogger.loadFromConfig();
        try {
            challengeRuntime = new ChallengeRuntimeInitializer().initialize(this, deepCoreLogger);
        } catch (IllegalStateException ex) {
            deepCoreLogger.error(ex.getMessage());
            return;
        }

        deepCoreLogger.info("DeepCore enabled.");
    }

    @Override
    public void onDisable() {
        if (challengeRuntime != null) {
            challengeRuntime.getChallengeManager().saveToConfig();
        }
        if (challengeRuntime != null) {
            challengeRuntime.getChallengeSessionManager().shutdown();
        }
        if (challengeRuntime != null) {
            challengeRuntime.getRunRecordsService().shutdown();
        }
    }

    public ChallengeManager getChallengeManager() {
        return challengeRuntime == null ? null : challengeRuntime.getChallengeManager();
    }

    public DeepCoreLogger getDeepCoreLogger() {
        return deepCoreLogger;
    }
}
