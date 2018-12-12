package com.eleybourn.bookcatalogue.backup.csv;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class to handle import in a separate thread.
 *
 * @author Philip Warner
 */
public class CsvImportTask extends ManagedTask {
    @NonNull
    private final ImportSettings mSettings;
    @NonNull
    private final Importer.CoverFinder mCoverFinder;
    private final Importer.OnImporterListener mImportListener = new Importer.OnImporterListener() {

        @Override
        public void onProgress(final @NonNull String message, final int position) {
            if (position > 0) {
                mTaskManager.sendTaskProgressMessage(CsvImportTask.this, message, position);
            } else {
                mTaskManager.sendHeaderTaskProgressMessage(message);
            }
        }

		@Override
		public boolean isActive() {
			return !CsvImportTask.this.isCancelled();
		}

        @Override
        public void setMax(final int max) {
            mTaskManager.setMaxProgress(CsvImportTask.this, max);
        }
    };

    @NonNull
    private final CsvImporter mImporter;

    public CsvImportTask(final @NonNull TaskManager manager, final @NonNull ImportSettings settings) {
        super("CsvImportTask", manager);

        mSettings = settings;
        mImporter = new CsvImporter(settings);
        mCoverFinder = new LocalCoverFinder(manager.getContext(),
                // the source is the folder from which we are importing.
                mSettings.file.getParent(),
                // If this is not the Shared Storage folder, we'll be doing copies, else renames (to 'cover' folder)
                StorageUtils.getSharedStorage().getAbsolutePath());
    }

    @Override
    protected void runTask() {
        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            mImporter.doImport(in, mCoverFinder, mImportListener);

            if (isCancelled()) {
                mFinalMessage = getString(R.string.progress_end_cancelled);
            } else {
                mFinalMessage = getString(R.string.progress_end_import_complete);
            }
        } catch (IOException e) {
            mFinalMessage = getString(R.string.import_error_failed_is_csv_file_location_correct);
            Logger.error(e);
        }
    }

    @Override
    protected void onTaskFinish() {
        try {
            mCoverFinder.close();
        } catch (Exception ignore) {
        }
    }
}
