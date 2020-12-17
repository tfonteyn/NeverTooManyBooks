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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlRecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Default implementation of format-specific methods.
 */
public abstract class ArchiveWriterAbstract
        extends ArchiveWriterAbstractBase
        implements ArchiveWriter.SupportsArchiveHeader,
                   ArchiveWriter.SupportsPreferences,
                   ArchiveWriter.SupportsStyles {

    /**
     * The format/version is shared between writers.
     * A concrete writer merely defines the archive.
     * <ul>
     *     <li>v3: WIP
     *         <ul>
     *             <li>header: json</li>
     *             <li>styles: json</li>
     *             <li>preferences: xml</li>
     *             <li>books: json</li>
     *         </ul>
     *     </li>
     *     <li>v2: default hardcoded.
     *         <ul>
     *             <li>header: xml</li>
     *             <li>styles: xml</li>
     *             <li>preferences: xml</li>
     *             <li>books: csv</li>
     *         </ul>
     *     </li>
     *     <li>v1: obsolete</li>
     * </ul>
     * <p>
     * RELEASE: set correct archiver version
     */
    protected static final int VERSION = 2;

    private static final String FILE_EXT_XML = ".xml";
    private static final String FILE_EXT_JSON = ".json";
    private static final String FILE_EXT_CSV = ".csv";

    /** {@link #prepareBooks} writes to this file; {@link #writeBooks} copies it to the archive. */
    @Nullable
    private File mTmpBooksFile;
    private String mBooksFileExtension;

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

    @SuppressWarnings("ConstantConditions")
    @Override
    public void writeHeader(@NonNull final Context context,
                            @NonNull final ArchiveInfo archiveInfo)
            throws IOException {

        switch (VERSION) {
            case 3: {
                putString(ArchiveWriterRecord.Type.InfoHeader.getName() + FILE_EXT_JSON,
                          archiveInfo.toJson().toString());
                break;
            }
            case 2: {
                final byte[] header;
                try (XmlRecordWriter writer = new XmlRecordWriter()) {
                    header = writer.createArchiveHeader(archiveInfo);
                }
                putByteArray(ArchiveWriterRecord.Type.InfoHeader.getName() + FILE_EXT_XML,
                             header, true);
                break;
            }
            default:
                throw new IllegalStateException();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void writeStyles(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {

        switch (VERSION) {
            case 3: {
                final ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (RecordWriter recordWriter = new JsonRecordWriter(null)) {
                    mResults.add(recordWriter.write(context, data,
                                                    EnumSet.of(ArchiveWriterRecord.Type.Styles),
                                                    mHelper.getOptions(),
                                                    progressListener));
                }
                // and store the array
                putByteArray(ArchiveWriterRecord.Type.Styles.getName() + FILE_EXT_JSON,
                             data.toByteArray(), true);
                break;
            }
            case 2: {
                // Write the styles as XML to a byte array.
                final ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (RecordWriter recordWriter = new XmlRecordWriter()) {
                    mResults.add(recordWriter.write(context, data,
                                                    EnumSet.of(ArchiveWriterRecord.Type.Styles),
                                                    mHelper.getOptions(),
                                                    progressListener));
                }
                // and store the array
                putByteArray(ArchiveWriterRecord.Type.Styles.getName() + FILE_EXT_XML,
                             data.toByteArray(), true);
                break;
            }
            default:
                throw new IllegalStateException();
        }

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
        try (RecordWriter recordWriter = new XmlRecordWriter()) {
            mResults.add(recordWriter.write(context, data,
                                            EnumSet.of(ArchiveWriterRecord.Type.Preferences),
                                            mHelper.getOptions(),
                                            progressListener));
        }
        // and store the array
        putByteArray(ArchiveWriterRecord.Type.Preferences.getName() + FILE_EXT_XML,
                     data.toByteArray(), true);
    }

    /**
     * Default implementation: write as a temporary CSV file,
     * to be added to the archive in {@link #writeBooks}.
     * <p>
     * Updates progress.
     *
     * <br><br>{@inheritDoc}
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public ExportResults prepareBooks(@NonNull final Context context,
                                      @NonNull final ProgressListener progressListener)
            throws IOException {

        // Filter to valid options for this step.
        final Set<ArchiveWriterRecord.Type> types = EnumSet.noneOf(ArchiveWriterRecord.Type.class);

        if (mHelper.getExporterEntries().contains(ArchiveWriterRecord.Type.Books)) {
            types.add(ArchiveWriterRecord.Type.Books);
        }
        if (mHelper.getExporterEntries().contains(ArchiveWriterRecord.Type.Cover)) {
            types.add(ArchiveWriterRecord.Type.Cover);
        }

        // Get a temp file and set for delete
        mTmpBooksFile = File.createTempFile("books_", ".tmp");
        mTmpBooksFile.deleteOnExit();

        switch (VERSION) {
            case 3: {
                mBooksFileExtension = FILE_EXT_JSON;
                try (RecordWriter recordWriter =
                             new JsonRecordWriter(mHelper.getUtcDateTimeSince())) {
                    return recordWriter.write(context, mTmpBooksFile, types,
                                              mHelper.getOptions(),
                                              progressListener);
                }
            }
            case 2: {
                mBooksFileExtension = FILE_EXT_CSV;
                try (RecordWriter recordWriter =
                             new CsvRecordWriter(mHelper.getUtcDateTimeSince())) {
                    return recordWriter.write(context, mTmpBooksFile, types,
                                              mHelper.getOptions(),
                                              progressListener);
                }
            }
            default:
                throw new IllegalStateException();
        }
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
            Objects.requireNonNull(mTmpBooksFile);
            putFile(ArchiveWriterRecord.Type.Books.getName() + mBooksFileExtension,
                    mTmpBooksFile, true);
        } finally {
            FileUtils.delete(mTmpBooksFile);
        }
    }

    /**
     * An archive agnostic default implementation for writing cover files.
     * <p>
     * Write each cover file as collected in {@link #prepareBooks}
     * to the archive.
     * <p>
     * FIXME: not implemented as a RecordWriter,
     * as we need to access the previous ExportResults object.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    protected void writeCoversDefImpl(@NonNull final Context context,
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
     * Write a generic string to the archive.
     *
     * @param name    for the entry
     * @param content to store in the archive as bytes
     *
     * @throws IOException on failure
     */
    public void putString(@NonNull final String name,
                          @NonNull final String content)
            throws IOException {
        putByteArray(name, content.getBytes(StandardCharsets.UTF_8), true);
    }

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress Flag: compress the data if the writer supports it.
     *
     * @throws IOException on failure
     */
    public abstract void putByteArray(@NonNull String name,
                                      @NonNull byte[] bytes,
                                      @SuppressWarnings("SameParameterValue") boolean compress)
            throws IOException;


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
}
