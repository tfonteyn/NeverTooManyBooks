package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

public class RestoreTask
        extends TaskWithProgress<ImportSettings> {

    @NonNull
    private final ImportSettings mSettings;

    /**
     * @param taskId   a task identifier, will be returned in the task finished listener.
     * @param context  the caller context
     * @param settings the import settings
     */
    @UiThread
    public RestoreTask(final int taskId,
                       @NonNull final FragmentActivity context,
                       @NonNull final ImportSettings /* in/out */settings) {
        super(taskId, UniqueId.TFT_IMPORT_ARCHIVE, context, false, R.string.progress_msg_importing);
        mSettings = settings;

        if ((mSettings.what & ImportSettings.MASK) == 0) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportSettings doInBackground(final Void... params) {
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.readFrom(mSettings.file)) {
            //noinspection ConstantConditions
            reader.restore(mSettings, new BackupReader.BackupReaderListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }

                @Override
                public void onProgressStep(@NonNull final String message,
                                           final int delta) {
                    mProgress += delta;
                    publishProgress(message, mProgress);
                }

                @Override
                public boolean isCancelled() {
                    return RestoreTask.this.isCancelled();
                }
            });
        } catch (IOException e) {
            Logger.error(e);
            mException = e;
        }
        return mSettings;
    }
}
