/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * <strong>WARNING - EXPERIMENTAL:</strong> format can/will change, splitting of authors etc...
 */
public class JsonArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    /** Import configuration. */
    @NonNull
    private final ImportHelper mHelper;

    @Nullable
    private ArchiveMetaData mMetaData;

    /**
     * Constructor.
     *
     * @param helper import configuration
     */
    public JsonArchiveReader(@NonNull final ImportHelper helper) {
        mHelper = helper;
    }

    @WorkerThread
    @Override
    public void validate(@NonNull final Context context)
            throws DataReaderException,
                   IOException {
        if (mMetaData == null) {
            // reading it will either assign a value to mMetaData, or throw exceptions
            readMetaData(context);
        }

        // the info block will/can do more checks.
        mMetaData.validate(context);
    }

    @NonNull
    @Override
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException,
                   IOException {

        if (mMetaData == null) {
            @Nullable
            final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
            if (is == null) {
                throw new FileNotFoundException(mHelper.getUri().toString());
            }

            //noinspection TryFinallyCanBeTryWithResources
            try (RecordReader recordReader = new JsonRecordReader(
                    context, EnumSet.of(RecordType.MetaData))) {
                // wrap the entire input into a single record.
                final ArchiveReaderRecord record = new JsonArchiveRecord(
                        mHelper.getUriInfo().getDisplayName(context), is);

                mMetaData = recordReader.readMetaData(context, record).orElse(null);
            } finally {
                is.close();
            }

            // An archive based on this class <strong>must</strong> have an info block.
            if (mMetaData == null) {
                throw new DataReaderException(context.getString(
                        R.string.error_file_not_recognized));
            }
        }
        return Optional.of(mMetaData);
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
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        final Set<RecordType> recordTypes = mHelper.getRecordTypes();
        RecordType.addRelatedTypes(recordTypes);

        try (RecordReader recordReader = new JsonRecordReader(context, recordTypes)) {
            // wrap the entire input into a single record.
            final ArchiveReaderRecord record = new JsonArchiveRecord(
                    mHelper.getUriInfo().getDisplayName(context), is);

            return recordReader.read(context, record, mHelper, progressListener);
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
        private final String mName;

        /** The record source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param name of this record
         * @param is   InputStream to use
         */
        JsonArchiveRecord(@NonNull final String name,
                          @NonNull final InputStream is) {
            mName = name;
            mIs = is;
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
            return mName;
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return mIs;
        }
    }
}
