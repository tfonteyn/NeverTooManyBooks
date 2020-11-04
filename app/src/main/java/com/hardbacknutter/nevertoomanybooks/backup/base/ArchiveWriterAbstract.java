/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlExporter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Default implementation of format-specific methods.
 *
 * <ol>This writes in order:
 *      <li>XML Archive info</li>
 *      <li>XML Styles</li>
 *      <li>XML Preferences</li>
 *      <li>CSV Books</li>
 * </ol>
 * <p>
 * Covers are implemented but support depends on the concrete class.
 */
public abstract class ArchiveWriterAbstract
        extends ArchiveWriterAbstractBase
        implements ArchiveWriter.SupportsArchiveHeader,
                   ArchiveWriter.SupportsPreferences,
                   ArchiveWriter.SupportsStyles {

    /**
     * The format/version is shared between writers.
     * A concrete writer merely defines the container.
     */
    protected static final int VERSION = 2;

    /** Buffer for the Writer. */
    private static final int BUFFER_SIZE = 65535;

    /** {@link #prepareData} writes to this file; {@link #writeBooks} copies it to the archive. */
    @Nullable
    private File mTmpBooksFile;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final Context context,
                                    @NonNull final ExportHelper helper) {
        super(context, helper);
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
    public void writeHeader(@NonNull final Context context,
                            @NonNull final ArchiveInfo archiveInfo)
            throws IOException {
        // Write the archiveInfo as XML to a byte array.
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
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
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
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
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(data, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE);
             Exporter exporter = new XmlExporter(context, Options.PREFS, null)) {

            mResults.add(exporter.write(context, writer, progressListener));
        }
        // and store the array
        putByteArray(ArchiveContainerEntry.PreferencesXml.getName(), data.toByteArray(), true);
    }

    /**
     * Default implementation: write as a temporary CSV file,
     * to be added to the archive in {@link #writeBooks}.
     * <p>
     * Updates progress.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public ExportResults prepareData(@NonNull final Context context,
                                     @NonNull final ProgressListener progressListener)
            throws IOException {
        // Get a temp file and set for delete
        mTmpBooksFile = File.createTempFile("books_csv_", ".tmp");
        mTmpBooksFile.deleteOnExit();

        // Not strictly needed for the CsvExporter as it will ignore
        // other options, but done as a reminder (see XmlArchiveWriter)
        final int entities = mHelper.getOptions() & (Options.BOOKS | Options.COVERS);
        try (Exporter exporter = new CsvExporter(context, entities,
                                                 mHelper.getUtcDateTimeSince())) {
            return exporter.write(context, mTmpBooksFile, progressListener);
        }
    }

    /**
     * Default implementation: write the file as prepared in {@link #prepareData}.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void writeBooks(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws IOException {
        try {
            Objects.requireNonNull(mTmpBooksFile);
            putFile(ArchiveContainerEntry.BooksCsv.getName(), mTmpBooksFile, true);
        } finally {
            FileUtils.delete(mTmpBooksFile);
        }
    }

    /**
     * A container agnostic default implementation for writing cover files.
     * <p>
     * Write each cover file as collected in {@link #prepareData}
     * to the archive.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    protected void defWriteCovers(@NonNull final Context context,
                                  @NonNull final ProgressListener progressListener)
            throws IOException {

        progressListener.publishProgressStep(0, context.getString(R.string.lbl_covers_long));

        int exported = 0;
        int delta = 0;
        long lastUpdate = 0;

        final String coverStr = context.getString(R.string.lbl_covers);

        for (final String filename : mResults.getCoverFileNames()) {
            final File cover = AppDir.Covers.getFile(context, filename);
            // We're using jpg, png.. don't bother compressing.
            // Compressing might actually make some image files bigger!
            putFile(filename, cover, false);
            exported++;

            delta++;
            final long now = System.currentTimeMillis();
            if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                final String msg = context.getString(R.string.name_colon_value,
                                                     coverStr,
                                                     String.valueOf(exported));
                progressListener.publishProgressStep(delta, msg);
                lastUpdate = now;
                delta = 0;
            }
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
                                    boolean compress)
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
                                         @SuppressWarnings("SameParameterValue") boolean compress)
            throws IOException;
}
