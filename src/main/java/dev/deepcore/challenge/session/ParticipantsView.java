package dev.deepcore.challenge.session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Provides filtered views over online players and active participants.
 */
public final class ParticipantsView {
    /**
     * Returns a snapshot list of all currently online players.
     *
     * @return snapshot list of players currently online
     */
    public List<Player> onlinePlayers() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    /**
     * Returns the number of currently online players.
     *
     * @return count of currently online players
     */
    public int onlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Returns UUIDs of all currently online players.
     *
     * @return set of UUIDs for currently online players
     */
    public Set<UUID> onlinePlayerIds() {
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineIds.add(player.getUniqueId());
        }
        return onlineIds;
    }

    /**
     * Returns online players whose UUID appears in the participant set.
     *
     * @param participants participant UUIDs to match against online players
     * @return online players included in the participant set
     */
    public List<Player> onlineParticipants(Set<UUID> participants) {
        List<Player> onlineParticipants = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (participants.contains(player.getUniqueId())) {
                onlineParticipants.add(player);
            }
        }
        return onlineParticipants;
    }

    /**
     * Returns online non-spectator participants.
     *
     * @param participants participant UUIDs to filter against online players
     * @return online participants whose game mode is not spectator
     */
    public List<Player> activeParticipants(Set<UUID> participants) {
        List<Player> activeParticipants = new ArrayList<>();
        for (Player player : onlineParticipants(participants)) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                activeParticipants.add(player);
            }
        }
        return activeParticipants;
    }

    /**
     * Returns players to include in shared-vitals sync for the given phase.
     *
     * @param runningPhase whether the challenge is currently in running phase
     * @param participants participant UUIDs to include when running
     * @return participants to target for shared-vitals synchronization
     */
    public List<Player> sharedVitalsParticipants(boolean runningPhase, Set<UUID> participants) {
        return runningPhase ? onlineParticipants(participants) : onlinePlayers();
    }
}
