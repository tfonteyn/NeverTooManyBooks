package com.hardbacknutter.nevertomanybooks.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Controller interface for a {@link ManagedTask}.
 */
interface ManagedTaskController {

    void requestAbort();

    @NonNull
    ManagedTask getManagedTask();
}
