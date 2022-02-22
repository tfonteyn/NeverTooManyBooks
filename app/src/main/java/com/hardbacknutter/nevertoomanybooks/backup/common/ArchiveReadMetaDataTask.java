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
package com.hardbacknutter.nevertoomanybooks.backup.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Input: {@link ImportHelper}.
 * Output: {@link ArchiveMetaData}.
 */
public class ArchiveReadMetaDataTask
        extends MTask<ArchiveMetaData> {

    /** Log tag. */
    private static final String TAG = "ArchiveReadMetaDataTask";

    /** import configuration. */
    private ImportHelper mHelper;

    public ArchiveReadMetaDataTask() {
        super(R.id.TASK_ID_READ_META_DATA, TAG);
    }

    /**
     * Start the task.
     *
     * @param helper import configuration
     */
    @UiThread
    public void start(@NonNull final ImportHelper helper) {
        mHelper = helper;
        execute();
    }

    @Nullable
    @Override
    @WorkerThread
    protected ArchiveMetaData doWork(@NonNull final Context context)
            throws InvalidArchiveException, ImportException, IOException,
                   StorageException {
        return mHelper.readMetaData(context);
    }
}
