package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.backup.ui.BackupFileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class BackupTask
        extends TaskWithProgress<ExportSettings> {

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;

    /**
     * Constructor.
     *
     * @param taskId      a task identifier, will be returned in the task finished listener.
     * @param context     the caller context
     * @param settings    the export settings
     */
    @UiThread
    public BackupTask(final int taskId,
                      @NonNull final FragmentActivity context,
                      @NonNull final ExportSettings settings) {
        super(taskId, UniqueId.TFT_BACKUP, context, false, R.string.progress_msg_backing_up);
        mSettings = settings;

        // sanity checks
        if ((mSettings.file == null) || ((mSettings.what & ExportSettings.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + mSettings);
        }

        // Ensure the file key extension is what we want
        if (!BackupFileDetails.isArchive(mSettings.file)) {
            mSettings.file = new File(
                    mSettings.file.getAbsoluteFile() + BackupFileDetails.ARCHIVE_EXTENSION);
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
    @Nullable
    @WorkerThread
    protected ExportSettings doInBackground(final Void... params) {
        BackupContainer bkp = new TarBackupContainer(mTmpFile);
        try (BackupWriter wrt = bkp.newWriter()) {
            // go go go...
            wrt.backup(mSettings, new BackupWriter.BackupWriterListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }

                @Override
                public void onProgressStep(@Nullable final String message,
                                           final int delta) {
                    mProgress += delta;
                    publishProgress(message, mProgress);
                }

                @Override
                public boolean isCancelled() {
                    return BackupTask.this.isCancelled();
                }
            });

            if (isCancelled()) {
                return null;
            }

            // success
            StorageUtils.deleteFile(mSettings.file);
            //noinspection ConstantConditions
            StorageUtils.renameFile(mTmpFile, mSettings.file);

            SharedPreferences.Editor ed = Prefs.getPrefs().edit();
            // if the backup was a full one (not a 'since') remember that.
            if ((mSettings.what & ExportSettings.ALL) != 0) {
                ed.putString(BackupManager.PREF_LAST_BACKUP_DATE, mBackupDate);
            }
            ed.putString(BackupManager.PREF_LAST_BACKUP_FILE, mSettings.file.getAbsolutePath());
            ed.apply();

        } catch (IOException e) {
            Logger.error(e);
            mException = e;
            cleanup();
        }

        return mSettings;
    }
}
