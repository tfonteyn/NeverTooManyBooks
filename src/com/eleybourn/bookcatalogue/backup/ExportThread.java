package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class to handle export in a separate thread.
 *
 * @author Philip Warner
 */
public class ExportThread extends ManagedTask {

    private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {
        @Override
        public void onProgress(@NonNull final String message, final int position) {
            if (position > 0) {
                mTaskManager.doProgress(ExportThread.this, message, position);
            } else {
                mTaskManager.doProgress(message);
            }
        }

        @Override
        public boolean isCancelled() {
            return ExportThread.this.isCancelled();
        }

        @Override
        public void setMax(final int max) {
            mTaskManager.setMax(ExportThread.this, max);
        }

    };

    @NonNull
    private final CsvExporter mExporter;

    public ExportThread(@NonNull final TaskManager manager) {
        super("ExportThread", manager);
        mExporter = new CsvExporter();
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
            mTaskManager.showBriefMessage(getString(R.string.error_export_failed));
        }
    }

    /**
     * Backup the current file
     */
    private void renameFiles(@NonNull final File temp) {
        if (isCancelled()) {
            StorageUtils.deleteFile(temp);
        } else {
            mExporter.renameFiles(temp);
        }
    }

}