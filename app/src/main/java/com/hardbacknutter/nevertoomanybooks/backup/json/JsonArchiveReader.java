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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

public class JsonArchiveReader
        implements ArchiveReader {

    /** Import configuration. */
    @NonNull
    private final ImportHelper mHelper;

    /**
     * Constructor.
     *
     * @param helper import configuration
     */
    public JsonArchiveReader(@NonNull final ImportHelper helper) {
        mHelper = helper;
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws ImportException,
                   IOException, DiskFullException {

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        try (RecordReader recordReader = new JsonRecordReader(
                context, mHelper.getImportEntries())) {

            // wrap the entire input into a single record.
            final ArchiveReaderRecord record = new JsonArchiveRecord(
                    mHelper.getUriInfo(context).getDisplayName(context), is);

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
