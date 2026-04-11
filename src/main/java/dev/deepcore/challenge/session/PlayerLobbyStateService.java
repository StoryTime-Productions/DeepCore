package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Handles player game-mode enforcement and lobby loadout application.
 */
public final class PlayerLobbyStateService {
    private final WorldClassificationService worldClassificationService;
    private final PrepBookService prepBookService;

    /**
     * Creates a player lobby state service.
     *
     * @param worldClassificationService world classification helper
     * @param prepBookService            prep book inventory helper
     */
    public PlayerLobbyStateService(
            WorldClassificationService worldClassificationService, PrepBookService prepBookService) {
        this.worldClassificationService = worldClassificationService;
        this.prepBookService = prepBookService;
    }

    /**
     * Enforces survival mode on world entry for non-eliminated participants.
     *
     * @param player            player whose game mode should be normalized
     * @param runningPhase      whether the challenge is currently running
     * @param hardcoreEnabled   whether hardcore mode is enabled
     * @param eliminatedPlayers UUIDs of eliminated players
     */
    public void enforceSurvivalOnWorldEntry(
            Player player, boolean runningPhase, boolean hardcoreEnabled, Set<UUID> eliminatedPlayers) {
        if (runningPhase && hardcoreEnabled && eliminatedPlayers.contains(player.getUniqueId())) {
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Applies lobby inventory loadout when the player is in a lobby/limbo world.
     *
     * @param player player whose inventory should be reset for lobby state
     */
    public void applyLobbyInventoryLoadoutIfInLobbyWorld(Player player) {
        if (!worldClassificationService.isLobbyOrLimboWorld(player.getWorld())) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        player.setItemOnCursor(null);
        prepBookService.giveIfMissing(player);
        player.updateInventory();
    }
}
