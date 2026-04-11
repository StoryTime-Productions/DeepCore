package dev.deepcore.challenge.session;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks named tasks and guarantees replace/cancel behavior from one place.
 */
public final class TaskGroup {
    private final Map<String, BukkitTask> tasks = new HashMap<>();

    /**
     * Replaces any existing task for the key with the provided task instance.
     *
     * @param key  logical task key
     * @param task task instance to store and manage
     */
    public void replace(String key, BukkitTask task) {
        cancel(key);
        tasks.put(key, task);
    }

    /**
     * Cancels and removes the task currently mapped to the provided key.
     *
     * @param key logical task key to cancel
     */
    public void cancel(String key) {
        BukkitTask existing = tasks.remove(key);
        if (existing != null) {
            existing.cancel();
        }
    }

    /** Cancels all tracked tasks and clears the task map. */
    public void cancelAll() {
        for (BukkitTask task : tasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        tasks.clear();
    }
}
