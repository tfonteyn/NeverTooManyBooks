package com.eleybourn.bookcatalogue.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
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
    private static final String TAG = BackupTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_SAVE_TO_ARCHIVE;

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;
    @NonNull
    private final ProgressDialogFragment<ExportSettings> mFragment;

    private final BackupWriter mBackupWriter;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param fragment ProgressDialogFragment
     * @param settings the export settings
     */
    @UiThread
    private BackupTask(@NonNull final Context context,
                       @NonNull final ProgressDialogFragment<ExportSettings> fragment,
                       @NonNull final ExportSettings settings)
            throws IOException {

        mFragment = fragment;
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

        BackupContainer bkp = new TarBackupContainer(mTmpFile);
        mBackupWriter = bkp.newWriter(context);
    }

    /**
     * @param fm       FragmentManager
     * @param settings the export settings
     */
    @UiThread
    public static void start(@NonNull final Context context,
                             @NonNull final FragmentManager fm,
                             @NonNull final ExportSettings settings) {
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<ExportSettings> progressDialog =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_backing_up,
                                                       false, 0);
            BackupTask task;
            try {
                task = new BackupTask(context, progressDialog, settings);
                progressDialog.setTask(M_TASK_ID, task);
                progressDialog.show(fm, TAG);
                task.execute();
            } catch (IOException e) {
                progressDialog.onTaskFinished(false, settings);
            }
        }
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

        try {
            // go go go...
            mBackupWriter.backup(mSettings, new BackupWriter.BackupWriterListener() {

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
        } finally {
            try {
                mBackupWriter.close();
            } catch (IOException ignore) {
            }
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
        mFragment.onTaskFinished(mException == null, result);
    }
}
