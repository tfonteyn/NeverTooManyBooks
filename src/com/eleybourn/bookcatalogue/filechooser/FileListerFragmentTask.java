package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;

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
public abstract class FileListerFragmentTask implements FragmentTask {
    @NonNull
    private final File mRoot;
    private final FileDetailsComparator mComparator = new FileDetailsComparator();

    private ArrayList<FileDetails> mDirs;

    /**
     * Constructor
     */
    FileListerFragmentTask(final @NonNull File root) {
        mRoot = root;
    }

    /** Return a FileFilter appropriate to the types of files being listed */
    @NonNull
    protected abstract FileFilter getFilter();

    /** Turn an array of Files into an ArrayList of FileDetails. */
    @NonNull
    protected abstract ArrayList<FileDetails> processList(final @NonNull Context context, final @Nullable File[] files);

    @Override
    public void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                    final @NonNull SimpleTaskContext taskContext) {
        // Get a file list
        File[] files = mRoot.listFiles(getFilter());
        // Filter/fill-in using the subclass
        mDirs = processList(fragment.requireContext(), files);
        // Sort it
        Collections.sort(mDirs, mComparator);
    }

    @Override
    public void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @Nullable Exception exception) {
        // Display it in UI thread.
        Activity listenerActivity = fragment.getActivity();
        if (listenerActivity instanceof FileListerListener) {
            ((FileListerListener) listenerActivity).onGotFileList(mRoot, mDirs);
        }
    }

    /**
     * Interface for the creating activity to allow the resulting list to be returned.
     *
     * @author pjw
     */
    public interface FileListerListener {
        void onGotFileList(final @NonNull File root, final @NonNull ArrayList<FileDetails> list);
    }

    /**
     * Perform case-insensitive sorting using default locale.
     */
    private static class FileDetailsComparator implements Comparator<FileDetails> {
        public int compare(final @NonNull FileDetails f1, final @NonNull FileDetails f2) {
            return f1.getFile().getName().toUpperCase().compareTo(f2.getFile().getName().toUpperCase());
        }
    }

}
