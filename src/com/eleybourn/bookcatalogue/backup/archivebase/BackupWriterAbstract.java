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

import android.database.Cursor;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlExporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlUtils;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupWriterAbstract
        implements BackupWriter {

    @NonNull
    private final DBA mDb;

    private ExportSettings mSettings;

    private BackupWriter.BackupWriterListener mProgressListener;

    protected BackupWriterAbstract() {
        mDb = new DBA(BookCatalogueApp.getAppContext());
    }

    /**
     * Do a full backup.
     *
     * @param settings what to backup
     * @param listener to send progress updates to
     */
    @Override
    public void backup(@NonNull final ExportSettings settings,
                       @NonNull final BackupWriter.BackupWriterListener listener)
            throws IOException {
        mSettings = settings;
        mProgressListener = listener;

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = ExportSettings.NOTHING;

        File tempBookCsvFile = null;

        BackupInfo.InfoUserValues infoValues = new BackupInfo.InfoUserValues();
        infoValues.hasStyles = (mSettings.what & ExportSettings.BOOK_LIST_STYLES) != 0;
        infoValues.hasPrefs = (mSettings.what & ExportSettings.PREFERENCES) != 0;


        try {
            // If we are doing books, generate the CSV file, and set the number
            if ((mSettings.what & ExportSettings.BOOK_CSV) != 0) {
                // Get a temp file and set for delete
                tempBookCsvFile = File.createTempFile("tmp_books_csv_", ".tmp");
                tempBookCsvFile.deleteOnExit();
                try (FileOutputStream output = new FileOutputStream(tempBookCsvFile)) {
                    CsvExporter exporter = new CsvExporter(mSettings);
                    infoValues.bookCount = exporter.doBooks(output, new ForwardingListener());
                }
            }

            // If we are doing covers, get the exact number by counting them
            if (!mProgressListener.isCancelled() && (mSettings.what & ExportSettings.COVERS) != 0) {
                // just count the covers, no exporting as yet
                if ((mSettings.what & ExportSettings.COVERS) != 0) {
                    // we don't write here, only pretend, so we can count the covers.
                    infoValues.coverCount = doCovers(true);
                }
            }

            // we now have a known number of books; add the covers and we've more or less have an
            // exact number of steps. Added arbitrary 5 for the other entities we might do
            mProgressListener.setMax(infoValues.coverCount + infoValues.bookCount + 5);

            // Process each component of the Archive, unless we are cancelled, as in Nikita
            if (!mProgressListener.isCancelled()) {
                doInfo(infoValues);
            }

            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportSettings.XML_TABLES) != 0) {
                doXmlTables();
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportSettings.BOOK_CSV) != 0) {
                try {
                    //noinspection ConstantConditions
                    putBooks(tempBookCsvFile);
                } finally {
                    StorageUtils.deleteFile(tempBookCsvFile);
                }
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportSettings.COVERS) != 0) {
                doCovers(false);
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportSettings.BOOK_LIST_STYLES) != 0) {
                doStyles();
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportSettings.PREFERENCES) != 0) {
                doPreferences();
            }
        } finally {
            mSettings.what = entitiesWritten;
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "backup","exported covers#=" + infoValues.coverCount);
            }
            try {
                close();
            } catch (IOException e) {
                Logger.error(e, "Failed to close writer");
            }
        }
    }

    private void doInfo(@NonNull final BackupInfo.InfoUserValues infoValues)
            throws IOException {
        mProgressListener.onProgressStep(null, 1);

        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(data, StandardCharsets.UTF_8), XmlUtils.BUFFER_SIZE);

        try (XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doBackupInfoBlock(out, new ForwardingListener(),
                                          BackupInfo.newInstance(getContainer(), infoValues));
        }

        out.close();
        putInfo(data.toByteArray());
    }

    private void doXmlTables()
            throws IOException {
        // Get a temp file and set for delete
        final File tempXmlBackupFile = File.createTempFile("tmp_xml_", ".tmp");
        tempXmlBackupFile.deleteOnExit();

        try (FileOutputStream output = new FileOutputStream(tempXmlBackupFile)) {
            XmlExporter exporter = new XmlExporter(mSettings);
            exporter.doExport(output, new ForwardingListener());
            putXmlData(tempXmlBackupFile);
        } finally {
            StorageUtils.deleteFile(tempXmlBackupFile);
        }
    }

    private void doPreferences()
            throws IOException {
        // Turn the preferences into an XML file in a byte array
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(data, StandardCharsets.UTF_8), XmlUtils.BUFFER_SIZE);

        try (XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doPreferences(out, new ForwardingListener());
        }
        out.close();
        putPreferences(data.toByteArray());
        mProgressListener.onProgressStep(null, 1);
    }

    private void doStyles()
            throws IOException {
        Map<Long, BooklistStyle> bsMap = BooklistStyles.getUserStyles(mDb);
        if (!bsMap.isEmpty()) {
            // Turn the styles into an XML file in a byte array
            final ByteArrayOutputStream data = new ByteArrayOutputStream();
            final BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(data, StandardCharsets.UTF_8), XmlUtils.BUFFER_SIZE);

            try (XmlExporter xmlExporter = new XmlExporter()) {
                xmlExporter.doStyles(out, new ForwardingListener());
            }
            out.close();
            putBooklistStyles(data.toByteArray());
            mProgressListener.onProgressStep(null, 1);
        }
    }


    /**
     * Write each cover file corresponding to a book to the archive.
     *
     * @throws IOException on failure
     */
    private int doCovers(final boolean dryRun)
            throws IOException {
        long sinceTime = 0;
        if (mSettings.dateFrom != null && (mSettings.what & ExportSettings.EXPORT_SINCE) != 0) {
            sinceTime = mSettings.dateFrom.getTime();
        }

        int ok = 0;
        int missing = 0;
        int skipped = 0;
        String fmtNoSkip = BookCatalogueApp.getResString(R.string.progress_msg_covers);
        String fmtSkip = BookCatalogueApp.getResString(R.string.progress_msg_covers_skip);

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.name);
            while (cursor.moveToNext() && !mProgressListener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                File cover = StorageUtils.getCoverFile(uuid);
                if (cover.exists()) {
                    if (cover.exists()
                            && (mSettings.dateFrom == null || sinceTime < cover.lastModified())) {
                        if (!dryRun) {
                            putFile(cover.getName(), cover);
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
                        message = String.format(fmtNoSkip, ok, missing);
                    } else {
                        message = String.format(fmtSkip, ok, missing, skipped);
                    }
                    mProgressListener.onProgressStep(message, 1);
                }
            }
        }
        if (!dryRun) {
            Logger.info(this, "doCovers"," Wrote " + ok + " Images, " + missing + " missing,"
                    + " and " + skipped + " skipped");
        }

        return ok;
    }

    /**
     * Actual writer should override and close their output.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        mDb.close();
    }

    /**
     * Listener for the '{@link com.eleybourn.bookcatalogue.backup.Exporter#doExport} method
     * that just passes on the progress to our own listener.
     * <p>
     * It basically translates between 'delta' and 'absolute' positions for the progress counter
     */
    private class ForwardingListener
            implements Exporter.ExportListener {

        private int mLastPos;

        @Override
        public void setMax(final int max) {
            mProgressListener.setMax(max);
        }

        @Override
        public void onProgress(@NonNull final String message,
                               final int position) {
            // The progress is sent periodically and has jumps, so we calculate deltas
            mProgressListener.onProgressStep(message, position - mLastPos);
            mLastPos = position;
        }

        @Override
        public boolean isCancelled() {
            return mProgressListener.isCancelled();
        }
    }
}
