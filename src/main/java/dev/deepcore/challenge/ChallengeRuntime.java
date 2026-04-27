package dev.deepcore.challenge;

import dev.deepcore.challenge.training.TrainingManager;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.records.RunRecordsService;

/**
 * Holds initialized challenge runtime services for plugin lifecycle access.
 */
public final class ChallengeRuntime {
    private final ChallengeManager challengeManager;
    private final ChallengeSessionManager challengeSessionManager;
    private final WorldResetManager worldResetManager;
    private final RunRecordsService runRecordsService;
    private final TrainingManager trainingManager;

    /**
     * Creates an immutable runtime container for challenge services.
     *
     * @param challengeManager        challenge settings and component manager
     * @param challengeSessionManager challenge session orchestration manager
     * @param worldResetManager       world reset and lifecycle manager
     * @param runRecordsService       run records persistence/query service
     * @param trainingManager         training gym manager
     */
    public ChallengeRuntime(
            ChallengeManager challengeManager,
            ChallengeSessionManager challengeSessionManager,
            WorldResetManager worldResetManager,
            RunRecordsService runRecordsService,
            TrainingManager trainingManager) {
        this.challengeManager = challengeManager;
        this.challengeSessionManager = challengeSessionManager;
        this.worldResetManager = worldResetManager;
        this.runRecordsService = runRecordsService;
        this.trainingManager = trainingManager;
    }

    /**
     * Returns the challenge settings manager instance.
     *
     * @return challenge settings manager
     */
    public ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    /**
     * Returns the challenge session manager instance.
     *
     * @return challenge session manager
     */
    public ChallengeSessionManager getChallengeSessionManager() {
        return challengeSessionManager;
    }

    /**
     * Returns the world reset manager instance.
     *
     * @return world reset manager
     */
    public WorldResetManager getWorldResetManager() {
        return worldResetManager;
    }

    /**
     * Returns the run records service instance.
     *
     * @return run records service
     */
    public RunRecordsService getRunRecordsService() {
        return runRecordsService;
    }

    /**
     * Returns the training gym manager instance.
     *
     * @return training manager
     */
    public TrainingManager getTrainingManager() {
        return trainingManager;
    }
}
