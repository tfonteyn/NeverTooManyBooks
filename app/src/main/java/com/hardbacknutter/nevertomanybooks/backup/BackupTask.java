/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup;

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

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

public class BackupTask
        extends TaskBase<ExportOptions> {

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportOptions mSettings;
    @NonNull
    private final File mTmpFile;

    /**
     * Constructor.
     *
     * @param settings     the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public BackupTask(@NonNull final ExportOptions /* in/out */ settings,
                      @NonNull final TaskListener<ExportOptions> taskListener) {
        super(R.id.TASK_ID_WRITE_TO_ARCHIVE, taskListener);
        mSettings = settings;
        // sanity checks
        if ((mSettings.file == null) || ((mSettings.what & ExportOptions.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + mSettings);
        }

        // Ensure the file key extension is what we want
        if (!BackupManager.isArchive(mSettings.file)) {
            mSettings.file = new File(mSettings.file.getAbsoluteFile()
                                      + BackupManager.ARCHIVE_EXTENSION);
        }

        // we write to a temp file, and will rename it upon success (or delete on failure).
        mTmpFile = new File(mSettings.file.getAbsolutePath() + ".tmp");
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

        Context userContext = App.getFakeUserContext();
        try (BackupWriter writer = BackupManager.getWriter(userContext, mTmpFile)) {

            writer.backup(mSettings, new ProgressListener() {

                private int mAbsPosition;
                private int mMaxPosition;

                @Override
                public void setMax(final int maxPosition) {
                    mMaxPosition = maxPosition;
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final Object message) {
                    mAbsPosition += delta;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            mAbsPosition, values));
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final Object message) {
                    mAbsPosition = absPosition;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
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

            // success
            StorageUtils.deleteFile(mSettings.file);
            //noinspection ConstantConditions
            StorageUtils.renameFile(mTmpFile, mSettings.file);

            SharedPreferences.Editor ed = PreferenceManager
                                                  .getDefaultSharedPreferences(App.getAppContext())
                                                  .edit();
            // if the backup was a full one (not a 'since') remember that.
            if ((mSettings.what & ExportOptions.EXPORT_SINCE) == 0) {
                ed.putString(BackupManager.PREF_LAST_BACKUP_DATE, mBackupDate);
            }
            ed.putString(BackupManager.PREF_LAST_BACKUP_FILE, mSettings.file.getAbsolutePath());
            ed.apply();

        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return mSettings;
    }
}
