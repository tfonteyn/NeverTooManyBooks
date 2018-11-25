package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class to handle export in a separate thread.
 *
 * @author Philip Warner
 */
public class ExportTask extends ManagedTask {

    private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {
        @Override
        public void onProgress(final @NonNull String message, final int position) {
            if (position > 0) {
                mTaskManager.sendTaskProgressMessage(ExportTask.this, message, position);
            } else {
                mTaskManager.sendHeaderTaskProgressMessage(message);
            }
        }

        @Override
        public boolean isCancelled() {
            return ExportTask.this.isCancelled();
        }

        @Override
        public void setMax(final int max) {
            mTaskManager.setMaxProgress(ExportTask.this, max);
        }

    };

    @NonNull
    private final CsvExporter mExporter;

    public ExportTask(final @NonNull TaskManager manager) {
        super("ExportTask", manager);
        mExporter = new CsvExporter(manager.getContext());
    }

    @Override
    protected void runTask() {
        try {
            File tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
            final FileOutputStream out = new FileOutputStream(tmpFile);

            mExporter.export(out, mOnExportListener, Exporter.EXPORT_ALL, null);

            if (out.getChannel().isOpen()) {
                out.close();
            }
            renameFiles(tmpFile);
        } catch (IOException e) {
            Logger.error(e);
            mTaskManager.sendTaskUserMessage(getString(R.string.error_export_failed));
        }
    }

    /**
     * Backup the current file
     */
    private void renameFiles(final @NonNull File temp) {
        if (isCancelled()) {
            StorageUtils.deleteFile(temp);
        } else {
            mExporter.renameFiles(temp);
        }
    }

}