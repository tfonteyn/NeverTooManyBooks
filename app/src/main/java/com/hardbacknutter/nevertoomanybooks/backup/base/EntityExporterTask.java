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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Used to bypass the Archiver framework, to push out a single entity using
 * the given {@link Exporter} to write to a Uri.
 * <p>
 * Input: an {@link Exporter} and a configured {@link ExportManager}.
 * Output: the updated {@link ExportManager} with the {@link ExportResults}.
 */
public class EntityExporterTask
        extends TaskBase<Void, ExportManager> {

    /** Log tag. */
    private static final String TAG = "ExporterTask";

    /** export configuration. */
    @NonNull
    private final ExportManager mHelper;
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
    public EntityExporterTask(@NonNull final Exporter exporter,
                              @NonNull final ExportManager helper,
                              @NonNull final TaskListener<ExportManager> taskListener) {
        super(R.id.TASK_ID_EXPORTER, taskListener);
        mExporter = exporter;
        mHelper = helper;
        mHelper.validate();
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportManager doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try (Exporter exporter = mExporter) {
            mHelper.setResults(exporter.write(context, mHelper.getTempOutputFile(context),
                                              getProgressListener()));
            exporter.close();
            // The output file is now properly closed, export it to the user Uri
            if (!isCancelled()) {
                mHelper.onSuccess(context);
                return mHelper;
            }
        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            mException = e;
        }

        mHelper.onFail(context);
        return mHelper;
    }
}
