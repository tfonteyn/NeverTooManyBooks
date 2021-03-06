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
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Input: {@link ExportHelper}.
 * Output: {@link ExportResults}.
 */
public class ArchiveWriterTask
        extends MTask<ExportResults> {

    /** Log tag. */
    private static final String TAG = "ArchiveWriterTask";

    /** export configuration. */
    @Nullable
    private ExportHelper mHelper;

    public ArchiveWriterTask() {
        super(R.id.TASK_ID_EXPORT, TAG);
    }

    /**
     * Start the task.
     *
     * @param exportHelper with uri/options
     */
    public void start(@NonNull final ExportHelper exportHelper) {
        mHelper = exportHelper;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected ExportResults doWork(@NonNull final Context context)
            throws ExportException, IOException, StorageException {

        ExportResults results = null;
        //noinspection ConstantConditions
        try (ArchiveWriter writer = mHelper.createWriter(context)) {
            results = writer.write(context, this);

        } catch (@NonNull final IOException e) {
            // The zip archiver (maybe others as well?) can throw an IOException
            // when the user cancels, so only throw when this is not the case
            if (!isCancelled()) {
                // it's a real exception, cleanup and let the caller handle it.
                mHelper.onError(context);
                throw e;
            }
        }

        if (isCancelled()) {
            mHelper.onError(context);
            return new ExportResults();
        } else {
            mHelper.onSuccess(context);
            return Objects.requireNonNull(results, "exportResults");
        }
    }
}
