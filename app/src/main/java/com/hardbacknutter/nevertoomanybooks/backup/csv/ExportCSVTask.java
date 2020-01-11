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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

public class ExportCSVTask
        extends TaskBase<Void, ExportHelper> {

    /** Log tag. */
    private static final String TAG = "ExportCSVTask";

    @NonNull
    private final CsvExporter mExporter;
    @NonNull
    private final ExportHelper mExportHelper;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param exportHelper the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ExportCSVTask(@NonNull final Context context,
                         @NonNull final ExportHelper exportHelper,
                         @NonNull final TaskListener<ExportHelper> taskListener) {
        super(R.id.TASK_ID_CSV_EXPORT, taskListener);
        mExportHelper = exportHelper;
        mExporter = new CsvExporter(context, exportHelper);
    }

    @Override
    @UiThread
    protected void onCancelled(@NonNull final ExportHelper result) {
        cleanup(App.getAppContext());
        super.onCancelled(result);
    }

    private void cleanup(@NonNull final Context context) {
        StorageUtils.deleteFile(ExportHelper.getTempFile(context));
        try {
            mExporter.close();
        } catch (@NonNull final IOException ignore) {
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName("ExportCSVTask");
        Context context = App.getAppContext();

        try (OutputStream os = new FileOutputStream(ExportHelper.getTempFile(context))) {
            mExportHelper.addResults(mExporter.doBooks(os, getProgressListener()));

            if (!isCancelled()) {
                // send to user destination
                Objects.requireNonNull(mExportHelper.uri);
                StorageUtils.exportFile(context, ExportHelper.getTempFile(context),
                                        mExportHelper.uri);
            }
            return mExportHelper;

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return mExportHelper;
        } finally {
            cleanup(context);
        }
    }

}
