package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExportCSVTask
        extends TaskWithProgress<Void> {

    @NonNull
    private final CsvExporter mExporter;

    private final File tmpFile;

    /**
     * Constructor.
     *
     * @param taskId   a generic identifier
     * @param context  the calling fragment
     * @param settings the export settings
     */
    public ExportCSVTask(final int taskId,
                         @NonNull final FragmentActivity context,
                         @NonNull final ExportSettings settings) {

        super(taskId, context, R.string.progress_msg_backing_up, false);

        mFragment.setNumberFormat(null);
        mExporter = new CsvExporter(settings);
        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    @Override
    @Nullable
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
                    return mFragment.isCancelled();
                }

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }
            });

            if (!mFragment.isCancelled()) {
                // success
                mExporter.renameFiles(tmpFile);
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(e);
            mException = e;
            // cleanup
            StorageUtils.deleteFile(tmpFile);
        } finally {
            if (mFragment.isCancelled() || mException != null) {
                // cleanup
                StorageUtils.deleteFile(tmpFile);
            }
        }
        return null;
    }
}
