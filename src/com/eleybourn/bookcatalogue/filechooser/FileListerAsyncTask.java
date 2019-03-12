package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

/**
 * Partially implements a FragmentTask to build a list of files in the background.
 *
 * @author pjw
 */
public abstract class FileListerAsyncTask
        extends AsyncTask<Void, Object, ArrayList<FileDetails>> {

    /**
     * Perform case-insensitive sorting using default locale.
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
            new Comparator<FileDetails>() {

                public int compare(@NonNull final FileDetails o1,
                                   @NonNull final FileDetails o2) {
                    return o1.getFile().getName().toLowerCase()
                             .compareTo(o2.getFile().getName().toLowerCase());
                }
            };
    private final int mTaskId;
    @NonNull
    private final File mRoot;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;
    protected final ProgressDialogFragment<ArrayList<FileDetails>> mFragment;

    /**
     * Constructor.
     *
     * @param taskId a task identifier, will be returned in the task finished listener.
     * @param root   folder to list
     */
    @UiThread
    protected FileListerAsyncTask(@NonNull final ProgressDialogFragment<ArrayList<FileDetails>> frag,
                                  final int taskId,
                                  @NonNull final File root) {
        mTaskId = taskId;
        mRoot = root;
        mFragment = frag;
        mFragment.setTask(mTaskId, this);
    }

    /** @return a FileFilter appropriate to the types of files being listed. */
    @NonNull
    protected abstract FileFilter getFilter();

    /** Turn an array of Files into an ArrayList of FileDetails. */
    @NonNull
    protected abstract ArrayList<FileDetails> processList(@Nullable final File[] files);

    @Override
    @Nullable
    @WorkerThread
    protected ArrayList<FileDetails> doInBackground(final Void... params) {
        // Get a file list
        File[] files = mRoot.listFiles(getFilter());
        // Filter/fill-in using the subclass
        ArrayList<FileDetails> dirs = processList(files);
        Collections.sort(dirs, FILE_DETAILS_COMPARATOR);
        return dirs;
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@Nullable final ArrayList<FileDetails> result) {
        if (result != null) {
            // Display it in UI thread.
            Activity activity = mFragment.getActivity();
            if (activity instanceof FileListerListener) {
                ((FileListerListener) activity).onGotFileList(mRoot, result);
            }
        }
        mFragment.taskFinished(mTaskId, mException == null, result);
    }

    /**
     * Interface for the creating activity to allow the resulting list to be returned.
     * We're not using the standard {@link ProgressDialogFragment.OnTaskFinishedListener}.
     */
    public interface FileListerListener {

        void onGotFileList(@NonNull File root,
                           @NonNull ArrayList<FileDetails> list);
    }
}
