package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;

public class RestoreTask
        extends TaskBase<ImportOptions> {

    @NonNull
    private final ImportOptions mSettings;

    /**
     * Constructor.
     *
     * @param settings     the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RestoreTask(@NonNull final ImportOptions /* in/out */ settings,
                       @NonNull final TaskListener<ImportOptions> taskListener) {
        super(R.id.TASK_ID_READ_FROM_ARCHIVE, taskListener);
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

        //TODO: should be using a user context.
        Context context = App.getAppContext();
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(context, mSettings.file)) {

            reader.restore(mSettings, new ProgressListener() {

                private int mAbsPosition;
                private int mMaxPosition;

                @Override
                public void setMax(final int maxPosition) {
                    mMaxPosition = maxPosition;
                }

                @Override
                public void incMax(final int delta) {
                    mMaxPosition += delta;
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final Object message) {
                    mAbsPosition += delta;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            mAbsPosition, values));
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final Object message) {
                    mAbsPosition = absPosition;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            mAbsPosition, values));
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
