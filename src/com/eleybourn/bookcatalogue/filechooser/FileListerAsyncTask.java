package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Partially implements a FragmentTask to build a list of files in the background.
 *
 * @author pjw
 */
public abstract class FileListerAsyncTask
        extends TaskWithProgress<ArrayList<FileDetails>> {

    @NonNull
    private final File mRoot;

    /**
     * Perform case-insensitive sorting using default locale.
     */
    private final Comparator<FileDetails> mComparator = new Comparator<FileDetails>() {

        public int compare(@NonNull final FileDetails o1,
                           @NonNull final FileDetails o2) {
            return o1.getFile().getName().toLowerCase()
                     .compareTo(o2.getFile().getName().toLowerCase());
        }
    };

    /**
     * Constructor.
     *
     * @param taskId  a task identifier, will be returned in the task finished listener.
     * @param context the caller context
     * @param root    folder to list
     */
    @UiThread
    protected FileListerAsyncTask(final int taskId,
                                  @NonNull final FragmentActivity context,
                                  @NonNull final File root) {
        super(taskId, UniqueId.TFT_FILE_LISTER, context, true,
              R.string.progress_msg_searching_directory);
        mRoot = root;
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
        Collections.sort(dirs, mComparator);
        return dirs;
    }

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
        super.onPostExecute(result);
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
