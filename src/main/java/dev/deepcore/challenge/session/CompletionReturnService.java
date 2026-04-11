package dev.deepcore.challenge.session;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schedules and manages the post-dragon-kill return-to-lobby countdown.
 */
public final class CompletionReturnService {
    private final Plugin plugin;
    private BukkitTask countdownTask;

    /**
     * Creates a completion return service.
     *
     * @param plugin plugin used to schedule completion countdown tasks
     */
    public CompletionReturnService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the post-completion return countdown task.
     *
     * @param startSeconds      initial countdown duration in seconds
     * @param shouldContinue    predicate checked each tick to keep running
     * @param onComplete        callback executed when countdown reaches zero
     * @param onSecondRemaining callback invoked for each second remaining
     */
    public void start(
            int startSeconds,
            BooleanSupplier shouldContinue,
            Runnable onComplete,
            Consumer<Integer> onSecondRemaining) {
        stop();

        final int[] secondsLeft = {Math.max(0, startSeconds)};
        countdownTask = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (!shouldContinue.getAsBoolean()) {
                                stop();
                                return;
                            }

                            if (secondsLeft[0] <= 0) {
                                stop();
                                onComplete.run();
                                return;
                            }

                            onSecondRemaining.accept(secondsLeft[0]);
                            secondsLeft[0]--;
                        },
                        0L,
                        20L);
    }

    /** Stops the active return countdown task if running. */
    public void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }
}
