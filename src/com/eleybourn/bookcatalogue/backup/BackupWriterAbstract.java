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

import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupWriterAbstract implements BackupWriter {
    private final CatalogueDBAdapter mDb;

    /**
     * Constructor
     */
    protected BackupWriterAbstract() {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mDb.open();
    }

    /**
     * Do a full backup, sending progress to the listener
     */
    @Override
    public void backup(@NonNull final BackupWriterListener listener, final int backupFlags, @Nullable Date since) throws IOException {
        try {
            // Estimate the total steps
            int estTotal = 1;
            // First, see how many books in total
            final int maxBooks = mDb.countBooks();

            int coverCount;
            if ((backupFlags & Exporter.EXPORT_COVERS) != 0) {
                coverCount = writeCovers(listener, backupFlags, since, true);
            } else {
                coverCount = 0;
            }

            // If we are doing books, add them
            if ((backupFlags & Exporter.EXPORT_DETAILS) != 0) {
                estTotal += maxBooks;
            }

            // If we are doing covers, add them
            if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_COVERS) != 0) {
                estTotal += coverCount;
            }

            listener.setMax(estTotal);

            // Generate the book list first, so we know how many there are.
            final File temp = generateBooks(listener, backupFlags, since, coverCount);

            listener.setMax(coverCount + listener.getTotalBooks() + 1);

            // Process each component of the Archive, unless we are cancelled, as in Nikita
            if (!listener.isCancelled()) {
                writeInfo(listener, listener.getTotalBooks(), coverCount);
            }
            if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_DETAILS) != 0) {
                writeBooks(temp);
            }
            if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_COVERS) != 0) {
                writeCovers(listener, backupFlags, since, false);
            }
            if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_PREFERENCES) != 0) {
                writePreferences(listener);
            }
            if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_STYLES) != 0) {
                writeStyles(listener);
            }
        } finally {
            try {
                close();
            } catch (Exception e) {
                Logger.logError(e, "Failed to close archive");
            }
        }

        if (BuildConfig.DEBUG) {
            System.out.println("Closed writer");
        }
    }

    /**
     * Generate a bundle containing the INFO block, and send it to the archive
     */
    private void writeInfo(@NonNull final BackupWriterListener listener, final int bookCount, final int coverCount) throws IOException {
        final BackupInfo info = BackupInfo.createInfo(getContainer(), BookCatalogueApp.getAppContext(), bookCount, coverCount);
        putInfo(info);
        listener.step(null, 1);
    }

    /**
     * Generate a temporary file containing a books export, and send it to the archive
     * <p>
     * NOTE: This implementation is built around the TAR format; it is not a fixed design.
     * We could for example pass an Exporter to the writer and leave it to decide if a
     * temp file or a stream were appropriate. Sadly, tar archives need to know size before
     * the header can be written.
     * <p>
     * It IS convenient to do it here because we can capture the progress, but we could also
     * have writer.putBooks(exporter, listener) as the method.
     */
    private File generateBooks(@NonNull final BackupWriterListener listener,
                               final int backupFlags,
                               @Nullable final Date since,
                               final int numCovers) throws IOException {
        // This is an estimate only; we actually don't know how many covers
        // there are in the backup.
        listener.setMax((mDb.countBooks() * 2 + 1));

        final Exporter.ExportListener exportListener = new Exporter.ExportListener() {
            private int mLastPos = 0;

            @Override
            public void onProgress(@NonNull String message, final int position) {
                // The progress is sent periodically and has jumps, so we calculate deltas
                listener.step(message, position - mLastPos);
                mLastPos = position;
            }

            @Override
            public boolean isCancelled() {
                return listener.isCancelled();
            }

            @Override
            public void setMax(final int max) {
                // Save the book count for later
                listener.setTotalBooks(max);
                // Update the progress bar to a more reasonable value
                listener.setMax(numCovers + max + 1);
            }
        };

        // Get a temp file and mark for delete
        if (BuildConfig.DEBUG) {
            System.out.println("Getting books");
        }
        final File temp = File.createTempFile("bookcat", ".tmp");
        temp.deleteOnExit();
        FileOutputStream output = null;
        try {
            CsvExporter exporter = new CsvExporter();
            output = new FileOutputStream(temp);
            exporter.export(output, exportListener, backupFlags, since);
            output.close();
        } finally {
            if (output != null && output.getChannel().isOpen()) {
                output.close();
            }
        }

        return temp;
    }

    /**
     * @param exportFile the file containing the exported books in CSV format
     */
    private void writeBooks(@NonNull final File exportFile) throws IOException {
        try {
            if (BuildConfig.DEBUG) {
                System.out.println("Writing Books");
            }
            putBooks(exportFile);
        } finally {
            StorageUtils.deleteFile(exportFile);
        }
    }

//	/**
//	 * Generate a temporary file containing a books export, and send it to the archive
//	 * 
//	 * NOTE: This implementation is built around the TAR format; it is not a fixed design.
//	 * We could for example pass an Exporter to the writer and leave it to decide if a 
//	 * temp file or a stream were appropriate. Sadly, tar archives need to know size before
//	 * the header can be written.
//	 * 
//	 * It IS convenient to do it here because we can capture the progress, but we could also
//	 * have writer.putBooks(exporter, listener) as the method.
//	 * 
//	 * @param writer
//	 * 
//	 * @throws IOException
//	 */
//	private void writeBooks(final BackupWriterListener listener, final int backupFlags, final Date since) throws IOException {
//		// This is an estimate only; we actually don't know how many covers
//		// there are in the backup.
//		listener.setMax((int) (mDb.countBooks() * 2 + 1));
//
//		Exporter.ExportListener exportListener = new Exporter.ExportListener() {
//			private int mLastPos = 0;
//			@Override
//			public void onProgress(String message, int position) {
//				// The progress is sent periodically and has jumps, so we calculate deltas
//				listener.step(message, position - mLastPos);
//				mLastPos = position;
//			}
//			@Override
//			public boolean isCancelled() {
//				return listener.isCancelled();
//			}
//			@Override
//			public void setMax(int max) {
//				// Ignore
//			}
//		};
//
//		// Get a temp file and mark for delete
//		System.out.println("Getting books");
//		File temp = File.createTempFile("bookcat", ".tmp");
//		temp.deleteOnExit();
//		FileOutputStream output = null;
//		try {
//			CsvExporter exporter = new CsvExporter();
//			output = new FileOutputStream(temp);
//			exporter.export(output, exportListener, backupFlags, since);
//			output.close();
//			System.out.println("Writing Books");
//			putBooks(temp);
//		} finally {
//			if (output != null && output.getChannel().isOpen())
//				output.close();
//			 StorageUtils.deleteFile(temp);
//		}
//	}

    /**
     * Write each cover file corresponding to a book to the archive
     */
    private int writeCovers(@NonNull final BackupWriterListener listener, final int backupFlags,
                            @Nullable final Date since, boolean dryRun) throws IOException {
        long sinceTime = 0;
        if (since != null && (backupFlags & Exporter.EXPORT_SINCE) != 0) {
            try {
                sinceTime = since.getTime();
            } catch (Exception e) {
                // Just ignore; backup everything
                Logger.logError(e);
            }
        }

        if (BuildConfig.DEBUG) {
            System.out.println("Writing Images");
        }

        int ok = 0;
        int missing = 0;
        int skipped = 0;
        String fmt_noskip = BookCatalogueApp.getResourceString(R.string.covers_progress);
        String fmt_skip = BookCatalogueApp.getResourceString(R.string.covers_progress_incr);

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.toString());
            while (cursor.moveToNext() && !listener.isCancelled()) {
                File cover = StorageUtils.getCoverFile(cursor.getString(uuidCol));
                if (cover.exists()) {
                    if (cover.exists()
                            && (since == null || sinceTime < cover.lastModified())) {
                        if (!dryRun) {
                            putCoverFile(cover);
                        }
                        ok++;
                    } else {
                        skipped++;
                    }
                } else {
                    missing++;
                }
                if (!dryRun) {
                    String message;
                    if (skipped == 0) {
                        message = String.format(fmt_noskip, ok, missing);
                    } else {
                        message = String.format(fmt_skip, ok, missing, skipped);
                    }
                    listener.step(message, 1);
                }
            }
        }
        if (!dryRun) {
            System.out.println("Wrote " + ok + " Images, " + missing + " missing, and " + skipped + " skipped");
        }

        return ok;
    }

    /**
     * Get the preferences and save them
     */
    private void writePreferences(@NonNull final BackupWriterListener listener) throws IOException {
        SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();
        putPreferences(prefs);
        listener.step(null, 1);
    }

    /**
     * Save all USER styles
     */
    private void writeStyles(@NonNull final BackupWriterListener listener) throws IOException {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        for (BooklistStyle style : styles) {
            if (style.isUserDefined()) {
                putBooklistStyle(style);
            }
        }
        listener.step(null, 1);
    }

    /**
     * Cleanup
     */
    @Override
    public void close() throws IOException {
        mDb.close();
    }
}
