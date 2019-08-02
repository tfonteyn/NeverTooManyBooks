package com.hardbacknutter.nevertomanybooks.backup;

import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

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

        try (BackupWriter writer = BackupManager.getWriter(mTmpFile)) {

            writer.backup(mSettings, new ProgressListener() {

                private int mAbsPosition;
                private int mMaxPosition;

                @Override
                public void setMax(final int maxPosition) {
                    mMaxPosition = maxPosition;
                }

                @Override
                public void incMax(final int delta) {
                    mMaxPosition += delta;
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

            SharedPreferences.Editor ed = App.getPrefs().edit();
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
