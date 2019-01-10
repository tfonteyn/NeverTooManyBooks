package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;

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
public abstract class FileListerFragmentTask
        extends SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract {

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

    private ArrayList<FileDetails> mDirs;

    /**
     * Constructor.
     */
    protected FileListerFragmentTask(@NonNull final File root) {
        mRoot = root;
    }

    /** @return a FileFilter appropriate to the types of files being listed. */
    @NonNull
    protected abstract FileFilter getFilter();

    /** Turn an array of Files into an ArrayList of FileDetails. */
    @NonNull
    protected abstract ArrayList<FileDetails> processList(@Nullable final File[] files);

    @Override
    public void run(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                    @NonNull final SimpleTaskContext taskContext) {
        // Get a file list
        File[] files = mRoot.listFiles(getFilter());
        // Filter/fill-in using the subclass
        mDirs = processList(files);
        // Sort it
        Collections.sort(mDirs, mComparator);
    }

    @Override
    public void onFinish(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                         @Nullable final Exception e) {
        // Display it in UI thread.
        Activity listenerActivity = fragment.getActivity();
        if (listenerActivity instanceof FileListerListener) {
            ((FileListerListener) listenerActivity).onGotFileList(mRoot, mDirs);
        }
    }

    /**
     * Interface for the creating activity to allow the resulting list to be returned.
     */
    public interface FileListerListener {

        void onGotFileList(@NonNull final File root,
                           @NonNull final ArrayList<FileDetails> list);
    }

}
