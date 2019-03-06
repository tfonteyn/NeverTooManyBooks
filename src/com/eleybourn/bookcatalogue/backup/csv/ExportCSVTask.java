package com.eleybourn.bookcatalogue.backup.csv;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class ExportCSVTask
        extends AsyncTask<Void, Object, Void> {

    public static final String TAG = ExportCSVTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_CSV_EXPORT;
    protected final ProgressDialogFragment<Void> mFragment;
    @NonNull
    private final CsvExporter mExporter;
    private final File tmpFile;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param settings the export settings
     */
    @UiThread
    public ExportCSVTask(@NonNull final ProgressDialogFragment<Void> frag,
                         @NonNull final ExportSettings settings) {
        mFragment = frag;
        mFragment.setTask(M_TASK_ID, this);
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

        try (FileOutputStream out = new FileOutputStream(tmpFile)) {
            // start the export
            mExporter.doBooks(out, new Exporter.ExportListener() {
                @Override
                public void onProgress(@NonNull final String message,
                                       final int position) {
                    publishProgress(message, position);
                }

                @Override
                public boolean isCancelled() {
                    return ExportCSVTask.this.isCancelled();
                }

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }
            });

            if (isCancelled()) {
                return null;
            }
            // success
            mExporter.renameFiles(tmpFile);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(e);
            mException = e;
            cleanup();
        }
        return null;
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
    protected void onPostExecute(@Nullable final Void result) {
        mFragment.taskFinished(M_TASK_ID, mException == null, result);
    }
}
