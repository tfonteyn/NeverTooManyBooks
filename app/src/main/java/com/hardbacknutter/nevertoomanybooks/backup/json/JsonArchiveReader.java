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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * <strong>WARNING - EXPERIMENTAL:</strong> format can/will change, splitting of authors etc...
 */
public class JsonArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    @NonNull
    private final Uri uri;
    @NonNull
    private final Updates updateOption;
    @NonNull
    private final Set<RecordType> recordTypes;
    private final Locale systemLocale;

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
    public JsonArchiveReader(@NonNull final Context context,
                             @NonNull final Locale systemLocale,
                             @NonNull final Uri uri,
                             @NonNull final DataReader.Updates updateOption,
                             @NonNull final Set<RecordType> recordTypes) {
        this.systemLocale = systemLocale;
        this.uri = uri;
        this.updateOption = updateOption;
        this.recordTypes = recordTypes;
        RecordType.addRelatedTypes(recordTypes);
    }

    @WorkerThread
    @Override
    public void validate(@NonNull final Context context)
            throws DataReaderException,
                   IOException {
        if (metaData == null) {
            // reading it will either assign a value to metaData, or throw exceptions
            readMetaData(context);
        }

        // the info block will/can do more checks.
        metaData.validate(context);
    }

    @NonNull
    @Override
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException,
                   IOException {

        if (metaData == null) {
            @Nullable
            final InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) {
                throw new FileNotFoundException(uri.toString());
            }

            try (is; RecordReader recordReader =
                    new JsonRecordReader(context, systemLocale,
                                         EnumSet.of(RecordType.MetaData),
                                         updateOption)) {
                // wrap the entire input into a single record.
                final ArchiveReaderRecord record = new JsonArchiveRecord(
                        new UriInfo(uri).getDisplayName(context), is);

                metaData = recordReader.readMetaData(context, record).orElse(null);
            }

            // An archive based on this class <strong>must</strong> have an info block.
            if (metaData == null) {
                throw new DataReaderException(context.getString(
                        R.string.error_file_not_recognized));
            }
        }
        return Optional.of(metaData);
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException {

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) {
            throw new FileNotFoundException(uri.toString());
        }

        try (RecordReader recordReader = new JsonRecordReader(context, systemLocale,
                                                              recordTypes,
                                                              updateOption)) {
            // wrap the entire input into a single record.
            final ArchiveReaderRecord record = new JsonArchiveRecord(
                    new UriInfo(uri).getDisplayName(context), is);

            return recordReader.read(context, record, progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    @VisibleForTesting
    public static class JsonArchiveRecord
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
        JsonArchiveRecord(@NonNull final String name,
                          @NonNull final InputStream inputStream) {
            this.name = name;
            this.inputStream = inputStream;
        }

        @NonNull
        @Override
        public Optional<RecordType> getType() {
            return Optional.of(RecordType.AutoDetect);
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return Optional.of(RecordEncoding.Json);
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
