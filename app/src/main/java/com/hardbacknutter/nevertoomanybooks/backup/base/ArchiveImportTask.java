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
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Input: {@link ImportManager}.
 * Output: the updated {@link ImportManager} with the {@link ImportResults }.
 */
public class ArchiveImportTask
        extends VMTask<ImportManager> {

    /** Log tag. */
    private static final String TAG = "ArchiveImportTask";

    /** import configuration. */
    private ImportManager mHelper;

    /**
     * Start the task.
     *
     * @param helper import configuration
     */
    @UiThread
    public void startImport(@NonNull final ImportManager helper) {
        mHelper = helper;
        mHelper.validate();

        execute(R.id.TASK_ID_IMPORT);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportManager doWork()
            throws IOException, ImportException, InvalidArchiveException {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try (ArchiveReader reader = mHelper.getArchiveReader(context)) {
            mHelper.setResults(reader.read(context, this));

        }
        return mHelper;
    }
}
