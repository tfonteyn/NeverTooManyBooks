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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.bin.CoverRecordReader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * The ZIP based archive {@link DataReader}.
 * <p>
 * Currently supported formats.
 * <ul>
 *     <li>v7: adds {@link Style#getBookLevelFieldsOrderBy()} to {@link RecordType#Styles}</li>
 *     <li>v6: adds {@link RecordType#DeletedBooks} : {@link RecordEncoding#Json}</li>
 *     <li>v5: identical to v4,
 *              but a number of internal preferences have been changed/deleted.
 *     </li>
 *     <li>v4: Books will contain REFERENCES to Bookshelves and CalibreLibraries;
 *              and FULL data on other related objects.
 *         <ul>
 *             <li>{@link RecordType#MetaData} :            {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Styles} :              {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Preferences} :         {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Certificates} :        {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Bookshelves} :         {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#CalibreLibraries} :    {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#CalibreCustomFields} : {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Books} :               {@link RecordEncoding#Json}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v3: Books will contain FULL data on related objects.
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Json}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v2: xml/csv
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Csv}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v1: the original BookCatalogue format; prefs and styles cannot be imported.
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Csv}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 * </ul>
 */
public class ZipArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    private static final String ERROR_META_DATA = "metaData";
    /** Provide access to the Uri InputStream. */
    @NonNull
    private final ContentResolver contentResolver;

    /** The accumulated results. */
    @NonNull
    private final ImportResults results;

    @NonNull
    private final Locale systemLocale;
    @NonNull
    private final Uri uri;
    @NonNull
    private final Updates updateOption;
    @NonNull
    private final Set<RecordType> recordTypes;

    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {@link #getZipInputStream()}
     */
    @Nullable
    private ZipInputStream zipInputStream;

    /** Re-usable cover reader. */
    @Nullable
    private RecordReader coverReader;

    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveMetaData metaData;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param uri          to read from
     * @param updateOption options
     * @param recordTypes  the record types to accept and read
     */
    public ZipArchiveReader(@NonNull final Context context,
                            @NonNull final Locale systemLocale,
                            @NonNull final Uri uri,
                            @NonNull final DataReader.Updates updateOption,
                            @NonNull final Set<RecordType> recordTypes) {
        this.contentResolver = context.getContentResolver();
        this.systemLocale = systemLocale;
        this.uri = uri;
        this.updateOption = updateOption;
        this.recordTypes = RecordType.addRelatedTypes(recordTypes);

        this.results = new ImportResults();
    }

    @WorkerThread
    @Override
    public void validate(@NonNull final Context context)
            throws DataReaderException, IOException {
        if (metaData == null) {
            // reading it will either assign a value to metaData, or throw exceptions
            readMetaData(context);
        }

        // the info block will/can do more checks.
        metaData.validate(context);
    }

    /**
     * An archive <strong>MUST</strong> have an info block.
     * <p>
     * On any failure, this method throws an exception + {@link #metaData} will be {@code null}.
     *
     * @param context Current context
     *
     * @return a populated Optional with the metadata + {@link #metaData} assigned.
     *
     * @throws DataReaderException on a decoding/parsing of data issue.
     *                             The embedded Exception has the details,
     *                             should be reported to the user,
     *                             but action is optional.
     * @throws IOException         on generic/other IO failures
     */
    @Override
    @WorkerThread
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException, IOException {

        if (metaData == null) {
            RecordReader reader = null;
            try {
                final ArchiveReaderRecord record = seek(RecordType.MetaData)
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                final RecordEncoding encoding = record
                        .getEncoding().orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                reader = encoding
                        .createReader(context, systemLocale,
                                      EnumSet.of(RecordType.MetaData),
                                      updateOption)
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                metaData = reader
                        .readMetaData(context, record)
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                // Once here, the metaData should never be null, but AndroidStudio
                // does not seem to realise this; hence adding if/throw
                if (metaData == null) {
                    throw new DataReaderException(context.getString(
                            R.string.error_file_not_recognized));
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
                // We MUST reset the stream here, so the caller gets a pristine stream.
                closeInputStream();
            }
        }

        return Optional.of(metaData);
    }

    /**
     * Do a full import.
     * <ol>
     * <li>The header is assumed to already have been read by {@link #readMetaData(Context)}</li>
     * <li>Seek and read {@link RecordType#Styles}</li>
     * <li>Seek and read {@link RecordType#Preferences}</li>
     * <li>Sequentially read records as encountered.</li>
     * </ol>
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataReaderException on a decoding/parsing of data issue.
     *                             The embedded Exception has the details,
     *                             should be reported to the user,
     *                             but action is optional.
     * @throws StorageException    on storage related failures
     *                             The user MUST take action on it NOW.
     * @throws IOException         on generic/other IO failures
     */
    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        // Sanity check: the archive info should have been read during the validate phase
        // This is also a check that the validate method has been called.
        Objects.requireNonNull(metaData, ERROR_META_DATA);

        final int archiveVersion = metaData.getArchiveVersion();
        switch (archiveVersion) {
            case 7:
            case 6:
            case 5:
            case 4:
            case 3:
            case 2:
            case 1:
                // The reader is flexible enough to detect the different versions for now.
                // Important: testing with v2 and up is exhaustive.
                // v1 was the old BC format but reading books and covers from it SHOULD work fine.
                // The v1 prefs and styles are simply ignored.
                read(context, recordTypes, progressListener);
                break;

            default:
                throw new DataReaderException(context.getString(
                        R.string.error_unsupported_version_v, archiveVersion));
        }

        return results;
    }

    private void read(@NonNull final Context context,
                      @NonNull final Set<RecordType> recordTypes,
                      @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        Objects.requireNonNull(metaData, ERROR_META_DATA);

        int estimatedSteps = 1 + metaData.getBookCount().orElse(0);

        final boolean readCovers = recordTypes.contains(RecordType.Cover);
        if (readCovers) {
            coverReader = new CoverRecordReader(updateOption);

            final Optional<Integer> coverCount = metaData.getCoverCount();
            if (coverCount.isPresent()) {
                estimatedSteps += coverCount.get();
            } else {
                // We don't have a count, so assume each book has 1 cover.
                estimatedSteps *= 2;
            }
        }
        progressListener.setMaxPos(estimatedSteps);

        // On any semi-decent device the user won't see the record progress updates
        // other than the actual books/covers but we're showing them regardless as "why-not".
        // Also: show this HERE, before the json (or other readers) start reading
        // as they could take some time before the actual first entry is read.
        try {
            // Seek the styles record first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (recordTypes.contains(RecordType.Styles)) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_styles_long));
                final Optional<ArchiveReaderRecord> record = seek(RecordType.Styles);
                if (record.isPresent()) {
                    readRecord(context, recordTypes, record.get(), progressListener);
                }
                closeInputStream();
            }

            // Seek the preferences record next, so we can apply any prefs while reading data.
            if (recordTypes.contains(RecordType.Preferences)) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_settings));
                final Optional<ArchiveReaderRecord> record = seek(RecordType.Preferences);
                if (record.isPresent()) {
                    readRecord(context, recordTypes, record.get(), progressListener);
                }
                closeInputStream();
            }

            // Instance in time when we last send a progress message
            long lastUpdateTime = 0;
            // Count the nr of covers in between progress updates.
            int coversDelta = 0;

            Optional<ArchiveReaderRecord> nextRecord;
            // process each entry based on type, unless we are cancelled.
            while ((nextRecord = next()).isPresent()
                   && !progressListener.isCancelled()) {

                final ArchiveReaderRecord record = nextRecord.get();
                if (record.getType().isPresent()) {
                    final RecordType type = record.getType().get();

                    if (type == RecordType.Cover && readCovers) {
                        coversDelta++;
                        final long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > progressListener.getUpdateIntervalInMs()) {
                            // send accumulated progress for the total nr of covers
                            progressListener.publishProgress(
                                    coversDelta, results.createCoversSummaryLine(context));
                            lastUpdateTime = now;
                            coversDelta = 0;
                        }

                        // there will be many covers... we're re-using a single RecordReader
                        results.add(coverReader.read(context, record, progressListener));

                    } else if (type == RecordType.Books && recordTypes.contains(type)) {
                        progressListener.publishProgress(
                                1, context.getString(R.string.lbl_books));
                        readRecord(context, recordTypes, record, progressListener);

                    } else if (type == RecordType.Bookshelves && recordTypes.contains(type)) {
                        progressListener.publishProgress(
                                1, context.getString(R.string.lbl_bookshelves));
                        readRecord(context, recordTypes, record, progressListener);

                    } else if (type == RecordType.Certificates && recordTypes.contains(type)) {
                        progressListener.publishProgress(
                                1, context.getString(R.string.lbl_certificates));
                        readRecord(context, recordTypes, record, progressListener);

                    } else if ((type == RecordType.CalibreLibraries
                                || type == RecordType.CalibreCustomFields)
                               && recordTypes.contains(type)) {
                        progressListener.publishProgress(
                                1, context.getString(R.string.lbl_calibre_content_server));
                        readRecord(context, recordTypes, record, progressListener);

                    } else if (type == RecordType.DeletedBooks
                               && recordTypes.contains(type)) {
                        progressListener.publishProgress(
                                1, context.getString(R.string.lbl_books));
                        readRecord(context, recordTypes, record, progressListener);
                    }
                }
            }
        } finally {
            try {
                close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
        }
    }

    /**
     * Read a single {@link ArchiveReaderRecord}.
     * For each record, a new {@link RecordReader} will be created and closed after usage.
     *
     * @param context          Current context
     * @param allowedTypes     the {@link RecordType}s which the reader
     *                         will be <strong>allowed</strong> to read.
     *                         This allows filtering/skipping unwanted entries
     * @param record           the record to read
     * @param progressListener Progress and cancellation interface
     *
     * @throws DataReaderException on record format failures
     * @throws IOException         on generic/other IO failures
     * @throws StorageException    on storage related failures
     */
    private void readRecord(@NonNull final Context context,
                            @NonNull final Set<RecordType> allowedTypes,
                            @NonNull final ArchiveReaderRecord record,
                            @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        final Optional<RecordEncoding> recordEncoding = record.getEncoding();
        if (recordEncoding.isPresent()) {
            RecordReader reader = null;
            try {
                final Optional<RecordReader> optReader =
                        recordEncoding.get().createReader(context, systemLocale,
                                                          allowedTypes,
                                                          updateOption);
                if (optReader.isPresent()) {
                    reader = optReader.get();
                    results.add(reader.read(context, record, progressListener));
                } else {
                    results.recordsSkipped++;
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        } else {
            results.recordsSkipped++;
        }
    }


    /**
     * Scan the input for the desired record type.
     * It's the responsibility of the caller to call {@link #closeInputStream} as needed.
     *
     * @param type to get
     *
     * @return Optional with the record
     *
     * @throws DataReaderException wraps a format specific Exception
     * @throws IOException         on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    private Optional<ArchiveReaderRecord> seek(@NonNull final RecordType type)
            throws DataReaderException, IOException {
        try {
            ZipEntry entry;
            while (true) {
                entry = getZipInputStream().getNextEntry();
                if (entry == null) {
                    return Optional.empty();
                }

                final Optional<RecordType> detectedType = RecordType.getType(entry.getName());
                if (detectedType.isPresent() && type == detectedType.get()) {
                    return Optional.of(new ZipArchiveRecord(this, entry));
                }
            }
        } catch (@NonNull final ZipException e) {
            throw new DataReaderException(e);
        }
    }

    /**
     * Read the next {@link ArchiveReaderRecord} from the backup.
     *
     * @return The next record
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    private Optional<ArchiveReaderRecord> next()
            throws IOException {

        final ZipEntry entry = getZipInputStream().getNextEntry();
        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(new ZipArchiveRecord(this, entry));
    }

    /**
     * Get the input stream; (re)creating as needed.
     * <p>
     * <strong>Note:</strong> ZipInputStream does not support marking,
     * so we let {@link #closeInputStream()} close/null the stream,
     * and (re)create it here when needed.
     *
     * @return the stream
     *
     * @throws FileNotFoundException if the uri cannot be resolved
     */
    @NonNull
    private ZipInputStream getZipInputStream()
            throws FileNotFoundException {

        if (zipInputStream == null) {
            final InputStream is = contentResolver.openInputStream(uri);
            if (is == null) {
                throw new FileNotFoundException(uri.toString());
            }
            zipInputStream = new ZipInputStream(new BufferedInputStream(
                    is, RecordReader.BUFFER_SIZE));
        }
        return zipInputStream;
    }

    /**
     * Reset the reader so {@link #next()} will get the first record.
     *
     * @throws IOException on generic/other IO failures
     */
    private void closeInputStream()
            throws IOException {
        if (zipInputStream != null) {
            zipInputStream.close();
            zipInputStream = null;
        }
    }

    @Override
    @CallSuper
    public void close()
            throws IOException {
        closeInputStream();

        if (coverReader != null) {
            coverReader.close();
        }

        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    private static class ZipArchiveRecord
            implements ArchiveReaderRecord {

        /** The record source stream. */
        @NonNull
        private final ZipArchiveReader archiveReader;
        /** Zip archive entry. */
        @NonNull
        private final ZipEntry entry;

        /**
         * Constructor.
         *
         * @param archiveReader Parent
         * @param entry         Corresponding archive entry
         */
        ZipArchiveRecord(@NonNull final ZipArchiveReader archiveReader,
                         @NonNull final ZipEntry entry) {
            this.archiveReader = archiveReader;
            this.entry = entry;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return RecordType.getType(entry.getName());
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return RecordEncoding.getEncoding(entry.getName());
        }

        @NonNull
        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public long getLastModifiedEpochMilli() {
            final long time = entry.getTime();
            if (time == -1) {
                // it's unlikely there won't be a 'time',
                // but if its missing use 'now' ... i.e. pretend the incoming data is newer
                return Instant.now().toEpochMilli();
            } else {
                return time;
            }
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws FileNotFoundException {
            // The reader can open/close the stream at will, so always ask the reader
            return archiveReader.getZipInputStream();
        }
    }
}
