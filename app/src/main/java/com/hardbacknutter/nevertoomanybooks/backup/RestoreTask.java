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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

public class RestoreTask
        extends TaskBase<Void, ImportHelper> {

    /** Log tag. */
    private static final String TAG = "RestoreTask";

    /** what and how to import. */
    @NonNull
    private final ImportHelper mImportHelper;

    /**
     * Constructor.
     *
     * @param importHelper the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RestoreTask(@NonNull final ImportHelper importHelper,
                       @NonNull final TaskListener<ImportHelper> taskListener) {
        super(R.id.TASK_ID_READ_FROM_ARCHIVE, taskListener);
        mImportHelper = importHelper;
        mImportHelper.validate();
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);

        Context context = App.getLocalizedAppContext();
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(context, mImportHelper.uri)) {
            reader.restore(context, mImportHelper, getProgressListener());

        } catch (@NonNull final IOException | ImportException | InvalidArchiveException e) {
            Logger.error(context, TAG, e);
            mException = e;
        }
        return mImportHelper;
    }
}
