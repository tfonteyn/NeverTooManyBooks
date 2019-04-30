package com.eleybourn.bookcatalogue.backup;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class RestoreTask
        extends AsyncTask<Void, Object, ImportSettings> {

    /** Fragment manager tag. */
    public static final String TAG = RestoreTask.class.getSimpleName();

    @NonNull
    private final ProgressDialogFragment<ImportSettings> mProgressDialog;
    @NonNull
    private final ImportSettings mSettings;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * @param progressDialog ProgressDialogFragment
     * @param settings       the import settings
     */
    @UiThread
    public RestoreTask(@NonNull final ProgressDialogFragment<ImportSettings> progressDialog,
                       @NonNull final ImportSettings /* in/out */settings) {

        mProgressDialog = progressDialog;
        mSettings = settings;
        if (((mSettings.what & ImportSettings.MASK) == 0) || (mSettings.file == null)) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportSettings doInBackground(final Void... params) {

        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.readFrom(mSettings.file)) {

            reader.restore(mSettings, new BackupReader.BackupReaderListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mProgressDialog.setMax(max);
                }

                @Override
                public void onProgressStep(final int delta,
                                           @NonNull final String message) {
                    mProgress += delta;
                    publishProgress(mProgress, message);
                }

                @Override
                public void onProgress(final int position,
                                       @NonNull final String message) {
                    publishProgress(position, message);
                }

                @Override
                public boolean isCancelled() {
                    return RestoreTask.this.isCancelled();
                }
            });

        } catch (IOException e) {
            Logger.error(this, e);
            mException = e;
        } catch (ImportException e) {
            Logger.error(this, e);
            mException = e;
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
    protected void onPostExecute(@NonNull final ImportSettings result) {
        mProgressDialog.onTaskFinished(mException == null, result, mException);
    }
}
