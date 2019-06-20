package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

public class RestoreTask
        extends TaskWithProgress<Object, ImportOptions> {

    @NonNull
    private final ImportOptions mSettings;

    /**
     * @param progressDialog ProgressDialogFragment
     * @param settings       the import settings
     */
    @UiThread
    public RestoreTask(@NonNull final ProgressDialogFragment<Object, ImportOptions> progressDialog,
                       @NonNull final ImportOptions /* in/out */ settings) {
        super(R.id.TASK_ID_READ_FROM_ARCHIVE, progressDialog);

        mSettings = settings;
        if (((mSettings.what & ImportOptions.MASK) == 0) || (mSettings.file == null)) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportOptions doInBackground(final Void... params) {
        Thread.currentThread().setName("RestoreTask");

        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(mSettings.file)) {

            reader.restore(mSettings, new ProgressListener() {

                private int mAbsPosition;

                @Override
                public void setMax(final int max) {
                    mProgressDialog.setMax(max);
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final String message) {
                    mAbsPosition += delta;
                    publishProgress(mAbsPosition, message);
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final String message) {
                    mAbsPosition = absPosition;
                    publishProgress(mAbsPosition, message);
                }

                @Override
                public void onProgress(final int absPosition,
                                       @StringRes final int messageId) {
                    mAbsPosition = absPosition;
                    publishProgress(mAbsPosition, messageId);
                }

                @Override
                public boolean isCancelled() {
                    return RestoreTask.this.isCancelled();
                }
            });

        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;
        } catch (@NonNull final ImportException e) {
            Logger.error(this, e);
            mException = e;
        }
        return mSettings;
    }
}
