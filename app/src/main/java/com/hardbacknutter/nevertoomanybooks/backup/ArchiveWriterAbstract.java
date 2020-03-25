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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstractBase;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Default implementation of format-specific methods.
 *
 * <ol>This writes in order:
 * <li>XML Archive info</li>
 * <li>XML Styles</li>
 * <li>XML Preferences</li>
 * <li>CSV Books</li>
 * </ol>
 * <p>
 * Covers are implemented in {@link #doCovers} but support depends on the concrete class.
 */
public abstract class ArchiveWriterAbstract
        extends ArchiveWriterAbstractBase
        implements ArchiveWriterAbstractBase.SupportsPreferences,
                   ArchiveWriterAbstractBase.SupportsStyles {

    /**
     * The format/version is shared between writers.
     * A concrete writer merely defines the container.
     */
    protected static final int VERSION = 2;
    /** Log tag. */
    private static final String TAG = "ArchiveWriterAbstract";
    /** Buffer for the Writer. */
    private static final int BUFFER_SIZE = 65535;

    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers;
    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers_skip;

    /** {@link #prepareBooks} writes to this file; {@link #writeBooks} copies it to the archive. */
    @Nullable
    private File mTmpBookCsvFile;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final Context context,
                                    @NonNull final ExportManager helper) {
        super(context, helper);

        mProgress_msg_covers = context.getString(
                R.string.progress_end_export_result_n_covers_processed_m_missing);
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
     * Default implementation: write as XML.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeArchiveHeader(@NonNull final Context context,
                                   @NonNull final ArchiveInfo archiveInfo)
            throws IOException {
        // Write the archiveInfo as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             XmlExporter xmlExporter = new XmlExporter(context, Options.INFO, null)) {
            xmlExporter.writeArchiveInfo(writer, archiveInfo);
        }
        // and store the array
        putByteArray(ArchiveContainerEntry.InfoHeaderXml.getName(), data.toByteArray(), true);
    }

    /**
     * Default implementation: write as XML.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeStyles(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        // Write the styles as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             Exporter exporter = new XmlExporter(context, Options.STYLES, null)) {
            mResults.add(exporter.write(context, writer, progressListener));
        }
        // and store the array
        putByteArray(ArchiveContainerEntry.BooklistStylesXml.getName(), data.toByteArray(), true);
    }

    /**
     * Default implementation: write as XML.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writePreferences(@NonNull final Context context,
                                 @NonNull final ProgressListener progressListener)
            throws IOException {
        // Write the preferences as XML to a byte array.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             Exporter exporter = new XmlExporter(context, Options.PREFS, null)) {
            exporter.write(context, writer, progressListener);
        }
        // and store the array
        putByteArray(ArchiveContainerEntry.PreferencesXml.getName(), data.toByteArray(), true);
    }

    /**
     * Default implementation: write as a temporary CSV file,
     * to be added to the archive in {@link #writeBooks}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void prepareBooks(@NonNull final Context context,
                             @NonNull final ProgressListener progressListener)
            throws IOException {
        // Get a temp file and set for delete
        mTmpBookCsvFile = File.createTempFile("books_csv_", ".tmp");
        mTmpBookCsvFile.deleteOnExit();

        Exporter exporter = new CsvExporter(context, Options.BOOKS, mHelper.getDateSince());
        mResults.add(exporter.write(context, mTmpBookCsvFile, progressListener));
    }

    /**
     * Default implementation: write the file as prepared in {@link #prepareBooks}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeBooks(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws IOException {
        try {
            Objects.requireNonNull(mTmpBookCsvFile);
            putFile(ArchiveContainerEntry.BooksCsv.getName(), mTmpBookCsvFile, true);
        } finally {
            FileUtils.delete(mTmpBookCsvFile);
        }
    }

    /**
     * Write a generic file to the archive.
     *
     * @param name     for the entry;  allows easier overriding of the file name
     * @param file     to store in the archive
     * @param compress Flag: compress the file if the writer supports it.
     *
     * @throws IOException on failure
     */
    protected abstract void putFile(@NonNull String name,
                                    @NonNull File file,
                                    final boolean compress)
            throws IOException;

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress Flag: compress the data if the writer supports it.
     *
     * @throws IOException on failure
     */
    protected abstract void putByteArray(@NonNull String name,
                                         @NonNull byte[] bytes,
                                         @SuppressWarnings("SameParameterValue")
                                         final boolean compress)
            throws IOException;

    /**
     * A container agnostic default implementation for writing cover files.
     * <p>
     * Write each cover file corresponding to a book to the archive.
     *
     * <strong>Note:</strong> We update the count during <strong>dryRun</strong> only.
     *
     * @param context          Current context
     * @param dryRun           when {@code true}, no writing is done, we only count them.
     *                         when {@code false}, we write.
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    protected void doCovers(@NonNull final Context context,
                            final boolean dryRun,
                            @NonNull final ProgressListener progressListener)
            throws IOException {

        long timeFrom = mHelper.getTimeFrom();

        int exported = 0;
        int skipped = 0;
        int[] missing = new int[2];
        long lastUpdate = 0;
        int delta = 0;

        // We only export files that match database entries.
        // Orphaned files will be ignored altogether.
        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DBDefinitions.KEY_BOOK_UUID);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    File cover = AppDir.getCoverFile(context, uuid, cIdx);
                    if (cover.exists()) {
                        if (cover.lastModified() > timeFrom) {
                            if (!dryRun) {
                                // We're using jpg, png.. don't bother compressing.
                                // Compressing might actually make some image files bigger!
                                putFile(cover.getName(), cover, false);
                            }
                            exported++;
                        } else {
                            skipped++;
                        }
                    } else {
                        missing[cIdx]++;
                    }
                }

                // progress messages only during real-run.
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
                    if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL_IN_MS) {
                        progressListener.onProgressStep(delta, message);
                        lastUpdate = now;
                        delta = 0;
                    }

                }
            }
        }

        // results are collected during dry-run
        if (dryRun) {
            mResults.coversExported += exported;
            mResults.coversMissing[0] += missing[0];
            mResults.coversMissing[1] += missing[1];
            mResults.coversSkipped += skipped;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "doCovers"
                       + "|exported=" + exported
                       + "|missing[0]=" + missing[0]
                       + "|missing[1]=" + missing[1]
                       + "|skipped=" + skipped);
        }
    }
}
