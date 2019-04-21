package com.eleybourn.bookcatalogue.backup;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class RestoreTask
        extends AsyncTask<Void, Object, ImportSettings> {

    /** Fragment manager tag. */
    private static final String TAG = RestoreTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_READ_FROM_ARCHIVE;
    @NonNull
    private final ProgressDialogFragment<ImportSettings> mFragment;
    @NonNull
    private final ImportSettings mSettings;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * @param fragment ProgressDialogFragment
     * @param settings the import settings
     */
    @UiThread
    private RestoreTask(@NonNull final ProgressDialogFragment<ImportSettings> fragment,
                        @NonNull final ImportSettings /* in/out */settings) {

        mFragment = fragment;
        mSettings = settings;
        if ((mSettings.what & ImportSettings.MASK) == 0) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    /**
     * @param fm       FragmentManager
     * @param settings the import settings
     */
    @UiThread
    public static void start(@NonNull final FragmentManager fm,
                             @NonNull final ImportSettings settings) {
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<ImportSettings> frag =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_importing,
                                                       false, 0);
            RestoreTask task = new RestoreTask(frag, settings);
            frag.setTask(M_TASK_ID, task);
            frag.show(fm, TAG);
            task.execute();
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportSettings doInBackground(final Void... params) {

        Context context = mFragment.getContextWithHorribleClutch();

        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.readFrom(context, mSettings.file)) {
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
            Logger.error(this, e);
            mException = e;
        }
        return mSettings;
    }

    /**
     * @param values: [0] String message, [1] Integer position/delta
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        mFragment.onProgress((String) values[0], (Integer) values[1]);
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@NonNull final ImportSettings result) {
        mFragment.onTaskFinished(mException == null, result);
    }
}
