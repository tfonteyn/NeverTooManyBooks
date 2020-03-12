/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

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
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Basic implementation of format-agnostic BackupWriter methods using
 * only a limited set of methods from the base interface.
 */
public abstract class ArchiveWriterAbstract
        implements ArchiveWriter {

    /** Log tag. */
    private static final String TAG = "BackupWriterAbstract";

    /** archives are written in this version. */
    private static final int VERSION = 2;

    private static final int BUFFER_SIZE = 32768;
    /** While writing cover images, only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers;
    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers_skip;
    /**  export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final Context context,
                                    @NonNull final ExportHelper helper) {
        mHelper = helper;

        mDb = new DAO(TAG);
        mProgress_msg_covers = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing);
        mProgress_msg_covers_skip = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing_s_skipped);
    }

    /**
     * We always write the latest version archives (no backwards compatibility).
     */
    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * Do a full backup.
     *
     * @param context          Current context
     * @param progressListener to send progress updates to
     */
    @Override
    @WorkerThread
    public void write(@NonNull final Context context,
                      @NonNull final ProgressListener progressListener)
            throws IOException {

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = Options.NOTHING;

        boolean incBooks = mHelper.getOption(Options.BOOK_CSV);
        boolean incCovers = mHelper.getOption(Options.COVERS);
        boolean incStyles = mHelper.getOption(Options.BOOK_LIST_STYLES);
        boolean incPrefs = mHelper.getOption(Options.PREFERENCES);
        boolean incXml = mHelper.getOption(Options.XML_TABLES);

        File tmpBookCsvFile = null;

        try {
            // If we are doing covers, get the exact number by counting them.
            // We want to stick that number into the INFO block.
            // Once you get beyond a 1000 covers this step is getting tedious/slow....
            if (!progressListener.isCancelled() && incCovers) {
                // set the progress bar temporarily in indeterminate mode.
                progressListener.setIndeterminate(true);
                progressListener.onProgress(0, context.getString(R.string.progress_msg_searching));
                doCovers(context, mHelper, true, progressListener);
                // reset; won't take effect until the next onProgress.
                progressListener.setIndeterminate(null);

                // set as temporary max, but keep in mind the position itself is still == 0
                progressListener.setMax(mHelper.getResults().coversExported);
            }

            // If we are doing books, generate the CSV file first, so we have the #books
            // which we want to stick into the INFO block, and use with the progress listener.
            if (incBooks) {
                // Get a temp file and set for delete
                tmpBookCsvFile = File.createTempFile("tmp_books_csv_", ".tmp");
                tmpBookCsvFile.deleteOnExit();

                Exporter mExporter = new CsvExporter(context, mHelper);
                try (OutputStream os = new FileOutputStream(tmpBookCsvFile)) {
                    // doBooks will use the max and add the number of books for the new max.
                    mHelper.addResults(mExporter.doBooks(os, progressListener));
                }
            }

            // Calculate the max value for the progress bar.
            int max = mHelper.getResults().booksExported
                      + mHelper.getResults().coversExported;

            // arbitrarily add 10 for the other entities we might do.
            progressListener.setMax(max + 10);

            // Process each component of the Archive, unless we are cancelled.

            // Start with the INFO
            if (!progressListener.isCancelled()) {
                progressListener.onProgressStep(1, null);

                ArchiveInfo info = ArchiveInfo.newInstance(context, VERSION,
                                                           mHelper.getResults().booksExported,
                                                           mHelper.getResults().coversExported,
                                                           incStyles, incPrefs);

                putInfo(info);
            }

            // Write styles and prefs next. This will facilitate & speedup
            // importing as we'll be seeking in the input archive for them first.
            if (!progressListener.isCancelled() && incStyles) {
                progressListener.onProgressStep(1, context.getString(R.string.lbl_styles));
                mHelper.getResults().styles += doStyles(context);
                progressListener.onProgressStep(mHelper.getResults().styles, null);
                entitiesWritten |= Options.BOOK_LIST_STYLES;
            }
            if (!progressListener.isCancelled() && incPrefs) {
                progressListener.onProgressStep(1, context.getString(R.string.lbl_settings));
                doPreferences(context);
                entitiesWritten |= Options.PREFERENCES;
            }

            if (!progressListener.isCancelled() && incXml) {
                doXmlTables(context, mHelper, progressListener);
                entitiesWritten |= Options.XML_TABLES;
            }

            if (!progressListener.isCancelled() && incBooks) {
                try {
                    progressListener.onProgressStep(1, context.getString(R.string.lbl_books));
                    putFile(ArchiveManager.BOOKS_FILE, tmpBookCsvFile);
                    entitiesWritten |= Options.BOOK_CSV;
                } finally {
                    FileUtils.delete(tmpBookCsvFile);
                }
            }

            // do covers last
            if (!progressListener.isCancelled() && incCovers) {
                doCovers(context, mHelper, false, progressListener);
                entitiesWritten |= Options.COVERS;
            }

        } finally {
            mHelper.setOptions(entitiesWritten);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Log.d(TAG, "backup|helper.getResults()=" + mHelper.getResults());
            }
            try {
                // closing a very large archive will take a while.
                progressListener.setIndeterminate(true);
                progressListener
                        .onProgress(0, context.getString(R.string.progress_msg_please_wait));
                close();
                // reset; won't take effect until the next onProgress.
                progressListener.setIndeterminate(null);
            } catch (@NonNull final IOException ignore) {
                // ignore
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

    /**
     * Export user data as XML.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws IOException on failure
     */
    private void doXmlTables(@NonNull final Context context,
                             @NonNull final ExportHelper helper,
                             @NonNull final ProgressListener progressListener)
            throws IOException {

        // Get a temp file and set for delete
        File tmpFile = File.createTempFile("tmp_xml_", ".tmp");
        tmpFile.deleteOnExit();

        try (OutputStream os = new FileOutputStream(tmpFile)) {
            XmlExporter exporter = new XmlExporter(helper);
            exporter.doAll(context, os, progressListener);
            putFile(ArchiveManager.XML_DATA, tmpFile);

        } finally {
            FileUtils.delete(tmpFile);
        }
    }

    private void doPreferences(@NonNull final Context context)
            throws IOException {

        // Turn the preferences into an XML file in a byte array
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {
            xmlExporter.doPreferences(context, out);
        }
        putByteArray(ArchiveManager.PREFERENCES, data.toByteArray());
    }

    private int doStyles(@NonNull final Context context)
            throws IOException {

        // Turn the styles into an XML file in a byte array
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int numberOfStyles;
        try (OutputStreamWriter osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter()) {
            numberOfStyles = xmlExporter.doStyles(context, out);
        }
        putByteArray(ArchiveManager.STYLES, data.toByteArray());
        return numberOfStyles;
    }

    /**
     * Write each cover file corresponding to a book to the archive.
     *
     * <strong>Note:</strong> We update the count during <strong>dryRun</strong> only.
     *
     * @param context Current context
     * @param helper  export configuration
     * @param dryRun  when {@code true}, no writing is done, we only count them.
     *                when {@code false}, we write.
     *
     * @throws IOException on failure
     */
    private void doCovers(@NonNull final Context context,
                          @NonNull final ExportHelper helper,
                          final boolean dryRun,
                          @NonNull final ProgressListener progressListener)
            throws IOException {

        long timeFrom = helper.getTimeFrom();

        int exported = 0;
        int skipped = 0;
        int[] missing = new int[2];
        long lastUpdate = 0;
        int delta = 0;

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DBDefinitions.KEY_BOOK_UUID);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    File cover = AppDir.getCoverFile(context, uuid, cIdx);
                    if (cover.exists()) {
                        if (timeFrom < cover.lastModified()) {
                            if (!dryRun) {
                                putFile(cover.getName(), cover);
                            }
                            exported++;
                        } else {
                            skipped++;
                        }
                    } else {
                        missing[cIdx]++;
                    }
                }
                if (!dryRun) {
                    String message;
                    if (skipped == 0) {
                        message = String.format(mProgress_msg_covers,
                                                exported, missing[0], missing[1]);
                    } else {
                        message = String.format(mProgress_msg_covers_skip,
                                                exported, missing[0], missing[1], skipped);
                    }
                    delta++;
                    long now = System.currentTimeMillis();
                    if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
                        progressListener.onProgressStep(delta, message);
                        lastUpdate = now;
                        delta = 0;
                    }

                }
            }
        }

        if (dryRun) {
            Exporter.Results results = helper.getResults();
            results.coversExported += exported;
            results.coversMissing[0] += missing[0];
            results.coversMissing[1] += missing[1];
            results.coversSkipped += skipped;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "doCovers"
                       + "|written=" + exported
                       + "|missing[0]=" + missing[0]
                       + "|missing[1]=" + missing[1]
                       + "|skipped=" + skipped);
        }
    }
}
