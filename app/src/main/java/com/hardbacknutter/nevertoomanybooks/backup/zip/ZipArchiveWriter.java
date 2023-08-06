/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * The ZIP based archive {@link DataWriter}.
 * <p>
 * There is a <strong>strict order</strong> of the entries:
 * <ol>
 *     <li>These always come first in the given order</li>
 *     <li>{@link RecordType#MetaData}</li>
 *     <li>{@link RecordType#Styles}</li>
 *     <li>{@link RecordType#Preferences}</li>
 *
 *     <li>These depend on other types being included or not</li>
 *     <li>{@link RecordType#Certificates}</li>
 *     <li>{@link RecordType#Bookshelves}</li>
 *     <li>{@link RecordType#CalibreLibraries}</li>
 *     <li>{@link RecordType#CalibreCustomFields}</li>
 *     <li>{@link RecordType#DeletedBooks}</li>
 *
 *     <li>These always come last in the given order</li>
 *     <li>{@link RecordType#Books}</li>
 *     <li>{@link RecordType#Cover}</li>
 * </ol>
 * <p>
 * The {@link DataWriter#write(Context, ProgressListener)} method executes
 * the interface method flow.
 */
public class ZipArchiveWriter
        implements DataWriter<ExportResults> {

    /**
     * See {@link ZipArchiveReader} class docs for the version descriptions.
     * <p>
     * RELEASE: set correct archiver version
     */
    public static final int VERSION = 6;

    /**
     * Arbitrary number of steps added to the progress max value.
     * This covers the styles/prefs/etc... and a small extra safety.
     */
    private static final int EXTRA_STEPS = 10;

    private static final int META_WRITER_BUFFER = 1024;

    /** The output stream for the archive. */
    @NonNull
    private final ZipOutputStream zipOutputStream;

    @NonNull
    private final Set<RecordType> recordTypes;
    @SuppressWarnings("FieldCanBeLocal")
    @NonNull
    private final File destFile;
    @Nullable
    private final LocalDateTime sinceDateTime;

    /** The accumulated results. */
    @NonNull
    private final ExportResults results = new ExportResults();

    /**
     * Constructor.
     *
     * @param recordTypes   the record types to accept and read
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     * @param destFile      {@link File} to write to
     *
     * @throws FileNotFoundException if the uri cannot be resolved
     */
    public ZipArchiveWriter(@NonNull final Set<RecordType> recordTypes,
                            @Nullable final LocalDateTime sinceDateTime,
                            @NonNull final File destFile)
            throws FileNotFoundException {
        this.recordTypes = RecordType.addRelatedTypes(recordTypes);
        this.destFile = destFile;
        this.sinceDateTime = sinceDateTime;

        zipOutputStream = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(this.destFile),
                RecordWriter.BUFFER_SIZE));
    }

    @NonNull
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   StorageException,
                   IOException {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        // do a cleanup before we start writing
        serviceLocator.getMaintenanceDao().purge();

        try {
            int steps = 0;
            if (recordTypes.contains(RecordType.Books)) {
                steps = serviceLocator.getBookDao().countBooksForExport(sinceDateTime);
                if (steps == 0) {
                    // no books to backup. We ignore all other record types!
                    return results;
                }
            }

            final boolean writeCovers = recordTypes.contains(RecordType.Cover);
            if (writeCovers) {
                // assume 1 book == 1 cover
                steps = 2 * steps;
            }

            // set as an estimated max value
            progressListener.setMaxPos(steps + EXTRA_STEPS);

            // Prepare data/files we need information on BEFORE we can write the archive header
            @Nullable
            final File tmpBooksFile;
            if (!progressListener.isCancelled() && recordTypes.contains(RecordType.Books)) {
                tmpBooksFile = prepareBooks(context, sinceDateTime, progressListener);
            } else {
                tmpBooksFile = null;
            }

            // Recalculate the progress max value using the exact number of books/covers
            // which will now include the back-covers.
            progressListener.setMaxPos(results.getBookCount()
                                       + results.getCoverCount()
                                       + EXTRA_STEPS);

            // Start with the archive header
            writeMetaData(context, results);

            // The order we're writing is important:
            // Write styles first, and preferences next! This will facilitate & speedup
            // importing as we'll be seeking in the input archive for these.
            final List<RecordType> typeList = List.of(RecordType.Styles,
                                                      RecordType.Preferences,
                                                      RecordType.Certificates,
                                                      RecordType.Bookshelves,
                                                      RecordType.CalibreLibraries,
                                                      RecordType.CalibreCustomFields,
                                                      RecordType.DeletedBooks);
            for (final RecordType type : typeList) {
                if (!progressListener.isCancelled() && recordTypes.contains(type)) {
                    results.add(writeRecord(context, type, progressListener));
                }
            }

            // Add the previously generated books file.
            if (!progressListener.isCancelled() && tmpBooksFile != null) {
                try {
                    putFile(RecordType.Books.getName() + getEncoding(RecordType.Books).getFileExt(),
                            tmpBooksFile, true);
                } finally {
                    // no longer needed
                    FileUtils.delete(tmpBooksFile);
                }
            }

            // Always do the covers as the last step
            if (!progressListener.isCancelled() && writeCovers && results.has(RecordType.Cover)) {
                final File dir = serviceLocator.getCoverStorage().getDir();
                writeCovers(context, dir, progressListener);
            }

        } finally {
            // closing a very large archive may take a while, so keep the progress dialog open
            progressListener.setIndeterminate(true);
            progressListener.publishProgress(
                    0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next publish call.
            progressListener.setIndeterminate(null);
        }

        return results;
    }

    /**
     * Prepare the Books and Covers.
     * <p>
     * For each book which will be exported, the {@link RecordWriter} implementation should call
     * {@link ExportResults#addBook(long)} and {@link ExportResults#addCover} as needed.
     *
     * @param context          Current context
     * @param dateSince        (optional) UTC based date to select only items
     *                         modified or added since.
     * @param progressListener Progress and cancellation interface
     *
     * @return the temporary books file
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    @NonNull
    private File prepareBooks(@NonNull final Context context,
                              @Nullable final LocalDateTime dateSince,
                              @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final RecordEncoding encoding = getEncoding(RecordType.Books);

        // Filter to valid options for this step.
        final Set<RecordType> filteredRecordTypes = EnumSet.noneOf(RecordType.class);

        if (recordTypes.contains(RecordType.Books)) {
            filteredRecordTypes.add(RecordType.Books);
        }
        if (recordTypes.contains(RecordType.Cover)) {
            filteredRecordTypes.add(RecordType.Cover);
        }

        final File file = File.createTempFile("books_", encoding.getFileExt());
        file.deleteOnExit();
        try (OutputStream os = new FileOutputStream(file);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = encoding.createWriter(dateSince)) {
            results.add(recordWriter.write(context, bw, filteredRecordTypes, progressListener));
        }

        return file;
    }

    /**
     * Write the {@link RecordType#MetaData} record.
     *
     * @param context current context
     * @param data    to add to the header bundle
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    private void writeMetaData(@NonNull final Context context,
                               @NonNull final ExportResults data)
            throws DataWriterException,
                   IOException {

        final RecordEncoding encoding = getEncoding(RecordType.MetaData);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, META_WRITER_BUFFER);
             RecordWriter recordWriter = encoding.createWriter(null)) {
            recordWriter.writeMetaData(context, bw, ArchiveMetaData.create(context, VERSION, data));
        }

        putByteArray(RecordType.MetaData.getName() + encoding.getFileExt(),
                     os.toByteArray(), true);
    }

    /**
     * Writes a single record. One of:
     * <ul>
     *     <li>{@link RecordType#Styles}</li>
     *     <li>{@link RecordType#Preferences}</li>
     *     <li>{@link RecordType#Certificates}</li>
     *     <li>{@link RecordType#Bookshelves}</li>
     *     <li>{@link RecordType#CalibreLibraries}</li>
     *     <li>{@link RecordType#CalibreCustomFields}</li>
     *     <li>{@link RecordType#DeletedBooks}</li>
     * </ul>
     * <p>
     * When writing, the 'compress' flag is set to {@code true}.
     *
     * @param context          Current context
     * @param recordType       of record
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    @NonNull
    private ExportResults writeRecord(@NonNull final Context context,
                                      @NonNull final RecordType recordType,
                                      @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final RecordEncoding encoding = getEncoding(recordType);

        final ExportResults writeResults;

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = encoding.createWriter(null)) {
            writeResults = recordWriter.write(context, bw, EnumSet.of(recordType),
                                              progressListener);
        }

        // Only copy the result/output if we actually wrote something
        if (writeResults.has(recordType)) {
            putByteArray(recordType.getName() + encoding.getFileExt(), os.toByteArray(), true);
        }

        return writeResults;
    }

    /**
     * Write the covers.
     * <p>
     * Write each cover file as collected in {@link #prepareBooks}
     * to the archive.
     *
     * @param context          Current context
     * @param coverDir         root of the cover directory / destination to write
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on generic/other IO failures
     */
    private void writeCovers(@NonNull final Context context,
                             @NonNull final File coverDir,
                             @NonNull final ProgressListener progressListener)
            throws IOException {

        progressListener.publishProgress(0, context.getString(R.string.lbl_covers_long));

        int exported = 0;
        int delta = 0;
        long lastUpdate = 0;

        final String coverStr = context.getString(R.string.lbl_covers);
        for (final String filename : results.getCoverFileNames()) {
            if (progressListener.isCancelled()) {
                return;
            }

            // We're using jpg, png.. don't bother compressing.
            // Compressing might actually make some image files bigger!
            putFile(filename, new File(coverDir, filename), false);
            exported++;

            delta++;
            final long now = System.currentTimeMillis();
            if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                final String msg = context.getString(R.string.name_colon_value,
                                                     coverStr,
                                                     String.valueOf(exported));
                progressListener.publishProgress(delta, msg);
                lastUpdate = now;
                delta = 0;
            }
        }
    }

    @AnyThread
    @NonNull
    private RecordEncoding getEncoding(@NonNull final RecordType recordType) {
        switch (recordType) {
            case MetaData:
            case Styles:
            case Preferences:
            case Certificates:
            case Bookshelves:
            case CalibreLibraries:
            case CalibreCustomFields:
            case DeletedBooks:
            case Books:
            case AutoDetect:
                return RecordEncoding.Json;
            case Cover:
                return RecordEncoding.Cover;

            case Database:
            default:
                throw new IllegalArgumentException(recordType.toString());
        }
    }

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress Flag: compress the data if the writer supports it.
     *
     * @throws IOException on generic/other IO failures
     */
    private void putByteArray(@NonNull final String name,
                              @NonNull final byte[] bytes,
                              @SuppressWarnings("SameParameterValue") final boolean compress)
            throws IOException {

        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(Instant.now().toEpochMilli());
        if (compress) {
            entry.setMethod(ZipEntry.DEFLATED);
        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            final CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            entry.setCrc(crc32.getValue());
        }

        zipOutputStream.putNextEntry(entry);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            FileUtils.copy(is, zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    /**
     * Write a generic file to the archive.
     *
     * @param name     for the entry;  allows easier overriding of the file name
     * @param file     to store in the archive
     * @param compress Flag: compress the file if the writer supports it.
     *
     * @throws IOException on generic/other IO failures
     */
    private void putFile(@NonNull final String name,
                         @NonNull final File file,
                         final boolean compress)
            throws IOException {

        final BasicFileAttributes fileAttr =
                Files.readAttributes(file.toPath(), BasicFileAttributes.class);

        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(fileAttr.lastModifiedTime().toMillis());
        if (compress) {
            entry.setMethod(ZipEntry.DEFLATED);

        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(fileAttr.size());
            entry.setCompressedSize(fileAttr.size());
            final CRC32 crc32 = FileUtils.getCrc32(file);
            entry.setCrc(crc32.getValue());
        }

        zipOutputStream.putNextEntry(entry);
        try (InputStream is = new FileInputStream(file)) {
            FileUtils.copy(is, zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    @Override
    public void close()
            throws IOException {
        zipOutputStream.close();
    }
}
