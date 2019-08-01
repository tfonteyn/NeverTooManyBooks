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

import android.content.res.Resources;
import android.database.Cursor;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlExporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupWriterAbstract
        implements BackupWriter {

    private static final int BUFFER_SIZE = 32768;

    /** Database access. */
    @NonNull
    private final DAO mDb;

    /** progress message. */
    private final String mProgress_msg_covers;
    /** progress message. */
    private final String mProgress_msg_covers_skip;

    private ExportOptions mSettings;
    private ProgressListener mProgressListener;

    /**
     * Constructor.
     */
    protected BackupWriterAbstract() {
        mDb = new DAO();

        Resources resources = LocaleUtils.getLocalizedResources();
        mProgress_msg_covers = resources.getString(R.string.progress_msg_covers_handled_missing);
        mProgress_msg_covers_skip = resources.getString(
                R.string.progress_msg_covers_handled_missing_skipped);
    }

    /**
     * Do a full backup.
     *
     * @param settings what to backup
     * @param listener to send progress updates to
     */
    @Override
    @WorkerThread
    public void backup(@NonNull final ExportOptions settings,
                       @NonNull final ProgressListener listener)
            throws IOException {
        mSettings = settings;
        mProgressListener = listener;

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = ExportOptions.NOTHING;

        File tempBookCsvFile = null;

        BackupInfo.InfoUserValues infoValues = new BackupInfo.InfoUserValues();
        infoValues.hasStyles = (mSettings.what & ExportOptions.BOOK_LIST_STYLES) != 0;
        infoValues.hasPrefs = (mSettings.what & ExportOptions.PREFERENCES) != 0;

        boolean includeCovers = (mSettings.what & ExportOptions.COVERS) != 0;
        try {
            // If we are doing covers, get the exact number by counting them
            if (!mProgressListener.isCancelled() && includeCovers) {
                // just count the covers, no exporting as yet
                if ((mSettings.what & ExportOptions.COVERS) != 0) {
                    // we don't write here, only pretend, so we can count the covers.
                    infoValues.coverCount = doCovers(true);
                }
            }

            // If we are doing books, generate the CSV file, and set the number
            if ((mSettings.what & ExportOptions.BOOK_CSV) != 0) {
                // Get a temp file and set for delete
                tempBookCsvFile = File.createTempFile("tmp_books_csv_", ".tmp");
                tempBookCsvFile.deleteOnExit();

                Exporter mExporter = new CsvExporter(App.getAppContext(), mSettings);
                try (OutputStream output = new FileOutputStream(tempBookCsvFile)) {
                    //FIXME: we know the # of covers... should pass it.
                    infoValues.bookCount = mExporter.doBooks(output, mProgressListener, includeCovers);
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
                    && (mSettings.what & ExportOptions.XML_TABLES) != 0) {
                doXmlTables();
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportOptions.BOOK_CSV) != 0) {
                try {
                    //noinspection ConstantConditions
                    putBooks(tempBookCsvFile);
                } finally {
                    StorageUtils.deleteFile(tempBookCsvFile);
                }
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportOptions.COVERS) != 0) {
                doCovers(false);
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportOptions.BOOK_LIST_STYLES) != 0) {
                doStyles();
            }
            if (!mProgressListener.isCancelled()
                    && (mSettings.what & ExportOptions.PREFERENCES) != 0) {
                doPreferences();
            }
        } finally {
            mSettings.what = entitiesWritten;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Logger.debug(this, "backup", "exported covers#=" + infoValues.coverCount);
            }
            try {
                close();
            } catch (@NonNull final IOException e) {
                Logger.error(this, e, "Failed to close writer");
            }
        }
    }

    private void doInfo(@NonNull final BackupInfo.InfoUserValues infoValues)
            throws IOException {
        mProgressListener.onProgressStep(1, null);

        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(data, StandardCharsets.UTF_8), BUFFER_SIZE);

        try (XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doBackupInfoBlock(out, mProgressListener,
                                          BackupInfo.newInstance(getContainer(), infoValues));
        }

        out.close();
        putInfo(data.toByteArray());
    }

    /**
     *
     * @throws IOException on failure
     */
    private void doXmlTables()
            throws IOException {
        // Get a temp file and set for delete
        final File tempXmlBackupFile = File.createTempFile("tmp_xml_", ".tmp");
        tempXmlBackupFile.deleteOnExit();

        try (OutputStream output = new FileOutputStream(tempXmlBackupFile)) {
            XmlExporter exporter = new XmlExporter(mSettings);
            exporter.doAll(output, mProgressListener);
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
                new OutputStreamWriter(data, StandardCharsets.UTF_8), BUFFER_SIZE);

        try (XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doPreferences(out, mProgressListener);
        }
        out.close();
        putPreferences(data.toByteArray());
        mProgressListener.onProgressStep(1, null);
    }

    private void doStyles()
            throws IOException {
        Map<String, BooklistStyle> bsMap = BooklistStyles.getUserStyles(mDb);
        if (!bsMap.isEmpty()) {
            // Turn the styles into an XML file in a byte array
            final ByteArrayOutputStream data = new ByteArrayOutputStream();
            final BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(data, StandardCharsets.UTF_8), BUFFER_SIZE);

            try (XmlExporter xmlExporter = new XmlExporter()) {
                xmlExporter.doStyles(out, mProgressListener);
            }
            out.close();
            putBooklistStyles(data.toByteArray());
            mProgressListener.onProgressStep(1, null);
        }
    }

    /**
     * Write each cover file corresponding to a book to the archive.
     *
     * @return the number of covers written
     *
     * @throws IOException on failure
     */
    private int doCovers(final boolean dryRun)
            throws IOException {
        long sinceTime = 0;
        if (mSettings.dateFrom != null && (mSettings.what & ExportOptions.EXPORT_SINCE) != 0) {
            sinceTime = mSettings.dateFrom.getTime();
        }

        int ok = 0;
        int missing = 0;
        int skipped = 0;

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DBDefinitions.KEY_BOOK_UUID);
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
                        message = String.format(mProgress_msg_covers, ok, missing);
                    } else {
                        message = String.format(mProgress_msg_covers_skip, ok, missing, skipped);
                    }
                    mProgressListener.onProgressStep(1, message);
                }
            }
        }
        if (!dryRun) {
            Logger.info(this, "doCovers", " written=" + ok,
                        "missing=" + missing, "skipped=" + skipped);
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
}
