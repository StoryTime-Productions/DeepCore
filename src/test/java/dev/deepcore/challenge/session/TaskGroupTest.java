package dev.deepcore.challenge.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class TaskGroupTest {

    @Test
    void replace_cancelsExistingTaskBeforeReplacing() {
        TaskGroup taskGroup = new TaskGroup();
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);

        taskGroup.replace("key", first);
        taskGroup.replace("key", second);

        verify(first).cancel();
        verify(second, never()).cancel();
    }

    @Test
    void cancel_removesAndCancelsTaskWhenPresent() {
        TaskGroup taskGroup = new TaskGroup();
        BukkitTask task = mock(BukkitTask.class);
        taskGroup.replace("cleanup", task);

        taskGroup.cancel("cleanup");

        verify(task).cancel();
    }

    @Test
    void cancelAll_cancelsAllTrackedTasksAndClearsState() {
        TaskGroup taskGroup = new TaskGroup();
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);

        taskGroup.replace("a", first);
        taskGroup.replace("b", second);
        taskGroup.cancelAll();

        verify(first).cancel();
        verify(second).cancel();

        taskGroup.cancel("a");
        taskGroup.cancel("b");

        verify(first).cancel();
        verify(second).cancel();
    }
}
