package com.eleybourn.bookcatalogue.backup.ui;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.ui.BRBaseActivity.FileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Object to provide a FileListerFragmentTask specific to archive mFileDetails.
 *
 * @author pjw
 */
public class FileListerTask
        extends AsyncTask<Void, Object, ArrayList<FileDetails>> {

    /** Used by the {@link ProgressDialogFragment} for this task. */
    public static final String TAG = "FileListerTask";

    /**
     * Perform case-insensitive sorting using system locale (i.e. mFileDetails are system objects).
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
            (o1, o2) -> o1.getFile().getName().toLowerCase(LocaleUtils.getSystemLocale())
                          .compareTo(o2.getFile().getName().toLowerCase(
                                  LocaleUtils.getSystemLocale()));

    @NonNull
    private final WeakReference<TaskListener<Object, ArrayList<FileDetails>>> mTaskListener;

    @NonNull
    private final File mRootDir;

    private final int mTaskId = R.id.TASK_ID_FILE_LISTER;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param rootDir folder to list
     */
    @UiThread
    FileListerTask(@NonNull final File rootDir,
                   @NonNull final TaskListener<Object, ArrayList<FileDetails>> taskListener) {
        mRootDir = rootDir;
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @NonNull
    @WorkerThread
    protected ArrayList<FileDetails> doInBackground(final Void... params) {
        Thread.currentThread().setName("FileListerTask");

        ArrayList<FileDetails> fileDetails = new ArrayList<>();

        // Filter for directories and our own archives.
        FileFilter fileFilter = file ->
                (file.isDirectory() && file.canWrite())
                        || (file.isFile() && BackupManager.isArchive(file));

        // Get a file list
        File[] files = mRootDir.listFiles(fileFilter);
        if (files == null) {
            return fileDetails;
        }

        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            fileDetails.add(fd);
            if (BackupManager.isArchive(file)) {
                try (BackupReader reader = BackupManager.getReader(file)) {
                    fd.setInfo(reader.getInfo());
                } catch (IOException e) {
                    Logger.error(this, e);
                }
            }
        }

        Collections.sort(fileDetails, FILE_DETAILS_COMPARATOR);
        return fileDetails;
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@NonNull final ArrayList<FileDetails> result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
