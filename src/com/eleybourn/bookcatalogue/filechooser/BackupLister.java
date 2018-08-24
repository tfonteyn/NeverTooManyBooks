package com.eleybourn.bookcatalogue.filechooser;

import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Object to provide a FileLister specific to archive files.
 * 
 * @author pjw
 */
public class BackupLister extends FileLister {
	/** Pattern to match an archive file spec */
	private static final Pattern mBackupFilePattern = Pattern.compile(".bcbk$", Pattern.CASE_INSENSITIVE);

	/**
	 * Constructor
	 */
	BackupLister(File root) {
		super(root);
	}

	/**
	 * Construct a file filter to select only directories and backup files.
	 */
	private final FileFilter mFilter = new FileFilter() {
		@Override
		public boolean accept(File f) {
			return (f.isDirectory() && f.canWrite()) || (f.isFile() && mBackupFilePattern.matcher(f.getName()).find());
		}
	};

	/**
	 * Get the file filter we constructed
	 */
	protected FileFilter getFilter() {
		return mFilter;
	}

	/**
	 * Process an array of Files into an ArrayList of BackupFileDetails
	 */
	protected ArrayList<FileDetails> processList(File[] files) {
		ArrayList<FileDetails> dirs = new ArrayList<>();

		if (files != null) {
			for (File f : files) {
				BackupFileDetails fd = new BackupFileDetails(f);
				dirs.add(fd);
				if (f.getName().toUpperCase().endsWith(".BCBK")) {
					BackupReader reader = null;
					try {
						reader = BackupManager.readBackup(f);
						fd.setInfo(reader.getInfo());
						reader.close();
					} catch (IOException e) {
						Logger.logError(e);
						if (reader != null)
							try { reader.close(); } catch (IOException ignore) {}
					}
				}
			}
		}
		return dirs;
	}

}
