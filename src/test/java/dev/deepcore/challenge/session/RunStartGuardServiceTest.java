package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RunStartGuardServiceTest {

    @Test
    void isDiscoPreviewBlockingChallengeStart_reflectsManagerState() {
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        RunStartGuardService missingManagerService = new RunStartGuardService(() -> null, log);
        assertFalse(missingManagerService.isDiscoPreviewBlockingChallengeStart());

        WorldResetManager manager = mock(WorldResetManager.class);
        when(manager.isCurrentOverworldDiscoVariant()).thenReturn(true);
        RunStartGuardService discoService = new RunStartGuardService(() -> manager, log);
        assertTrue(discoService.isDiscoPreviewBlockingChallengeStart());
    }

    @Test
    void announceDiscoPreviewStartBlocked_warnsAllOnlinePlayers() {
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        RunStartGuardService service = new RunStartGuardService(() -> null, log);

        Player a = mock(Player.class);
        Player b = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(a, b));
            service.announceDiscoPreviewStartBlocked();
        }

        verify(log).sendWarn(eq(a), contains("Disco world preview"));
        verify(log).sendWarn(eq(b), contains("Disco world preview"));
    }

    @Test
    void announceDiscoPreviewStartBlocked_handlesNoPlayersOnline() {
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        RunStartGuardService service = new RunStartGuardService(() -> null, log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            service.announceDiscoPreviewStartBlocked();
        }

        verify(log, never()).sendWarn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
