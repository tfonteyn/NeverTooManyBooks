/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQTask;

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
     * @return {@code false} to requeue, {@code true} for success
     */
    public abstract boolean run(@NonNull QueueManager queueManager);
}
