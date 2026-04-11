package dev.deepcore.challenge.world;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Bridge contract used by world reset flows to interact with session state.
 */
public interface ChallengeSessionWorldBridge {
    /**
     * Returns the preferred lobby teleport location for session flows.
     *
     * @return destination location to use for lobby-bound teleports
     */
    Location getPreferredLobbyTeleportLocation();

    /** Resets session state in preparation for a new run. */
    void resetForNewRun();

    /**
     * Ensures the prep book is present for the provided player.
     *
     * @param player player who should receive or retain the prep book
     */
    void ensurePrepBook(Player player);

    /** Refreshes lobby preview state and entities. */
    void refreshLobbyPreview();
}
