package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

import java.io.FileInputStream;
import java.io.IOException;

public class ImportCSVTask
        extends TaskWithProgress<Void> {

    private final ImportSettings mSettings;
    private final CsvImporter mImporter;

    /**
     * Constructor.
     *
     * @param taskId   a generic identifier
     * @param context  the calling fragment
     * @param settings the import settings
     */
    public ImportCSVTask(final int taskId,
                         @NonNull final FragmentActivity context,
                         @NonNull final ImportSettings settings) {

        super(taskId, context, R.string.progress_msg_importing, false);
        mSettings = settings;
        mImporter = new CsvImporter(settings);
    }

    @Override
    @Nullable
    protected Void doInBackground(final Void... params) {

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            mImporter.doImport(in, new LocalCoverFinder(mSettings.file.getParent()),
                               new Importer.ImportListener() {

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

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(e);
            mException = e;
        }

        return null;
    }
}
