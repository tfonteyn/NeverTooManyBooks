package com.eleybourn.bookcatalogue.backup.csv;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class ExportCSVTask
        extends AsyncTask<Void, Object, Void> {

    /** Fragment manager tag. */
    private static final String TAG = ExportCSVTask.class.getSimpleName();
    @NonNull
    private final ProgressDialogFragment<Void> mProgressDialog;
    @NonNull
    private final Exporter mExporter;
    @NonNull
    private final File tmpFile;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param settings       the export settings
     * @param progressDialog ProgressDialogFragment
     */
    @UiThread
    public ExportCSVTask(@NonNull final ExportSettings settings,
                         @NonNull final ProgressDialogFragment<Void> progressDialog) {
        mProgressDialog = progressDialog;
        mExporter = new CsvExporter(settings);
        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    @Override
    @UiThread
    protected void onCancelled(final Void result) {
        cleanup();
    }

    private void cleanup() {
        StorageUtils.deleteFile(tmpFile);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Void doInBackground(final Void... params) {
        try (OutputStream out = new FileOutputStream(tmpFile)) {
            // start the export
            mExporter.doBooks(out, new Exporter.ExportListener() {
                @Override
                public void onProgress(@NonNull final String message,
                                       final int position) {
                    publishProgress(position, message);
                }

                @Override
                public void onProgress(@StringRes final int messageId,
                                       final int position) {
                    publishProgress(position, messageId);
                }

                @Override
                public boolean isCancelled() {
                    return ExportCSVTask.this.isCancelled();
                }

                @Override
                public void setMax(final int max) {
                    mProgressDialog.setMax(max);
                }
            });

            if (isCancelled()) {
                return null;
            }
            // success
            CsvExporter.renameFiles(tmpFile);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return null;
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
    protected void onPostExecute(@Nullable final Void result) {
        mProgressDialog.onTaskFinished(mException == null, result, mException);
    }
}
