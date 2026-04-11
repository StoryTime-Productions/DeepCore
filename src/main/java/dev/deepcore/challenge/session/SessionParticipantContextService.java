package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Provides centralized participant and challenge-state queries for session
 * services.
 */
public final class SessionParticipantContextService {
    private final ChallengeManager challengeManager;
    private final SessionState sessionState;
    private final ParticipantsView participantsView;
    private final Set<UUID> participants;

    /**
     * Creates a session participant context service.
     *
     * @param challengeManager challenge settings and component manager
     * @param sessionState     mutable session phase/state container
     * @param participantsView participant roster and counts view
     * @param participants     active run participants
     */
    public SessionParticipantContextService(
            ChallengeManager challengeManager,
            SessionState sessionState,
            ParticipantsView participantsView,
            Set<UUID> participants) {
        this.challengeManager = challengeManager;
        this.sessionState = sessionState;
        this.participantsView = participantsView;
        this.participants = participants;
    }

    public boolean isRunningPhase() {
        return sessionState.is(SessionState.Phase.RUNNING);
    }

    public List<Player> getPlayersForSharedVitals() {
        return participantsView.sharedVitalsParticipants(isRunningPhase(), participants);
    }

    public List<Player> getOnlineParticipants() {
        return participantsView.onlineParticipants(participants);
    }

    public List<Player> getActiveParticipants() {
        return participantsView.activeParticipants(participants);
    }

    public boolean isChallengeActive(Player player) {
        return isRunningPhase() && challengeManager.isEnabled() && participants.contains(player.getUniqueId());
    }

    public boolean isSharedInventoryEnabled() {
        return challengeManager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY);
    }
}
