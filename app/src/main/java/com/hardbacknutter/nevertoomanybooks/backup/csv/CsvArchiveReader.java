/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.VersionedFileService;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.BasicMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * A minimal implementation of {@link DataReader} which reads a plain CSV file with books.
 */
public class CsvArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    private static final String DB_BACKUP_NAME = "DbCsvBackup.db";
    private static final int DB_BACKUP_COPIES = 3;

    @NonNull
    private final Locale systemLocale;
    @NonNull
    private final Uri uri;
    @NonNull
    private final DataReader.Updates updateOption;

    /**
     * Constructor.
     *
     * @param systemLocale to use for ISO date parsing
     * @param uri          to read from
     * @param updateOption options
     */
    public CsvArchiveReader(@NonNull final Locale systemLocale,
                            @NonNull final Uri uri,
                            @NonNull final DataReader.Updates updateOption) {
        this.systemLocale = systemLocale;
        this.uri = uri;
        this.updateOption = updateOption;
    }

    @NonNull
    @Override
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException, CredentialsException, StorageException, IOException {

        // Sample the first line to get the (raw/lowercase) column names
        final String columnHeader;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             final BufferedReader reader = new BufferedReader(isr, RecordReader.BUFFER_SIZE)) {
            columnHeader = reader.readLine();
        }

        final CsvRecordReader.Origin origin = CsvRecordReader.Origin.guess(columnHeader);

        final boolean supportsUpdates = CsvRecordReader
                .parse(context, 0, columnHeader)
                .stream()
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .map(origin::mapColumnName)
                .anyMatch(DBKey.DATE_LAST_UPDATED__UTC::equals);

        final Bundle bundle = ServiceLocator.getInstance().newBundle();
        bundle.putParcelable(CsvRecordReader.Origin.BKEY, origin);
        bundle.putBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED, supportsUpdates);
        return Optional.of(new ArchiveMetaData(0, bundle));
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        // Importing CSV which we didn't create can be dangerous.
        // Backup the database, keeping up to CSV_BACKUP_COPIES copies.
        // ENHANCE: For now we don't inform the user of this nor offer a restore.

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        final File source = new File(serviceLocator.getDb().getPath());
        final File destination = new File(serviceLocator.getUpgradesDir(), DB_BACKUP_NAME);

        // Move/rename the previous/original file
        new VersionedFileService(DB_BACKUP_COPIES).save(destination);
        // and write the new copy.
        FileUtils.copy(source, destination);

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             RecordReader recordReader = new CsvRecordReader(systemLocale,
                                                             updateOption)) {
            if (is == null) {
                throw new FileNotFoundException(uri.toString());
            }
            final ArchiveReaderRecord record = new CsvArchiveRecord(
                    new UriInfo(uri).getDisplayName(context), is);

            return recordReader.read(context, record, progressListener);
        }
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    @VisibleForTesting
    public static class CsvArchiveRecord
            implements ArchiveReaderRecord {

        @NonNull
        private final String name;

        /** The record source stream. */
        @NonNull
        private final InputStream inputStream;

        /**
         * Constructor.
         *
         * @param name        of this record
         * @param inputStream InputStream to use
         */
        CsvArchiveRecord(@NonNull final String name,
                         @NonNull final InputStream inputStream) {
            this.name = name;
            this.inputStream = inputStream;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return Optional.of(RecordType.Books);
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return Optional.of(RecordEncoding.Csv);
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return inputStream;
        }
    }
}
