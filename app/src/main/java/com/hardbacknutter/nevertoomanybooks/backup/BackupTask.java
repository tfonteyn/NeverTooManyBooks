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
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Calls the default {@link  BackupManager#getWriter} to create a backup.
 */
public class BackupTask
        extends TaskBase<Void, ExportHelper> {

    /** Log tag. */
    private static final String TAG = "BackupTask";

    /** what and how to export. */
    @NonNull
    private final ExportHelper mExportHelper;

    /**
     * Constructor.
     *
     * @param exportHelper the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public BackupTask(@NonNull final ExportHelper exportHelper,
                      @NonNull final TaskListener<ExportHelper> taskListener) {
        super(R.id.TASK_ID_WRITE_TO_ARCHIVE, taskListener);
        mExportHelper = exportHelper;
        mExportHelper.validate();
    }

    @UiThread
    @Override
    protected void onCancelled(@NonNull final ExportHelper result) {
        cleanup(App.getAppContext());
        super.onCancelled(result);
    }

    @AnyThread
    private void cleanup(@NonNull final Context context) {
        StorageUtils.deleteFile(ExportHelper.getTempFile(context));
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);

        Context context = App.getLocalizedAppContext();
        Uri uri = Uri.fromFile(ExportHelper.getTempFile(context));
        try (BackupWriter writer = BackupManager.getWriter(context, uri)) {

            writer.backup(context, mExportHelper, getProgressListener());
            if (!isCancelled()) {
                Objects.requireNonNull(mExportHelper.uri, ErrorMsg.NULL_URI);
                // the export was successful
                StorageUtils.exportFile(context, ExportHelper.getTempFile(context),
                                        mExportHelper.uri);

                // if the backup was a full one (not a 'since') remember that.
                if ((mExportHelper.options & ExportHelper.EXPORT_SINCE) == 0) {
                    BackupManager.setLastFullBackupDate(context);
                }

            }
            return mExportHelper;

        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return mExportHelper;
        } finally {
            cleanup(context);
        }
    }
}
