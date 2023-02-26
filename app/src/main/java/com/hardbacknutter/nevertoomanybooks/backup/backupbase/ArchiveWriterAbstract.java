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
package com.hardbacknutter.nevertoomanybooks.backup.backupbase;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Implementation of <strong>encoding-agnostic</strong> {@link DataWriter} methods.
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
 *
 *     <li>These always come last in the given order</li>
 *     <li>{@link RecordType#Books}</li>
 *     <li>{@link RecordType#Cover}</li>
 * </ol>
 * <p>
 * The {@link DataWriter#write(Context, ProgressListener)} method executes
 * the interface method flow.
 * <p>
 * 2020-12-21: we've moved to only having a single child class:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter}
 * so we could eliminate this class. But let's keep it open for the future for now.
 */
public abstract class ArchiveWriterAbstract
        implements DataWriter<ExportResults> {

    /**
     * See {@link ArchiveReaderAbstract} class docs for the version descriptions.
     * <p>
     * RELEASE: set correct archiver version
     */
    private static final int VERSION = 5;

    /**
     * Arbitrary number of steps added to the progress max value.
     * This covers the styles/prefs/etc... and a small extra safety.
     */
    private static final int EXTRA_STEPS = 10;
    private static final int META_WRITER_BUFFER = 1024;

    /** Export configuration. */
    @NonNull
    private final ExportHelper exportHelper;
    /** The accumulated results. */
    @NonNull
    private final ExportResults results = new ExportResults();
    private final RealNumberParser realNumberParser;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final Context context,
                                    @NonNull final ExportHelper helper) {
        exportHelper = helper;
        realNumberParser = new RealNumberParser(context);
    }

    /**
     * Do a full backup.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    @NonNull
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   StorageException,
                   IOException {

        // do a cleanup before we start writing
        ServiceLocator.getInstance().getMaintenanceDao().purge();

        final Set<RecordType> recordTypes = exportHelper.getRecordTypes();
        RecordType.addRelatedTypes(recordTypes);

        final LocalDateTime dateSince = exportHelper.getLastDone(context);

        try {
            int steps = ServiceLocator.getInstance().getBookDao().countBooksForExport(dateSince);
            if (steps == 0) {
                // nothing to backup.
                return results;
            }

            final boolean writeCovers = this instanceof SupportsCovers
                                        && recordTypes.contains(RecordType.Cover);
            if (writeCovers) {
                // assume 1 book == 1 cover
                steps = 2 * steps;
            }
            // set as an estimated max value
            progressListener.setMaxPos(steps + EXTRA_STEPS);

            // Prepare data/files we need information of BEFORE we can write the archive header
            final File tmpBooksFile;
            if (!progressListener.isCancelled() && recordTypes.contains(RecordType.Books)) {
                tmpBooksFile = prepareBooks(context, dateSince, progressListener);
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
                                                      RecordType.CalibreCustomFields);
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
                ((SupportsCovers) this).writeCovers(context, progressListener);
            }

        } finally {
            // closing a very large archive may take a while, so keep the progress dialog open
            progressListener.setIndeterminate(true);
            progressListener.publishProgress(
                    0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next publish call.
            progressListener.setIndeterminate(null);
        }

        // If the backup was a full backup remember that.
        exportHelper.setLastDone(context);

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
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    private File prepareBooks(@NonNull final Context context,
                              @Nullable final LocalDateTime dateSince,
                              @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final RecordEncoding encoding = getEncoding(RecordType.Books);

        // Filter to valid options for this step.
        final Set<RecordType> recordTypes = EnumSet.noneOf(RecordType.class);

        if (exportHelper.getRecordTypes().contains(RecordType.Books)) {
            recordTypes.add(RecordType.Books);
        }
        if (exportHelper.getRecordTypes().contains(RecordType.Cover)) {
            recordTypes.add(RecordType.Cover);
        }

        final File file = File.createTempFile("books_", encoding.getFileExt());
        file.deleteOnExit();
        try (OutputStream os = new FileOutputStream(file);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = encoding.createWriter(context, realNumberParser,
                                                               dateSince)) {
            results.add(recordWriter.write(context, bw, recordTypes, progressListener));
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
     * @throws IOException on generic/other IO failures
     */
    private void writeMetaData(@NonNull final Context context,
                               @NonNull final ExportResults data)
            throws DataWriterException,
                   IOException {

        final RecordEncoding encoding = getEncoding(RecordType.MetaData);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, META_WRITER_BUFFER);
             RecordWriter recordWriter = encoding.createWriter(context, realNumberParser, null)) {
            recordWriter.writeMetaData(bw, ArchiveMetaData.create(context, VERSION, data));
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
     * @throws IOException on generic/other IO failures
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
             RecordWriter recordWriter = encoding.createWriter(context, realNumberParser, null)) {
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
     * An archive-agnostic default implementation for writing cover files.
     * <p>
     * Write each cover file as collected in {@link #prepareBooks}
     * to the archive.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on generic/other IO failures
     */
    public void writeCovers(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws StorageException, IOException {

        progressListener.publishProgress(0, context.getString(R.string.lbl_covers_long));

        int exported = 0;
        int delta = 0;
        long lastUpdate = 0;

        final File coverDir = CoverDir.getDir(context);

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

    @NonNull
    @AnyThread
    protected abstract RecordEncoding getEncoding(@NonNull RecordType recordType);

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress Flag: compress the data if the writer supports it.
     *
     * @throws IOException on generic/other IO failures
     */
    protected abstract void putByteArray(@NonNull String name,
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
     * @throws IOException on generic/other IO failures
     */
    protected abstract void putFile(@NonNull String name,
                                    @NonNull File file,
                                    boolean compress)
            throws IOException;

    @FunctionalInterface
    public interface SupportsCovers {

        /**
         * Write the covers.
         *
         * @param context          Current context
         * @param progressListener Progress and cancellation interface
         *
         * @throws IOException on generic/other IO failures
         * @throws StorageException on storage related failures
         */
        @WorkerThread
        void writeCovers(@NonNull Context context,
                         @NonNull ProgressListener progressListener)
                throws StorageException, IOException;
    }
}
