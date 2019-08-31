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
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

public class BackupTask
        extends TaskBase<ExportOptions> {

    /** We write to a temp file. */
    @NonNull
    private final File mTmpFile;
    /** What to write. */
    @NonNull
    private final ExportOptions mSettings;
    /** Once writing was all done & a success, rename the temp file to the actual on. */
    @NonNull
    private File mOutputFile;


    /**
     * Constructor.
     *
     * @param file         File to write to
     * @param settings     the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public BackupTask(@NonNull final File file,
                      @NonNull final ExportOptions /* in/out */ settings,
                      @NonNull final TaskListener<ExportOptions> taskListener) {
        super(R.id.TASK_ID_WRITE_TO_ARCHIVE, taskListener);
        mOutputFile = file;
        mSettings = settings;
        // sanity checks
        if ((mOutputFile == null) || ((mSettings.what & ExportOptions.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + mSettings);
        }

        // Ensure the file key extension is what we want
        if (!BackupManager.isArchive(mOutputFile)) {
            mOutputFile = new File(mOutputFile.getAbsoluteFile()
                                   + BackupManager.ARCHIVE_EXTENSION);
        }

        // we write to a temp file, and will rename it upon success (or delete on failure).
        mTmpFile = new File(mOutputFile.getAbsolutePath() + ".tmp");
    }

    @UiThread
    @Override
    protected void onCancelled(@Nullable final ExportOptions result) {
        cleanup();
        super.onCancelled(result);
    }

    @AnyThread
    private void cleanup() {
        StorageUtils.deleteFile(mTmpFile);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportOptions doInBackground(final Void... params) {
        Thread.currentThread().setName("BackupTask");

        Context context = App.getLocalizedAppContext();
        try (BackupWriter writer = BackupManager.getWriter(context, mTmpFile)) {

            writer.backup(context, mSettings, new ProgressListenerBase() {

                private int mAbsPosition;

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final Object message) {
                    mAbsPosition += delta;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, getMax(),
                                                            mAbsPosition, values));
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final Object message) {
                    mAbsPosition = absPosition;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, getMax(),
                                                            mAbsPosition, values));
                }

                @Override
                public boolean isCancelled() {
                    return BackupTask.this.isCancelled();
                }
            });

            if (isCancelled()) {
                return mSettings;
            }

            // success, delete any existing one for paranoia's sake.
            StorageUtils.deleteFile(mOutputFile);
            StorageUtils.renameFile(mTmpFile, mOutputFile);

            SharedPreferences.Editor ed = PreferenceManager
                                                  .getDefaultSharedPreferences(context)
                                                  .edit();
            // if the backup was a full one (not a 'since') remember that.
            if ((mSettings.what & ExportOptions.EXPORT_SINCE) == 0) {
                ed.putString(BackupManager.PREF_LAST_BACKUP_DATE,
                             DateUtils.utcSqlDateTimeForToday());
            }
            ed.putString(BackupManager.PREF_LAST_BACKUP_FILE, mOutputFile.getAbsolutePath());
            ed.apply();

        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return mSettings;
    }
}
