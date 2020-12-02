/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * Input: {@link ExportHelper}.
 * Output: {@link ExportResults}.
 */
public class ArchiveWriterTask
        extends VMTask<ExportResults> {

    /** Log tag. */
    private static final String TAG = "ArchiveWriterTask";

    /** export configuration. */
    @Nullable
    private ExportHelper mHelper;

    /**
     * Start the task.
     *
     * @param exportHelper with uri/options
     */
    public void startExport(@NonNull final ExportHelper exportHelper) {
        mHelper = exportHelper;
        execute(R.id.TASK_ID_EXPORT);
    }

    @NonNull
    @Override
    @WorkerThread
    protected ExportResults doWork(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        Thread.currentThread().setName(TAG);

        ExportResults results = null;
        //noinspection ConstantConditions
        try (ArchiveWriter writer = mHelper.createArchiveWriter(context)) {
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
