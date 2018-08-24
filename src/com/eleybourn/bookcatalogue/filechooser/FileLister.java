package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;

import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;

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
	private ArrayList<FileDetails> dirs;
	private final File mRoot;

	/**
	 * Interface for the creating activity to allow the resulting list to be returned.
	 * 
	 * @author pjw
	 */
	public interface FileListerListener {
		void onGotFileList(File root, ArrayList<FileDetails> list);
	}

	/**
	 * Constructor
	 */
	FileLister(File root) {
		mRoot = root;
	}

	/** Return a FileFilter appropriate to the types of files being listed */
	protected abstract FileFilter getFilter();
	/** Turn an array of Files into an ArrayList of FileDetails. */
	protected abstract ArrayList<FileDetails> processList(File[] files);

	@Override
	public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
		// Get a file list
		File[] files = mRoot.listFiles(getFilter());
		// Filter/fill-in using the subclass
		dirs = processList(files);
		// Sort it
		Collections.sort(dirs, mComparator);
	}

	@Override
	public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
		// Display it in UI thread.
		Activity a = fragment.getActivity();
		if (a != null && a instanceof FileListerListener) {
			((FileListerListener)a).onGotFileList(mRoot, dirs);
		}
	}

	/**
	 * Perform case-insensitive sorting using default locale.
	 */
	private static class FileDetailsComparator implements Comparator<FileDetails> {
		public int compare(FileDetails f1, FileDetails f2) {
			return f1.getFile().getName().toUpperCase().compareTo(f2.getFile().getName().toUpperCase());
		}
	}

	private final FileDetailsComparator mComparator = new FileDetailsComparator();

}
