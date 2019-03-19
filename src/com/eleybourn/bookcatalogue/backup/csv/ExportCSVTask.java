package com.eleybourn.bookcatalogue.backup.csv;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;

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

    private static final String TAG = ExportCSVTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_CSV_EXPORT;
    @NonNull
    private final ProgressDialogFragment<Void> mFragment;
    @NonNull
    private final CsvExporter mExporter;
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
     * @param fragment ProgressDialogFragment
     * @param settings the export settings
     */
    @UiThread
    private ExportCSVTask(@NonNull final ProgressDialogFragment<Void> fragment,
                          @NonNull final ExportSettings settings) {
        mFragment = fragment;
        mExporter = new CsvExporter(settings);
        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    /**
     * @param fm       FragmentManager
     * @param settings the export settings
     */
    @UiThread
    public static void start(@NonNull final FragmentManager fm,
                             @NonNull final ExportSettings settings) {
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<Void> frag =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_backing_up,
                                                       false, 0);
            ExportCSVTask task = new ExportCSVTask(frag, settings);
            frag.setTask(M_TASK_ID, task);
            frag.show(fm, TAG);
            task.execute();
        }
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
        mFragment.onTaskFinished(mException == null, result);
    }
}
