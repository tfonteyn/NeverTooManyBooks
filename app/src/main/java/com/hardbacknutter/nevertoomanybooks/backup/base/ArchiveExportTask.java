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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Input: {@link ExportManager}.
 * Output: the updated {@link ExportManager} with the {@link ExportResults}.
 */
public class ArchiveExportTask
        extends VMTask<ExportManager> {

    /** Log tag. */
    private static final String TAG = "ArchiveExportTask";

    /** export configuration. */
    @Nullable
    private ExportManager mHelper;

    public void setHelper(@NonNull final ExportManager helper) {
        mHelper = helper;
    }

    @NonNull
    public String getDefaultUriName(@NonNull final Context context) {
        Objects.requireNonNull(mHelper);
        return Exporter.getNamePrefix(context) + mHelper.getArchiveContainer().getFileExt();
    }

    /**
     * Start the task.
     * {@link #setHelper(ExportManager)} must have been called before.
     *
     * @param uri to write to
     */
    public void startExport(@NonNull final Uri uri) {
        Objects.requireNonNull(mHelper);
        mHelper.setUri(uri);
        execute(R.id.TASK_ID_EXPORT);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportManager doWork()
            throws IOException {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        //noinspection ConstantConditions
        try (ArchiveWriter exporter = mHelper.getArchiveWriter(context)) {
            mHelper.setResults(exporter.write(context, this));
        }

        // The output file is now properly closed, export it to the user Uri
        if (!isCancelled()) {
            mHelper.onSuccess(context);
            return mHelper;
        }

        mHelper.onFail(context);
        return mHelper;
    }
}
