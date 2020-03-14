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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.options.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Uses a given {@link Exporter} to write the book list to a Uri.
 * <p>
 * Input: an {@link Exporter} and a configured {@link ExportHelper}.
 * Output: the updated {@link ExportHelper} with the {@link ExportResults}.
 */
public class ExportTask
        extends TaskBase<Void, ExportHelper> {

    /** Log tag. */
    private static final String TAG = "ExportTask";

    /** export configuration. */
    @NonNull
    private final ExportHelper mHelper;
    @NonNull
    private final Exporter mExporter;

    /**
     * Constructor.
     *
     * @param exporter     Exporter to use
     * @param helper       export configuration
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ExportTask(@NonNull final Exporter exporter,
                      @NonNull final ExportHelper helper,
                      @NonNull final TaskListener<ExportHelper> taskListener) {
        super(R.id.TASK_ID_EXPORTER, taskListener);
        mExporter = exporter;
        mHelper = helper;
        mHelper.validate();
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try (Exporter exporter = mExporter) {
            mHelper.getResults().add(exporter.write(context, mHelper.getTempOutputFile(context),
                                                    getProgressListener()));
        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            mException = e;
        }

        // The Exporter file is now properly closed,
        // export it to the user Uri (if successful) and cleanup.
        try {
            if (mException == null && !isCancelled()) {
                mHelper.onSuccess(context);
            }
        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
        } finally {
            mHelper.onCleanup(context);
        }
        return mHelper;
    }

}
