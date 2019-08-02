package com.hardbacknutter.nevertomanybooks.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Allows other objects to know when a task completed.
 */
public interface ManagedTaskListener {

    void onTaskFinished(@NonNull ManagedTask task);
}
