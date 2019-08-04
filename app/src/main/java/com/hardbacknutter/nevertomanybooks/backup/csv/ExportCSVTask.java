package com.hardbacknutter.nevertomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertomanybooks.backup.Exporter;
import com.hardbacknutter.nevertomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ExportCSVTask
        extends TaskBase<Integer> {

    @NonNull
    private final Exporter mExporter;
    @NonNull
    private final File tmpFile;

    /**
     * Constructor.
     *
     * @param context      Current context for accessing resources.
     * @param settings     the export settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ExportCSVTask(@NonNull final Context context,
                         @NonNull final ExportOptions settings,
                         @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_CSV_EXPORT, taskListener);
        mExporter = new CsvExporter(context, settings);

        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    @Override
    @UiThread
    protected void onCancelled(final Integer result) {
        cleanup();
        super.onCancelled(result);
    }

    private void cleanup() {
        StorageUtils.deleteFile(tmpFile);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("ExportCSVTask");

        try (OutputStream out = new FileOutputStream(tmpFile)) {
            // start the export
            mExporter.doBooks(out, new ProgressListener() {

                private int mMaxPosition;

                @Override
                public void setMax(final int maxPosition) {
                    mMaxPosition = maxPosition;
                }

                @Override
                public void incMax(final int delta) {
                    mMaxPosition += delta;
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final Object message) {
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            absPosition, values));
                }

                @Override
                public boolean isCancelled() {
                    return ExportCSVTask.this.isCancelled();
                }
            }, true);

            if (isCancelled()) {
                return null;
            }
            // success
            CsvExporter.renameFiles(tmpFile);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return null;
    }
}
