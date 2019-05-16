package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.FileInputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportException;
import com.eleybourn.bookcatalogue.backup.ImportOptions;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

public class ImportCSVTask
        extends TaskWithProgress<Object, Integer> {

    private final ImportOptions mSettings;
    private final Importer mImporter;

    /**
     * Constructor.
     *
     * @param settings       the import settings
     * @param progressDialog ProgressDialogFragment
     */
    @UiThread
    public ImportCSVTask(@NonNull final ImportOptions settings,
                         @NonNull final ProgressDialogFragment<Object, Integer> progressDialog) {
        super(R.id.TASK_ID_CSV_IMPORT, progressDialog);

        mSettings = settings;
        mImporter = new CsvImporter(settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("ImportCSVTask");

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doBooks(in, new LocalCoverFinder(mSettings.file.getParent()),
                              new ProgressListener() {
                                  @Override
                                  public void setMax(final int max) {
                                      mProgressDialog.setMax(max);
                                  }

                                  @Override
                                  public void onProgress(final int absPosition,
                                                         @Nullable final String message) {
                                      publishProgress(absPosition, message);
                                  }

                                  @Override
                                  public void onProgress(final int absPosition,
                                                         @StringRes final int messageId) {
                                      publishProgress(absPosition, messageId);
                                  }

                                  @Override
                                  public boolean isCancelled() {
                                      return ImportCSVTask.this.isCancelled();
                                  }
                              }
            );

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
}
