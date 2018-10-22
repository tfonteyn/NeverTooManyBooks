package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTask;

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
public abstract class FileLister implements FragmentTask {
    @NonNull
    private final File mRoot;
    private final FileDetailsComparator mComparator = new FileDetailsComparator();

    private ArrayList<FileDetails> mDirs;

    /**
     * Constructor
     */
    FileLister(@NonNull final File root) {
        mRoot = root;
    }

    /** Return a FileFilter appropriate to the types of files being listed */
    @NonNull
    protected abstract FileFilter getFilter();

    /** Turn an array of Files into an ArrayList of FileDetails. */
    @NonNull
    protected abstract ArrayList<FileDetails> processList(@Nullable final File[] files);

    @Override
    public void run(@NonNull final SimpleTaskQueueProgressFragment fragment, @NonNull final SimpleTaskContext taskContext) {
        // Get a file list
        File[] files = mRoot.listFiles(getFilter());
        // Filter/fill-in using the subclass
        mDirs = processList(files);
        // Sort it
        Collections.sort(mDirs, mComparator);
    }

    @Override
    public void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment, @Nullable final Exception exception) {
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
        void onGotFileList(@NonNull final File root, @NonNull final ArrayList<FileDetails> list);
    }

    /**
     * Perform case-insensitive sorting using default locale.
     */
    private static class FileDetailsComparator implements Comparator<FileDetails> {
        public int compare(@NonNull final FileDetails f1, @NonNull final FileDetails f2) {
            return f1.getFile().getName().toUpperCase().compareTo(f2.getFile().getName().toUpperCase());
        }
    }

}
