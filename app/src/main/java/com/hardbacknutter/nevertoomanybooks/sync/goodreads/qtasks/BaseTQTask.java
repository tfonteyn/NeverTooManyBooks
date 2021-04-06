/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTask;

/**
 * Base class for Goodreads tasks.
 * Defines the categories and the extended-status field.
 */
public abstract class BaseTQTask
        extends TQTask {

    private static final long serialVersionUID = 4676971523754206924L;

    @GrStatus.Status
    private int mLastExtStatus;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    BaseTQTask(@NonNull final String description) {
        super(description);
    }

    void setLastExtStatus(@GrStatus.Status final int status,
                          @Nullable final Exception e) {
        mLastExtStatus = status;
        setLastException(e);
    }

    @GrStatus.Status
    public int getLastExtStatus() {
        return mLastExtStatus;
    }

    void setLastExtStatus(@GrStatus.Status final int status) {
        mLastExtStatus = status;
    }

    /**
     * Run the task.
     *
     * @param context The localised Application context
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    public abstract boolean doWork(@NonNull final Context context,
                                   @NonNull QueueManager queueManager);
}
