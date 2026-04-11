package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SessionParticipantContextServiceTest {

    @Test
    void delegatesParticipantViewsAndStateChecks() {
        ChallengeManager manager = mock(ChallengeManager.class);
        SessionState state = new SessionState();
        ParticipantsView view = mock(ParticipantsView.class);
        UUID participantId = UUID.randomUUID();
        Set<UUID> participants = Set.of(participantId);
        Player participant = mock(Player.class);

        when(manager.isEnabled()).thenReturn(true);
        when(manager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(true);
        when(view.onlineParticipants(participants)).thenReturn(List.of(participant));
        when(view.activeParticipants(participants)).thenReturn(List.of(participant));
        when(view.sharedVitalsParticipants(true, participants)).thenReturn(List.of(participant));

        SessionParticipantContextService service =
                new SessionParticipantContextService(manager, state, view, participants);

        assertFalse(service.isRunningPhase());
        state.setPhase(SessionState.Phase.RUNNING);
        assertTrue(service.isRunningPhase());

        assertEquals(1, service.getOnlineParticipants().size());
        assertEquals(1, service.getActiveParticipants().size());
        assertEquals(1, service.getPlayersForSharedVitals().size());

        when(participant.getUniqueId()).thenReturn(participantId);
        assertTrue(service.isChallengeActive(participant));

        when(manager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY)).thenReturn(true);
        assertTrue(service.isSharedInventoryEnabled());

        when(manager.isEnabled()).thenReturn(false);
        assertFalse(service.isChallengeActive(participant));
    }
}
