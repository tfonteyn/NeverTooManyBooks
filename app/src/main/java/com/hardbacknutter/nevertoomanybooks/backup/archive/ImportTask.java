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
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.options.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Uses a given {@link Importer} to read the book list from a Uri.
 * <p>
 * Input: an {@link Importer} and a configured {@link ImportHelper}.
 * Output: the updated {@link ImportHelper} with the {@link ImportResults}.
 */
public class ImportTask
        extends TaskBase<Void, ImportHelper> {

    /** Log tag. */
    private static final String TAG = "ImportTask";

    /** import configuration. */
    @NonNull
    private final ImportHelper mHelper;
    @NonNull
    private final Importer mImporter;

    /**
     * Constructor.
     *
     * @param importer     Importer to use
     * @param helper       import configuration
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ImportTask(@NonNull final Importer importer,
                      @NonNull final ImportHelper helper,
                      @NonNull final TaskListener<ImportHelper> taskListener) {
        super(R.id.TASK_ID_IMPORTER, taskListener);
        mImporter = importer;
        mHelper = helper;
    }

    @Override
    @WorkerThread
    @NonNull
    protected ImportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try (InputStream is = context.getContentResolver().openInputStream(mHelper.getUri())) {
            if (is == null) {
                throw new IOException("InputStream was NULL");
            }

            mHelper.getResults().add(mImporter.readBooks(context, is, getProgressListener()));
            //            if (!isCancelled()) {
//                mHelper.onSuccess();
//            }
            return mHelper;

        } catch (@NonNull final IOException | ImportException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return mHelper;

        } finally {
            cleanup();
        }
    }

    @Override
    @UiThread
    protected void onCancelled(@NonNull final ImportHelper helper) {
        cleanup();
        super.onCancelled(helper);
    }

    private void cleanup() {
        //mHelper.onCleanup(context);
        try {
            mImporter.close();
        } catch (@NonNull final IOException ignore) {
            // ignore
        }
    }
}
