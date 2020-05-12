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
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Input: {@link ImportManager}.
 * Output: the updated {@link ImportManager} with the {@link ImportResults }.
 */
public class ArchiveImportTask
        extends TaskBase<ImportManager> {

    /** Log tag. */
    private static final String TAG = "ArchiveImportTask";

    /** import configuration. */
    @NonNull
    private final ImportManager mHelper;

    /**
     * Constructor.
     *
     * @param helper       import configuration
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ArchiveImportTask(@NonNull final ImportManager helper,
                             @NonNull final TaskListener<ImportManager> taskListener) {
        super(R.id.TASK_ID_IMPORT, taskListener);
        mHelper = helper;
        mHelper.validate();
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportManager doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try (ArchiveReader reader = mHelper.getArchiveReader(context)) {
            mHelper.setResults(reader.read(context, getProgressListener()));

        } catch (@NonNull final IOException | ImportException | InvalidArchiveException e) {
            Logger.error(context, TAG, e);
            mException = e;
        }
        return mHelper;
    }
}
