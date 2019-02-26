package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.FileInputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

public class ImportCSVTask
        extends TaskWithProgress<Void> {

    private final ImportSettings mSettings;
    private final CsvImporter mImporter;

    /**
     * Constructor.
     *
     * @param taskId      a task identifier, will be returned in the task finished listener.
     * @param fragmentTag tag for the progress fragment
     * @param context     the caller context
     * @param settings    the import settings
     */
    @UiThread
    public ImportCSVTask(final int taskId,
                         @NonNull final String fragmentTag,
                         @NonNull final FragmentActivity context,
                         @NonNull final ImportSettings settings) {

        super(taskId, fragmentTag, context, false, R.string.progress_msg_importing);
        mSettings = settings;
        mImporter = new CsvImporter(settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Void doInBackground(final Void... params) {

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doImport(in, new LocalCoverFinder(mSettings.file.getParent()),
                               new Importer.ImportListener() {

                                   @Override
                                   public void onProgress(@NonNull final String message,
                                                          final int position) {
                                       publishProgress(message, position);
                                   }

                                   @Override
                                   public boolean isCancelled() {
                                       return ImportCSVTask.this.isCancelled();
                                   }

                                   @Override
                                   public void setMax(final int max) {
                                       mFragment.setMax(max);
                                   }
                               });

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(e);
            mException = e;
        }

        return null;
    }
}
