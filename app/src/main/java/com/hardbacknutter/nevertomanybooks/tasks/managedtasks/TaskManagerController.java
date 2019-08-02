package com.hardbacknutter.nevertomanybooks.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Controller interface for a {@link TaskManager}.
 */
public interface TaskManagerController {

    void requestAbort();

    @NonNull
    TaskManager getTaskManager();
}
