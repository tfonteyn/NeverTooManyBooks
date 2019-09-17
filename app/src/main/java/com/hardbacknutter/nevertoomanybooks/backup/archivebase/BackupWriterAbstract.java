/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import android.content.Context;
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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 */
public abstract class BackupWriterAbstract
        implements BackupWriter {

    private static final int BUFFER_SIZE = 32768;

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /** progress message. */
    private final String mProgress_msg_covers;
    /** progress message. */
    private final String mProgress_msg_covers_skip;

    private ExportHelper mExportHelper;
    private ProgressListener mProgressListener;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    protected BackupWriterAbstract(@NonNull final Context context) {
        mDb = new DAO();
        mProgress_msg_covers = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing);
        mProgress_msg_covers_skip = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing_s_skipped);
    }

    /**
     * Do a full backup.
     *
     * @param context          Current context
     * @param exportHelper     what to backup
     * @param progressListener to send progress updates to
     */
    @Override
    @WorkerThread
    public void backup(@NonNull final Context context,
                       @NonNull final ExportHelper exportHelper,
                       @NonNull final ProgressListener progressListener)
            throws IOException {
        mExportHelper = exportHelper;
        mProgressListener = progressListener;

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = Options.NOTHING;

        boolean incBooks = (mExportHelper.options & Options.BOOK_CSV) != 0;
        boolean incCovers = (mExportHelper.options & Options.COVERS) != 0;
        boolean incStyles = (mExportHelper.options & Options.BOOK_LIST_STYLES) != 0;
        boolean incPrefs = (mExportHelper.options & Options.PREFERENCES) != 0;
        boolean incXml = (mExportHelper.options & Options.XML_TABLES) != 0;

        File tmpBookCsvFile = null;

        try {
            // If we are doing covers, get the exact number by counting them
            if (!mProgressListener.isCancelled() && incCovers) {
                doCovers(true);
            }

            // If we are doing books, generate the CSV file first, so we have the #books
            // which we need to stick into the info block, and use with the progress listener.
            if (incBooks) {
                // Get a temp file and set for delete
                tmpBookCsvFile = File.createTempFile("tmp_books_csv_", ".tmp");
                tmpBookCsvFile.deleteOnExit();

                Exporter mExporter = new CsvExporter(context, mExportHelper);
                try (OutputStream os = new FileOutputStream(tmpBookCsvFile)) {
                    // we know the # of covers...
                    // but getting the progress 100% right is not really important
                    mExportHelper.addResults(mExporter.doBooks(os, mProgressListener, incCovers));
                }
            }

            // we now have a known number of books; add the covers and we've more or less have an
            // exact number of steps. Added arbitrary 5 for the other entities we might do
            mProgressListener.setMax(mExportHelper.results.booksExported
                                     + mExportHelper.results.coversExported + 5);

            // Process each component of the Archive, unless we are cancelled.
            if (!mProgressListener.isCancelled()) {
                doInfo(mExportHelper.results.booksExported,
                       mExportHelper.results.coversExported,
                       incStyles, incPrefs);
            }
            // Write styles and prefs first. This will facilitate & speedup
            // importing as we'll be seeking in the input archive for them first.
            if (!mProgressListener.isCancelled() && incStyles) {
                mExportHelper.results.styles += doStyles();
                entitiesWritten |= Options.BOOK_LIST_STYLES;
            }
            if (!mProgressListener.isCancelled() && incPrefs) {
                doPreferences();
                entitiesWritten |= Options.PREFERENCES;
            }
            if (!mProgressListener.isCancelled() && incXml) {
                doXmlTables();
                entitiesWritten |= Options.XML_TABLES;
            }
            if (!mProgressListener.isCancelled() && incBooks) {
                try {
                    putBooks(tmpBookCsvFile);
                    entitiesWritten |= Options.BOOK_CSV;
                } finally {
                    StorageUtils.deleteFile(tmpBookCsvFile);
                }
            }
            // do covers last
            if (!mProgressListener.isCancelled() && incCovers) {
                doCovers(false);
                entitiesWritten |= Options.COVERS;
            }

        } finally {
            mExportHelper.options = entitiesWritten;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Logger.debug(this, "backup",
                             "mExportHelper.getResults()=" + mExportHelper.getResults());
            }
            try {
                close();
            } catch (@NonNull final IOException ignore) {
            }
        }
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

    private void doInfo(final int bookCount,
                        final int coverCount,
                        final boolean hasStyles,
                        final boolean hasPrefs)
            throws IOException {
        mProgressListener.onProgressStep(1, null);

        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {

            BackupInfo backupInfo = BackupInfo.newInstance(getContainer(),
                                                           bookCount, coverCount,
                                                           hasStyles, hasPrefs);
            xmlExporter.doBackupInfoBlock(out, mProgressListener, backupInfo);
        }

        putInfo(data.toByteArray());
    }

    /**
     * Export user data as XML.
     *
     * @throws IOException on failure
     */
    private void doXmlTables()
            throws IOException {
        // Get a temp file and set for delete
        File tmpFile = File.createTempFile("tmp_xml_", ".tmp");
        tmpFile.deleteOnExit();

        try (OutputStream os = new FileOutputStream(tmpFile)) {
            XmlExporter exporter = new XmlExporter(mExportHelper);
            exporter.doAll(os, mProgressListener);
            putXmlData(tmpFile);

        } finally {
            StorageUtils.deleteFile(tmpFile);
        }
    }

    private void doPreferences()
            throws IOException {
        // Turn the preferences into an XML file in a byte array
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doPreferences(out, mProgressListener);
        }

        putPreferences(data.toByteArray());
        mProgressListener.onProgressStep(1, null);
    }

    private int doStyles()
            throws IOException {
        // Turn the styles into an XML file in a byte array
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int numberOfStyles;
        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {
            numberOfStyles = xmlExporter.doStyles(out, mProgressListener);
        }

        putBooklistStyles(data.toByteArray());
        mProgressListener.onProgressStep(1, null);

        return numberOfStyles;
    }

    /**
     * Write each cover file corresponding to a book to the archive.
     *
     * <strong>Note:</strong> We update the count during <strong>dryRun</strong> only.
     *
     * @param dryRun when {@code true}, no writing is done, we only count them.
     *               when {@code false}, we write, but do not count.
     *
     * @throws IOException on failure
     */
    private void doCovers(final boolean dryRun)
            throws IOException {
        long timeFrom = mExportHelper.getTimeFrom();

        int coversExported = 0;
        int missing = 0;
        int skipped = 0;

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DBDefinitions.KEY_BOOK_UUID);
            while (cursor.moveToNext() && !mProgressListener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                File cover = StorageUtils.getCoverFileForUuid(uuid);
                if (cover.exists()) {
                    if (cover.exists() && (timeFrom < cover.lastModified())) {
                        if (!dryRun) {
                            putFile(cover.getName(), cover);
                        }
                        coversExported++;
                    } else {
                        skipped++;
                    }
                } else {
                    missing++;
                }
                if (!dryRun) {
                    String message;
                    if (skipped == 0) {
                        message = String.format(mProgress_msg_covers, coversExported, missing);
                    } else {
                        message = String.format(mProgress_msg_covers_skip,
                                                coversExported, missing, skipped);
                    }
                    mProgressListener.onProgressStep(1, message);
                }
            }
        }

        if (dryRun) {
            mExportHelper.results.coversExported += coversExported;
            mExportHelper.results.coversMissing += missing;
            mExportHelper.results.coversProcessed += coversExported + missing + skipped;
        }

        if (!dryRun) {
            Logger.info(this, "doCovers", " written=" + coversExported,
                        "missing=" + missing, "skipped=" + skipped);
        }
    }
}
