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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class CsvExportTask
        extends TaskBase<Void, ExportHelper> {

    /** Log tag. */
    private static final String TAG = "ExportCSVTask";
    /** Write to this temp file first. */
    private static final String TEMP_FILE_NAME = TAG + ".tmp";

    @NonNull
    private final CsvExporter mExporter;
    /**  export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param helper the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public CsvExportTask(@NonNull final Context context,
                         @NonNull final ExportHelper helper,
                         @NonNull final TaskListener<ExportHelper> taskListener) {
        super(R.id.TASK_ID_CSV_EXPORT, taskListener);
        mHelper = helper;
        mExporter = new CsvExporter(context, helper);
    }

    @Override
    @UiThread
    protected void onCancelled(@NonNull final ExportHelper result) {
        cleanup(App.getTaskContext());
        super.onCancelled(result);
    }

    private void cleanup(@NonNull final Context context) {
        FileUtils.delete(getTempFile(context));
        try {
            mExporter.close();
        } catch (@NonNull final IOException ignore) {
            // ignore
        }
    }

    private File getTempFile(@NonNull final Context context) {
        return AppDir.Cache.getFile(context, TEMP_FILE_NAME);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName("ExportCSVTask");
        final Context context = App.getTaskContext();

        try (OutputStream os = new FileOutputStream(getTempFile(context))) {
            mHelper.addResults(mExporter.doBooks(os, getProgressListener()));

            if (!isCancelled()) {
                // send to user destination
                FileUtils.copy(context, getTempFile(context), mHelper.getUri());
            }
            return mHelper;

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return mHelper;
        } finally {
            cleanup(context);
        }
    }
}
