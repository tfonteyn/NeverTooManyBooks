/*
 * @Copyright 2019 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Writes to a fixed file {@link ExportHelper#getTempFile},
 * an on being successful, copies that file to the external Uri.
 */
public class BackupTask
        extends TaskBase<ExportHelper> {

    private static final String TAG = "BackupTask";

    /** what and how to export. */
    @NonNull
    private final ExportHelper mExportHelper;

    private final ProgressListener mProgressListener = new ProgressListenerBase() {

        private int mPos;

        @Override
        public void onProgressStep(final int delta,
                                   @Nullable final String message) {
            mPos += delta;
            publishProgress(new TaskListener.ProgressMessage(mTaskId, getMax(), mPos, message));
        }

        @Override
        public void onProgress(final int pos,
                               @Nullable final String message) {
            mPos = pos;
            publishProgress(new TaskListener.ProgressMessage(mTaskId, getMax(), mPos, message));
        }

        @Override
        public boolean isCancelled() {
            return BackupTask.this.isCancelled();
        }
    };

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
        cleanup();
        super.onCancelled(result);
    }

    @AnyThread
    private void cleanup() {
        StorageUtils.deleteFile(ExportHelper.getTempFile(App.getAppContext()));
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName("BackupTask");

        Context localizedAppContext = App.getLocalizedAppContext();
        Uri uri = Uri.fromFile(ExportHelper.getTempFile(localizedAppContext));
        try (BackupWriter writer = BackupManager.getWriter(localizedAppContext, uri)) {

            writer.backup(localizedAppContext, mExportHelper, mProgressListener);
            if (!isCancelled()) {
                // the export was successful
                //noinspection ConstantConditions
                StorageUtils.exportFile(ExportHelper.getTempFile(localizedAppContext),
                                        mExportHelper.uri);

                // if the backup was a full one (not a 'since') remember that.
                if ((mExportHelper.options & ExportHelper.EXPORT_SINCE) == 0) {
                    BackupManager.setLastFullBackupDate(localizedAppContext);
                }

            }
            return mExportHelper;

        } catch (@NonNull final IOException e) {
            Logger.error(localizedAppContext, TAG, e);
            mException = e;
            return mExportHelper;
        } finally {
            cleanup();
        }
    }

}
