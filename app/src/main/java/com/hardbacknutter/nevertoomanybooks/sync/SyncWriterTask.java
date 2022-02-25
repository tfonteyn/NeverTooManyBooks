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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class SyncWriterTask
        extends MTask<SyncWriterResults> {

    /** Log tag. */
    private static final String TAG = "SyncWriterTask";

    /** export configuration. */
    private SyncWriterHelper mHelper;

    public SyncWriterTask() {
        super(R.id.TASK_ID_EXPORT, TAG);
    }

    /**
     * Start the task.
     *
     * @param syncWriterHelper configuration
     */
    public void start(@NonNull final SyncWriterHelper syncWriterHelper) {
        mHelper = syncWriterHelper;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected SyncWriterResults doWork(@NonNull final Context context)
            throws ExportException,
                   IOException,
                   CertificateException,
                   StorageException {

        return mHelper.write(context, this);
    }
}
