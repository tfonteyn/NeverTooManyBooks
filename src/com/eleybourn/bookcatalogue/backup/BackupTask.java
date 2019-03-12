package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.backup.ui.BackupFileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class BackupTask
        extends AsyncTask<Void, Object, ExportSettings> {

    public static final String TAG = BackupTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_SAVE_TO_ARCHIVE;

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    private final ProgressDialogFragment<ExportSettings> mFragment;

    /**
     * Constructor.
     *
     * @param settings the export settings
     */
    @UiThread
    public BackupTask(@NonNull final ProgressDialogFragment<ExportSettings> frag,
                      @NonNull final ExportSettings settings) {

        mFragment = frag;
        mFragment.setTask(M_TASK_ID, this);
        mSettings = settings;
        // sanity checks
        if ((mSettings.file == null) || ((mSettings.what & ExportSettings.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + mSettings);
        }

        // Ensure the file key extension is what we want
        if (!BackupFileDetails.isArchive(mSettings.file)) {
            mSettings.file = new File(mSettings.file.getAbsoluteFile()
                                              + BackupFileDetails.ARCHIVE_EXTENSION);
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
                return mSettings;
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

    /**
     * @param values: [0] String message, [1] Integer position/delta
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        mFragment.onProgress((String) values[0], (Integer) values[1]);
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@NonNull final ExportSettings result) {
        mFragment.taskFinished(M_TASK_ID, mException == null, result);
    }
}
