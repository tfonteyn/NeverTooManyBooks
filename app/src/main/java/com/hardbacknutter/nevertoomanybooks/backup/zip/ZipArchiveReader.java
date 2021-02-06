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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;

/**
 * Implementation of ZIP-specific reader functions.
 */
public class ZipArchiveReader
        extends ArchiveReaderAbstract {

    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {@link #getInputStream()}
     */
    @Nullable
    private ZipInputStream mInputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    public ZipArchiveReader(@NonNull final Context context,
                            @NonNull final ImportHelper helper) {
        super(context, helper);
    }

    @Override
    @Nullable
    @WorkerThread
    public ArchiveReaderRecord seek(@NonNull final RecordType type)
            throws InvalidArchiveException, IOException {
        try {
            ZipEntry entry;
            while (true) {
                entry = getInputStream().getNextEntry();
                if (entry == null) {
                    return null;
                }

                final Optional<RecordType> detectedType = RecordType.getType(entry.getName());
                if (detectedType.isPresent() && type == detectedType.get()) {
                    return new ZipArchiveRecord(this, entry);
                }
            }
        } catch (@NonNull final ZipException e) {
            throw new InvalidArchiveException(e);
        }
    }

    @Override
    @Nullable
    @WorkerThread
    public ArchiveReaderRecord next()
            throws IOException {

        final ZipEntry entry = getInputStream().getNextEntry();
        if (entry == null) {
            return null;
        }

        return new ZipArchiveRecord(this, entry);
    }

    /**
     * Get the input stream; (re)creating as needed.
     *
     * <strong>Note:</strong> ZipInputStream does not support marking,
     * so we let {@link #closeInputStream()} close/null the stream,
     * and (re)create it here when needed.
     *
     * @return the stream
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    private ZipInputStream getInputStream()
            throws FileNotFoundException {
        if (mInputStream == null) {
            mInputStream = new ZipInputStream(openInputStream());
        }
        return mInputStream;
    }

    @Override
    protected void closeInputStream()
            throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }
    }

    private static class ZipArchiveRecord
            implements ArchiveReaderRecord {

        /** The record source stream. */
        @NonNull
        private final ZipArchiveReader mReader;
        /** Zip archive entry. */
        @NonNull
        private final ZipEntry mEntry;

        /**
         * Constructor.
         *
         * @param reader Parent
         * @param entry  Corresponding archive entry
         */
        ZipArchiveRecord(@NonNull final ZipArchiveReader reader,
                         @NonNull final ZipEntry entry) {
            mReader = reader;
            mEntry = entry;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return RecordType.getType(mEntry.getName());
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return RecordEncoding.getEncoding(mEntry.getName());
        }

        @NonNull
        @Override
        public String getName() {
            return mEntry.getName();
        }

        @Override
        public long getLastModifiedEpochMilli() {
            final long time = mEntry.getTime();
            if (time != -1) {
                return time;
            } else {
                // just pretend
                return Instant.now().toEpochMilli();
            }
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws FileNotFoundException {
            // The reader can open/close the stream at will, so always ask the reader
            return mReader.getInputStream();
        }
    }
}
