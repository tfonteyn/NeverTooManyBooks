package com.eleybourn.bookcatalogue.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Listener that lets other objects know about task progress and task completion.
 * <p>
 * See {@link com.eleybourn.bookcatalogue.searches.SearchCoordinator} for an example.
 *
 * @author Philip Warner
 */
public interface TaskManagerListener {

    void onTaskFinished(@NonNull TaskManager taskManager,
                        @NonNull ManagedTask task);

    default void onTaskProgress(int absPosition,
                                int max,
                                @NonNull String message) {
        // ignore by default
    }

    default void onTaskUserMessage(@NonNull String message) {
        // ignore by default
    }
}
