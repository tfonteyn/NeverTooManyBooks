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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTask;

/**
 * Base class for Goodreads tasks.
 * Defines the categories and the Goodreads specific extended-status field.
 */
public abstract class GrBaseTQTask
        extends TQTask {

    /** We're only allowing a single import task to be scheduled/run at any time. */
    public static final int CAT_IMPORT = 1;
    /** We're only allowing a single export task to be scheduled/run at any time. */
    public static final int CAT_EXPORT = 2;
    /** But we can schedule multiple single-book export times at any time. */
    public static final int CAT_EXPORT_ONE_BOOK = 3;

    private static final long serialVersionUID = 6014113427811967096L;

    @GrStatus.Status
    private int mLastExtStatus;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    GrBaseTQTask(@NonNull final String description) {
        super(description);
    }

    void setLastExtStatus(@GrStatus.Status final int status,
                          @Nullable final Exception e) {
        mLastExtStatus = status;
        setLastException(e);
    }

    void setLastExtStatus(@NonNull final GrStatus.SendBook status) {
        mLastExtStatus = status.getStatus();
        setLastException(null);
    }


    @GrStatus.Status
    public int getLastExtStatus() {
        return mLastExtStatus;
    }
}
