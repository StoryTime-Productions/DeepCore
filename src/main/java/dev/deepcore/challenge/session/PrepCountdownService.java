package dev.deepcore.challenge.session;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Manages the prep-phase ready countdown task lifecycle. */
public final class PrepCountdownService {
    private final JavaPlugin plugin;
    private BukkitTask countdownTask;

    /**
     * Creates a prep countdown service.
     *
     * @param plugin plugin used to schedule countdown tasks
     */
    public PrepCountdownService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the prep countdown with tick, cancel, and completion callbacks.
     *
     * @param countdownSeconds initial countdown duration in seconds
     * @param shouldCancel     predicate checked each tick for cancellation
     *                         conditions
     * @param onCancel         callback executed when countdown is canceled
     * @param onComplete       callback executed when countdown reaches zero
     * @param onTick           callback receiving each second remaining value
     */
    public void startCountdown(
            int countdownSeconds,
            BooleanSupplier shouldCancel,
            Runnable onCancel,
            Runnable onComplete,
            IntConsumer onTick) {
        cancel();

        final int[] timeLeft = {countdownSeconds};
        countdownTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (shouldCancel.getAsBoolean()) {
                                onCancel.run();
                                return;
                            }

                            if (timeLeft[0] <= 0) {
                                cancel();
                                onComplete.run();
                                return;
                            }

                            onTick.accept(timeLeft[0]);
                            timeLeft[0]--;
                        },
                        0L,
                        20L);
    }

    /** Cancels the active countdown task if one is currently running. */
    public void cancel() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }
}
