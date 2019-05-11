package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class BackupTask
        extends TaskWithProgress<Object, ExportSettings> {

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;

    /**
     * Constructor.
     *
     * @param progressDialog ProgressDialogFragment
     * @param settings       the export settings
     */
    @UiThread
    public BackupTask(@NonNull final ProgressDialogFragment<Object, ExportSettings> progressDialog,
                      @NonNull final ExportSettings /* in/out */ settings) {
        super(R.id.TASK_ID_WRITE_TO_ARCHIVE, progressDialog);

        mSettings = settings;
        // sanity checks
        if ((mSettings.file == null) || ((mSettings.what & ExportSettings.MASK) == 0)) {
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
    protected void onCancelled(final ExportSettings result) {
        cleanup();
    }

    @AnyThread
    private void cleanup() {
        StorageUtils.deleteFile(mTmpFile);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ExportSettings doInBackground(final Void... params) {

        try (BackupWriter writer = BackupManager.getWriter(mTmpFile)) {

            writer.backup(mSettings, new ProgressListener() {

                private int mAbsPosition;

                @Override
                public void setMax(final int max) {
                    mProgressDialog.setMax(max);
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final String message) {
                    mAbsPosition += delta;
                    publishProgress(mAbsPosition, message);
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final String message) {
                    mAbsPosition = absPosition;
                    publishProgress(mAbsPosition, message);
                }

                @Override
                public void onProgress(final int absPosition,
                                       @StringRes final int messageId) {
                    mAbsPosition = absPosition;
                    publishProgress(mAbsPosition, messageId);
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
            if ((mSettings.what & ExportSettings.ALL) != 0) {
                ed.putString(BackupManager.PREF_LAST_BACKUP_DATE, mBackupDate);
            }
            ed.putString(BackupManager.PREF_LAST_BACKUP_FILE, mSettings.file.getAbsolutePath());
            ed.apply();

        } catch (IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return mSettings;
    }
}
