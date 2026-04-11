package dev.deepcore.challenge.session;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Runs and manages a repeating action-bar broadcast task. */
public final class ActionBarTickerService {
    private final Plugin plugin;
    private BukkitTask tickerTask;

    /**
     * Creates an action-bar ticker service.
     *
     * @param plugin plugin used to schedule ticker tasks
     */
    public ActionBarTickerService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the ticker task that executes work and pushes action-bar updates.
     *
     * @param shouldTick         predicate checked before each tick execution
     * @param tickWork           work action to execute before broadcasting action
     *                           bar
     * @param messageSupplier    supplier for the action-bar message
     * @param recipientsSupplier supplier for players receiving action-bar updates
     */
    public void start(
            BooleanSupplier shouldTick,
            Runnable tickWork,
            Supplier<Component> messageSupplier,
            Supplier<List<Player>> recipientsSupplier) {
        stop();
        tickerTask = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (!shouldTick.getAsBoolean()) {
                                return;
                            }
                            tickWork.run();
                            Component message = messageSupplier.get();
                            for (Player player : recipientsSupplier.get()) {
                                player.sendActionBar(message);
                            }
                        },
                        0L,
                        20L);
    }

    /** Stops the active action-bar ticker task if one is running. */
    public void stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    /**
     * Clears the action bar for all provided players.
     *
     * @param players players whose action bars should be cleared
     */
    public void clearActionBar(Iterable<? extends Player> players) {
        Component empty = Component.empty();
        for (Player player : players) {
            player.sendActionBar(empty);
        }
    }
}
