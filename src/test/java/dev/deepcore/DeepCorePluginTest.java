package dev.deepcore;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.ChallengeRuntime;
import dev.deepcore.challenge.ChallengeRuntimeInitializer;
import dev.deepcore.challenge.training.TrainingManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class DeepCorePluginTest {

    @Test
    void onEnable_initializesRuntimeAndLogger_onSuccess() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doNothing().when(plugin).saveDefaultConfig();

        ChallengeRuntime runtime = mock(ChallengeRuntime.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        when(runtime.getChallengeManager()).thenReturn(challengeManager);

        try (MockedConstruction<DeepCoreLogger> loggerConstruction = mockConstruction(DeepCoreLogger.class);
                MockedConstruction<ChallengeRuntimeInitializer> initializerConstruction = mockConstruction(
                        ChallengeRuntimeInitializer.class,
                        (initializer, context) -> when(initializer.initialize(eq(plugin), any(DeepCoreLogger.class)))
                                .thenReturn(runtime))) {

            plugin.onEnable();

            DeepCoreLogger logger = loggerConstruction.constructed().get(0);
            verify(logger).loadFromConfig();
            verify(logger).info("DeepCore loaded!");
            assertSame(logger, plugin.getDeepCoreLogger());
            assertSame(challengeManager, plugin.getChallengeManager());
            assertSame(runtime, getField(plugin, "challengeRuntime"));

            // Ensure initializer construction path was actually exercised.
            assertSame(1, initializerConstruction.constructed().size());
        }
    }

    @Test
    void onEnable_logsAndReturnsWhenRuntimeInitializationFails() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doNothing().when(plugin).saveDefaultConfig();

        try (MockedConstruction<DeepCoreLogger> loggerConstruction = mockConstruction(DeepCoreLogger.class);
                MockedConstruction<ChallengeRuntimeInitializer> ignored = mockConstruction(
                        ChallengeRuntimeInitializer.class,
                        (initializer, context) -> when(initializer.initialize(eq(plugin), any(DeepCoreLogger.class)))
                                .thenThrow(new IllegalStateException("init failed")))) {

            plugin.onEnable();

            DeepCoreLogger logger = loggerConstruction.constructed().get(0);
            verify(logger).loadFromConfig();
            verify(logger).error("init failed");
            verify(logger, never()).info("DeepCore loaded!");
            assertNull(plugin.getChallengeManager());
        }
    }

    @Test
    void onDisable_persistsAndShutsDownRuntimeServicesWhenPresent() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        ChallengeRuntime runtime = mock(ChallengeRuntime.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        dev.deepcore.challenge.ChallengeSessionManager sessionService =
                mock(dev.deepcore.challenge.ChallengeSessionManager.class);
        dev.deepcore.records.RunRecordsService recordsService = mock(dev.deepcore.records.RunRecordsService.class);
        TrainingManager trainingManager = mock(TrainingManager.class);

        when(runtime.getChallengeManager()).thenReturn(challengeManager);
        when(runtime.getChallengeSessionManager()).thenReturn(sessionService);
        when(runtime.getRunRecordsService()).thenReturn(recordsService);
        when(runtime.getTrainingManager()).thenReturn(trainingManager);
        when(sessionService.isRunningPhase()).thenReturn(true);
        when(sessionService.isPausedPhase()).thenReturn(false);

        setField(plugin, "challengeRuntime", runtime);
        plugin.onDisable();

        org.mockito.InOrder order = inOrder(challengeManager, trainingManager, sessionService, recordsService);
        verify(challengeManager).saveToConfig();
        order.verify(challengeManager).saveToConfig();
        order.verify(trainingManager).shutdown();
        order.verify(sessionService).endChallengeAndReturnToPrep();
        order.verify(sessionService).shutdown();
        order.verify(recordsService).shutdown();
    }

    @Test
    void onDisable_doesNotForceEndWhenSessionNotRunningOrPaused() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        ChallengeRuntime runtime = mock(ChallengeRuntime.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        dev.deepcore.challenge.ChallengeSessionManager sessionService =
                mock(dev.deepcore.challenge.ChallengeSessionManager.class);
        dev.deepcore.records.RunRecordsService recordsService = mock(dev.deepcore.records.RunRecordsService.class);
        TrainingManager trainingManager = mock(TrainingManager.class);

        when(runtime.getChallengeManager()).thenReturn(challengeManager);
        when(runtime.getChallengeSessionManager()).thenReturn(sessionService);
        when(runtime.getRunRecordsService()).thenReturn(recordsService);
        when(runtime.getTrainingManager()).thenReturn(trainingManager);
        when(sessionService.isRunningPhase()).thenReturn(false);
        when(sessionService.isPausedPhase()).thenReturn(false);

        setField(plugin, "challengeRuntime", runtime);
        plugin.onDisable();

        verify(sessionService, never()).endChallengeAndReturnToPrep();
        verify(sessionService).shutdown();
    }

    @Test
    void onDisable_isNoOpWhenRuntimeMissing() {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        plugin.onDisable();
        assertNull(plugin.getChallengeManager());
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
