package com.eleybourn.bookcatalogue.backup.csv;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class to handle exportBooks in a separate thread.
 *
 * @author Philip Warner
 */
public class CsvExportTask extends ManagedTask {

    private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {
        @Override
        public void onProgress(final @NonNull String message, final int position) {
            if (position > 0) {
                mTaskManager.sendTaskProgressMessage(CsvExportTask.this, message, position);
            } else {
                mTaskManager.sendHeaderTaskProgressMessage(message);
            }
        }

        @Override
        public boolean isCancelled() {
            return CsvExportTask.this.isCancelled();
        }

        @Override
        public void setMax(final int max) {
            mTaskManager.setMaxProgress(CsvExportTask.this, max);
        }

    };

    @NonNull
    private final CsvExporter mExporter;

    public CsvExportTask(final @NonNull TaskManager manager, final @NonNull ExportSettings settings) {
        super("CsvExportTask", manager);
        mExporter = new CsvExporter(manager.getContext(), settings);
    }

    @Override
    protected void runTask() {
        try {
            File tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
            final FileOutputStream out = new FileOutputStream(tmpFile);
            mExporter.exportBooks(out, mOnExportListener);

            if (out.getChannel().isOpen()) {
                out.close();
            }

            if (isCancelled()) {
                StorageUtils.deleteFile(tmpFile);
            } else {
                mExporter.renameFiles(tmpFile);
            }
        } catch (IOException e) {
            Logger.error(e);
            mFinalMessage = getString(R.string.export_error_csv_failed);
        }
    }
}