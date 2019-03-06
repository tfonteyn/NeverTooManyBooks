package com.eleybourn.bookcatalogue.backup.csv;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.FileInputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class ImportCSVTask
        extends AsyncTask<Void, Object, Void> {

    public static final String TAG = ImportCSVTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_CSV_IMPORT;
    protected final ProgressDialogFragment<Void> mFragment;
    private final ImportSettings mSettings;
    private final CsvImporter mImporter;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param settings the import settings
     */
    @UiThread
    public ImportCSVTask(@NonNull final ProgressDialogFragment<Void> frag,
                         @NonNull final ImportSettings settings) {
        mFragment = frag;
        mFragment.setTask(M_TASK_ID, this);
        mSettings = settings;
        mImporter = new CsvImporter(settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Void doInBackground(final Void... params) {

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doImport(in, new LocalCoverFinder(mSettings.file.getParent()),
                               new Importer.ImportListener() {

                                   @Override
                                   public void onProgress(@NonNull final String message,
                                                          final int position) {
                                       publishProgress(message, position);
                                   }

                                   @Override
                                   public boolean isCancelled() {
                                       return ImportCSVTask.this.isCancelled();
                                   }

                                   @Override
                                   public void setMax(final int max) {
                                       mFragment.setMax(max);
                                   }
                               });

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(e);
            mException = e;
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
