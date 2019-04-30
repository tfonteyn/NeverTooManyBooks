package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.backup.ui.BackupFileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class BackupTask
        extends AsyncTask<Void, Object, ExportSettings> {

    /** Fragment manager tag. */
    public static final String TAG = BackupTask.class.getSimpleName();

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;
    @NonNull
    private final ProgressDialogFragment<ExportSettings> mProgressDialog;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param progressDialog ProgressDialogFragment
     * @param settings       the export settings
     */
    @UiThread
    public BackupTask(@NonNull final ProgressDialogFragment<ExportSettings> progressDialog,
                      @NonNull final ExportSettings settings)
            throws IOException {

        mProgressDialog = progressDialog;
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

        try (BackupWriter mBackupWriter = new TarBackupContainer(mTmpFile).newWriter()) {

            mBackupWriter.backup(mSettings, new BackupWriter.BackupWriterListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mProgressDialog.setMax(max);
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final String message) {
                    mProgress += delta;
                    publishProgress(mProgress, message);
                }

                @Override
                public void onProgressStep(final int delta,
                                           @StringRes final int messageId) {
                    mProgress += delta;
                    publishProgress(mProgress, messageId);
                }

                @Override
                public void onProgress(final int position,
                                       @Nullable final String message) {
                    publishProgress(position, message);
                }

                @Override
                public void onProgress(final int position,
                                       @StringRes final int messageId) {
                    publishProgress(position, messageId);
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

    /**
     * @param values: [0] Integer position/delta, [1] String message
     *                [0] Integer position/delta, [1] StringRes messageId
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        if (values[1] instanceof String) {
            mProgressDialog.onProgress((Integer) values[0], (String) values[1]);
        } else {
            mProgressDialog.onProgress((Integer) values[0], (Integer) values[1]);
        }
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
        mProgressDialog.onTaskFinished(mException == null, result, mException);
    }
}
