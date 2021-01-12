/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Input: {@link ImportHelper}.
 * Output: {@link ArchiveMetaData}.
 */
public class ArchiveReadMetaDataTask
        extends VMTask<ArchiveMetaData> {

    /** Log tag. */
    private static final String TAG = "ArchiveReadMetaDataTask";

    /** import configuration. */
    private ImportHelper mHelper;

    /**
     * Start the task.
     *
     * @param helper import configuration
     */
    @UiThread
    public void start(@NonNull final ImportHelper helper) {
        mHelper = helper;
        execute(R.id.TASK_ID_IMPORT_META_DATA);
    }

    @Nullable
    @Override
    @WorkerThread
    protected ArchiveMetaData doWork(@NonNull final Context context)
            throws InvalidArchiveException, GeneralParsingException,
                   IOException,
                   CertificateException, KeyManagementException {
        Thread.currentThread().setName(TAG);

        try (ArchiveReader reader = mHelper.createArchiveReader(context)) {
            return reader.readMetaData(context);
        }
    }
}
