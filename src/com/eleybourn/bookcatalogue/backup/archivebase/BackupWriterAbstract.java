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
package com.eleybourn.bookcatalogue.backup.archivebase;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.Exporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlExporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupWriterAbstract implements BackupWriter {
    @NonNull
    private final CatalogueDBAdapter mDb;

    private final String XML_BACKUP_FILE = "bc.xml";
    /**
     * Constructor
     */
    protected BackupWriterAbstract() {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
    }

    /**
     * Do a full backup, sending progress to the listener
     */
    @Override
    public void backup(final @NonNull ExportSettings settings,
                       final @NonNull BackupWriterListener listener) throws IOException {

        // keep track of what we wrote to the archive
        int entitiesWritten = ExportSettings.NOTHING;

        // progress counters
        int coverCount = 0;
        int estimatedSteps = 1;

        try {
            // If we are doing books, add the number
            if ((settings.what & ExportSettings.BOOK_CSV) != 0) {
                estimatedSteps += mDb.countBooks();
            }

            // If we are doing covers, add the number
            if (!listener.isCancelled() && (settings.what & ExportSettings.COVERS) != 0) {
                // just count the covers, no exporting as yet
                if ((settings.what & ExportSettings.COVERS) != 0) {
                    coverCount = writeCovers(listener, settings, true);
                    estimatedSteps += coverCount;
                }
            }
            listener.setMax(estimatedSteps);

            // Generate the book list first, so we know how many there are exactly.
            final File tempBookCsvFile = generateBooks(listener, settings, coverCount);

            final File tempXmlBackupFile = generateXmlBackupFile(listener,settings, coverCount);

            // we now have a known number of books
            listener.setMax(coverCount + listener.getTotalBooks() + 1);

            // Process each component of the Archive, unless we are cancelled, as in Nikita
            if (!listener.isCancelled()) {
                writeInfo(listener, listener.getTotalBooks(), coverCount);
            }
            if (!listener.isCancelled() && (settings.what & ExportSettings.XML_TABLES) != 0) {
                writeGenericFile(XML_BACKUP_FILE,tempXmlBackupFile);
            }
            if (!listener.isCancelled() && (settings.what & ExportSettings.BOOK_CSV) != 0) {
                 writeBooks(tempBookCsvFile);
            }
            if (!listener.isCancelled() && (settings.what & ExportSettings.COVERS) != 0) {
                writeCovers(listener, settings, false);
            }
            if (!listener.isCancelled() && (settings.what & ExportSettings.PREFERENCES) != 0) {
                writePreferences(listener);
            }
            if (!listener.isCancelled() && (settings.what & ExportSettings.BOOK_LIST_STYLES) != 0) {
                writeStyles(listener);
            }
        } finally {
            settings.what = entitiesWritten;
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "exported covers#=" + coverCount);
            }
            try {
                close();
            } catch (Exception e) {
                Logger.error(e, "Failed to close writer");
            }
        }
    }

    /**
     * Generate a bundle containing the INFO block, and send it to the archive
     */
    private void writeInfo(final @NonNull BackupWriterListener listener,
                           final int bookCount,
                           final int coverCount) throws IOException {
        final BackupInfo info = BackupInfo.createInfo(getContainer(), bookCount, coverCount);
        putInfo(info);
        listener.step(null, 1);
    }

    /**
     * Write a generic file to the archive
     *
     * @param name of the entry in the archive
     * @param file actual file to store in the archive
     */
    private void writeGenericFile(final @NonNull String name,
                                  final @NonNull File file) throws IOException {
        try {
            putGenericFile(name, file);
        } finally {
            StorageUtils.deleteFile(file);
        }
    }

    private File generateXmlBackupFile(final BackupWriterListener listener,
                                       final ExportSettings settings,
                                       final int numCovers) throws IOException {
        // Get a temp file and set for delete
        final File temp = File.createTempFile("bc-xml-backup", ".tmp");
        temp.deleteOnExit();

        final Exporter.ExportListener exportListener = new Exporter.ExportListener() {

            @Override
            public void setMax(final int max) {
                // Update the progress bar to a more realistic value
                listener.setMax(numCovers + max + 1);
            }

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
        };

        try (FileOutputStream output = new FileOutputStream(temp)) {
            XmlExporter exporter = new XmlExporter(settings);
            exporter.doBooks(output, exportListener);
        }

        return temp;

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
    @NonNull
    private File generateBooks(final @NonNull BackupWriterListener listener,
                               final @NonNull ExportSettings settings,
                               final int numCovers) throws IOException {
        // This is an estimate only; we actually don't know how many covers there are in the backup.
        listener.setMax((mDb.countBooks() * 2 + 1));

        // Listener for the 'doBooks' function that just passes on the progress to our own listener
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
                // Update the progress bar to a more realistic value
                listener.setMax(numCovers + max + 1);
            }
        };

        // Get a temp file and set for delete
        final File temp = File.createTempFile("bc", ".tmp");
        temp.deleteOnExit();
        try (FileOutputStream output = new FileOutputStream(temp)) {
            CsvExporter exporter = new CsvExporter(settings);
            exporter.doBooks(output, exportListener);
        }

        return temp;
    }

    /**
     * @param exportFile the file containing the exported books in CSV format
     */
    private void writeBooks(final @NonNull File exportFile) throws IOException {
        try {
            putBooks(exportFile);
        } finally {
            StorageUtils.deleteFile(exportFile);
        }
    }

    /**
     * Write each cover file corresponding to a book to the archive
     */
    private int writeCovers(final @NonNull BackupWriterListener listener,
                            final @NonNull ExportSettings settings,
                            final boolean dryRun) throws IOException {
        long sinceTime = 0;
        if (settings.dateFrom != null && (settings.what & ExportSettings.EXPORT_SINCE) != 0) {
            sinceTime = settings.dateFrom.getTime();
        }

        int ok = 0;
        int missing = 0;
        int skipped = 0;
        String fmt_no_skip = BookCatalogueApp.getResourceString(R.string.progress_msg_covers);
        String fmt_skip = BookCatalogueApp.getResourceString(R.string.progress_msg_covers_skip);

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.toString());
            while (cursor.moveToNext() && !listener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                File cover = StorageUtils.getCoverFile(uuid);
                if (cover.exists()) {
                    if (cover.exists()
                            && (settings.dateFrom == null || sinceTime < cover.lastModified())) {
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
                        message = String.format(fmt_no_skip, ok, missing);
                    } else {
                        message = String.format(fmt_skip, ok, missing, skipped);
                    }
                    listener.step(message, 1);
                }
            }
        }
        if (!dryRun) {
            Logger.info(this, " Wrote " + ok + " Images, " + missing + " missing, and " + skipped + " skipped");
        }

        return ok;
    }

    /**
     * Get the preferences and save them
     */
    private void writePreferences(final @NonNull BackupWriterListener listener) throws IOException {
        SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();
        putPreferences(prefs);
        listener.step(null, 1);
    }

    /**
     * Save all USER styles
     */
    private void writeStyles(final @NonNull BackupWriterListener listener) throws IOException {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        for (BooklistStyle style : styles) {
            if (style.isUserDefined()) {
                putBooklistStyle(style);
            }
        }
        listener.step(null, 1);
    }

    /**
     * Actual writer should override and close their output
     */
    @Override
    @CallSuper
    public void close() throws IOException {
        mDb.close();
    }
}
