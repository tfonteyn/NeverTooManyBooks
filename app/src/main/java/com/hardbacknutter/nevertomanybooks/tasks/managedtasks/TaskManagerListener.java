package com.hardbacknutter.nevertomanybooks.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Listener that lets other objects know about task progress and task completion.
 * <p>
 * See {@link com.hardbacknutter.nevertomanybooks.searches.SearchCoordinator} for an example.
 *
 * @author Philip Warner
 */
public interface TaskManagerListener {

    void onTaskFinished(@NonNull TaskManager taskManager,
                        @NonNull ManagedTask task);

    default void onTaskProgress(int absPosition,
                                int max,
                                @NonNull String message) {
        // ignore
    }

    default void onTaskUserMessage(@NonNull String message) {
        // ignore
    }
}
