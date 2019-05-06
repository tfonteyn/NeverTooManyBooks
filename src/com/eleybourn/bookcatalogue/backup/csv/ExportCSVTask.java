package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class ExportCSVTask
        extends TaskWithProgress<Object, Integer> {

    @NonNull
    private final Exporter mExporter;
    @NonNull
    private final File tmpFile;

    /**
     * Constructor.
     *
     * @param settings       the export settings
     * @param progressDialog ProgressDialogFragment
     */
    @UiThread
    public ExportCSVTask(@NonNull final ExportSettings settings,
                         @NonNull final ProgressDialogFragment<Object, Integer> progressDialog) {
        super(progressDialog);

        mExporter = new CsvExporter(settings);
        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    protected int getId() {
        return R.id.TASK_ID_CSV_EXPORT;
    }

    @Override
    @UiThread
    protected void onCancelled(final Integer result) {
        cleanup();
    }

    private void cleanup() {
        StorageUtils.deleteFile(tmpFile);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        try (OutputStream out = new FileOutputStream(tmpFile)) {
            // start the export
            mExporter.doBooks(out, new ProgressListener() {
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
                    return ExportCSVTask.this.isCancelled();
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
}
