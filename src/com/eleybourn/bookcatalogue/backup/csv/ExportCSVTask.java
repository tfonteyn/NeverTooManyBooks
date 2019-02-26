package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class ExportCSVTask
        extends TaskWithProgress<Void> {

    @NonNull
    private final CsvExporter mExporter;

    private final File tmpFile;

    /**
     * Constructor.
     *
     * @param taskId      a task identifier, will be returned in the task finished listener.
     * @param fragmentTag tag for the progress fragment
     * @param context     the caller context
     * @param settings    the export settings
     */
    @UiThread
    public ExportCSVTask(final int taskId,
                         @NonNull final String fragmentTag,
                         @NonNull final FragmentActivity context,
                         @NonNull final ExportSettings settings) {

        super(taskId, fragmentTag, context, false, R.string.progress_msg_backing_up);

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
}
