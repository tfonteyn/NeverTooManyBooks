package com.eleybourn.bookcatalogue.backup.csv;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.FileInputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.ImportException;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class ImportCSVTask
        extends AsyncTask<Void, Object, Void> {

    /** Fragment manager tag. */
    private static final String TAG = ImportCSVTask.class.getSimpleName();

    private final ImportSettings mSettings;
    private final Importer mImporter;
    private final ProgressDialogFragment<Void> mProgressDialog;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param settings       the import settings
     * @param progressDialog ProgressDialogFragment
     */
    @UiThread
    public ImportCSVTask(@NonNull final ImportSettings settings,
                         @NonNull final ProgressDialogFragment<Void> progressDialog) {

        mProgressDialog = progressDialog;
        mSettings = settings;
        mImporter = new CsvImporter(settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Void doInBackground(final Void... params) {

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doBooks(in, new LocalCoverFinder(mSettings.file.getParent()),
                              new Importer.ImportListener() {

                                  @Override
                                  public void onProgress(final int position,
                                                         @NonNull final String message) {
                                      publishProgress(position, message);
                                  }

                                  @Override
                                  public boolean isCancelled() {
                                      return ImportCSVTask.this.isCancelled();
                                  }

                                  @Override
                                  public void setMax(final int max) {
                                      mProgressDialog.setMax(max);
                                  }
                              });

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(this, e);
            mException = e;

        } catch (ImportException e) {
            Logger.error(this, e);
            mException = e;

        } finally {
            try {
                mImporter.close();
            } catch (IOException ignore) {
            }
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

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Void result) {
        mProgressDialog.onTaskFinished(mException == null, result, mException);
    }
}
