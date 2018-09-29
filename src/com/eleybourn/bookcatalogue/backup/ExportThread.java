package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
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

    /** backup copies to keep */
    private static final int COPIES = 5;

    private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {
        @Override
        public void onProgress(@NonNull final String message, final int position) {
            if (position > 0) {
                mManager.doProgress(ExportThread.this, message, position);
            } else {
                mManager.doProgress(message);
            }
        }

        @Override
        public boolean isCancelled() {
            return ExportThread.this.isCancelled();
        }

        @Override
        public void setMax(final int max) {
            mManager.setMax(ExportThread.this, max);
        }

    };
    private CatalogueDBAdapter mDb;

    public ExportThread(@NonNull final TaskManager manager) {
        super(manager);
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mDb.open();
    }

    @Override
    protected void onRun() {
        try {
            final FileOutputStream out = new FileOutputStream(StorageUtils.getTempExportFile());

            new CsvExporter().export(out, mOnExportListener, Exporter.EXPORT_ALL, null);

            if (out.getChannel().isOpen()) {
                out.close();
            }
            renameFiles();
        } catch (IOException e) {
            Logger.logError(e);
            mManager.doToast(getString(R.string.export_failed_sdcard));
        }
    }

    @Override
    protected void onThreadFinish() {
        cleanup();
    }

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

    private void cleanup() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Backup the current file
     */
    private void renameFiles() {
        final File temp = StorageUtils.getTempExportFile();
        if (isCancelled()) {
            StorageUtils.deleteFile(temp);
        } else {
            String fmt = "export.%s.csv";
            File fLast = StorageUtils.getFile(String.format(fmt, COPIES));
             StorageUtils.deleteFile(fLast);

            for (int i = COPIES - 1; i > 0; i--) {
                final File fCurr = StorageUtils.getFile(String.format(fmt, i));
                StorageUtils.renameFile(fCurr, fLast);
                fLast = fCurr;
            }
            final File export = StorageUtils.getExportFile();
            StorageUtils.renameFile(export, fLast);
            StorageUtils.renameFile(temp, export);
        }
    }
}