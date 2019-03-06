package com.eleybourn.bookcatalogue.backup;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class RestoreTask
        extends AsyncTask<Void, Object, ImportSettings> {

    public static final String TAG = RestoreTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_READ_FROM_ARCHIVE;
    protected final ProgressDialogFragment<ImportSettings> mFragment;
    @NonNull
    private final ImportSettings mSettings;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * @param settings the import settings
     */
    @UiThread
    public RestoreTask(@NonNull final ProgressDialogFragment<ImportSettings> frag,
                       @NonNull final ImportSettings /* in/out */settings) {
        mFragment = frag;
        mFragment.setTask(M_TASK_ID, this);
        mSettings = settings;
        if ((mSettings.what & ImportSettings.MASK) == 0) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportSettings doInBackground(final Void... params) {
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.readFrom(mSettings.file)) {
            //noinspection ConstantConditions
            reader.restore(mSettings, new BackupReader.BackupReaderListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }

                @Override
                public void onProgressStep(@NonNull final String message,
                                           final int delta) {
                    mProgress += delta;
                    publishProgress(message, mProgress);
                }

                @Override
                public boolean isCancelled() {
                    return RestoreTask.this.isCancelled();
                }
            });
        } catch (IOException e) {
            Logger.error(e);
            mException = e;
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
    protected void onPostExecute(@NonNull final ImportSettings result) {
        mFragment.taskFinished(M_TASK_ID, mException == null, result);
    }
}
