package dev.deepcore.challenge.session;

import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Encapsulates pre-run guard checks and related player messaging.
 */
public final class RunStartGuardService {
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final DeepCoreLogger log;

    /**
     * Creates a run start guard service.
     *
     * @param worldResetManagerSupplier supplier used to retrieve the active world
     *                                  reset manager
     * @param log                       logger used for player-facing warning
     *                                  delivery
     */
    public RunStartGuardService(Supplier<WorldResetManager> worldResetManagerSupplier, DeepCoreLogger log) {
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.log = log;
    }

    /**
     * Returns whether the current disco preview should block run start.
     *
     * @return true when disco preview mode is active and run start should be
     *         blocked
     */
    public boolean isDiscoPreviewBlockingChallengeStart() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        return worldResetManager != null && worldResetManager.isCurrentOverworldDiscoVariant();
    }

    /**
     * Announces to online players that run start is blocked by disco preview mode.
     */
    public void announceDiscoPreviewStartBlocked() {
        String message = ChatColor.LIGHT_PURPLE
                + "Disco world preview is just for laughs. Regenerate the world to start a real challenge run.";
        String plainMessage = ChatColor.stripColor(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            log.sendWarn(player, plainMessage);
        }
    }
}
