/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Class for public static methods relating to backup/restore
 * 
 * @author pjw
 */
public class BackupManager {

	/**
	 * Create a BackupReader for the specified file.
	 * 
	 * @param file	File to read
	 * 
	 * @return	a new reader
	 * 
	 * @throws IOException (inaccessible, invalid other other errors)
	 */
	public static BackupReader readBackup(File file) throws IOException {
		if (!file.exists())
			throw new java.io.FileNotFoundException("Attempt to open non-existent backup file");
		
		// We only support one backup format; so we use that. In future we would need to 
		// explore the file to determine which format to use
		TarBackupContainer bkp = new TarBackupContainer(file);
		// Each format should provide a validator of some kind
		if (!bkp.isValid())
			throw new IOException("Not a valid backup file");

		return bkp.newReader();
	}

	/**
	 * Esnure the file name extension is what we want
	 */
	private static File cleanupFile(File requestedFile) {
		if (!requestedFile.getName().toUpperCase().endsWith(".BCBK")) {
			return new File(requestedFile.getAbsoluteFile() + ".bcbk");
		} else {
			return requestedFile;
		}

	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static File backupCatalogue(final BookCatalogueActivity context, final File requestedFile, int taskId, final int backupFlags, final Date since) {
		final int flags = backupFlags & Exporter.EXPORT_MASK;
		if (flags == 0)
			throw new RuntimeException("Backup flags must be specified");
		//if (flags == (Exporter.EXPORT_ALL | Exporter.EXPORT_NEW_OR_UPDATED) )
		//	throw new RuntimeException("Illegal backup flag combination: ALL and NEW_OR_UPADTED");
		
		final File resultingFile = cleanupFile(requestedFile);
		final File tempFile = new File(resultingFile.getAbsolutePath() + ".tmp");

		FragmentTask task = new FragmentTaskAbstract() {
			private boolean mBackupOk = false;
			private final String mBackupDate = DateUtils.toSqlDateTime(new Date());

			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				BackupWriter wrt = null;

				try {
					if (BuildConfig.DEBUG) {
						System.out.println("Starting " + tempFile.getAbsolutePath());
					}
					TarBackupContainer bkp = new TarBackupContainer(tempFile);
					wrt = bkp.newWriter();

					wrt.backup(new BackupWriterListener() {
						private int mTotalBooks = 0;

						@Override
						public void setMax(int max) {
							fragment.setMax(max);
						}

						@Override
						public void step(String message, int delta) {
							fragment.step(message, delta);
						}

						@Override
						public boolean isCancelled() {
							return fragment.isCancelled();
						}

						@Override
						public void setTotalBooks(int books) {
							mTotalBooks = books;
						}

						@Override
						public int getTotalBooks() {
							return mTotalBooks;
						}}, backupFlags, since);

					if (fragment.isCancelled()) {
						if (BuildConfig.DEBUG) {
							System.out.println("Cancelled " + resultingFile.getAbsolutePath());
						}
						if (tempFile.exists())
							tempFile.delete();
					} else {
						if (resultingFile.exists())
							resultingFile.delete();
						tempFile.renameTo(resultingFile);
						mBackupOk = true;
						if (BuildConfig.DEBUG) {
							System.out.println("Finished " + resultingFile.getAbsolutePath() + ", size = " + resultingFile.length());
						}
					}
				} catch (Exception e) {
					Logger.logError(e);
					if (tempFile.exists())
						try {
							tempFile.delete();
						} catch (Exception e2) {
							// Ignore
						}
					throw new RuntimeException("Error during backup", e);
				} finally {
					if (wrt != null) {
						try {
							wrt.close();
						} catch (Exception e2) {
							// Ignore
						}
					}
				}
			}

			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				super.onFinish(fragment, exception);
				if (exception != null) {
					if (tempFile.exists())
						tempFile.delete();
				}
				fragment.setSuccess(mBackupOk);
				if (mBackupOk) {
					BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
					if ( (backupFlags == Exporter.EXPORT_ALL)) {
						prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_DATE, mBackupDate);
					}
					prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_FILE, resultingFile.getAbsolutePath());
				}
			}

		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.backing_up_ellipsis, task, false, taskId);
		frag.setNumberFormat(null);
		return resultingFile;
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static void restoreCatalogue(final BookCatalogueActivity context, final File inputFile, int taskId, final int importFlags) {

		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				try {
					if (BuildConfig.DEBUG) {
						System.out.println("Starting " + inputFile.getAbsolutePath());
					}
					BackupReader rdr = BackupManager.readBackup(inputFile);
					rdr.restore(new BackupReaderListener() {
						@Override
						public void setMax(int max) {
							fragment.setMax(max);
						}

						@Override
						public void step(String message, int delta) {
							fragment.step(message, delta);
						}

						@Override
						public boolean isCancelled() {
							return fragment.isCancelled();
						}}, importFlags);
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Error during restore", e);
				}
				if (BuildConfig.DEBUG) {
					System.out.println("Finished " + inputFile.getAbsolutePath() + ", size = " + inputFile.length());
				}
			}
		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context,
				R.string.importing_ellipsis, task, false, taskId);
		frag.setNumberFormat(null);
	}
}
