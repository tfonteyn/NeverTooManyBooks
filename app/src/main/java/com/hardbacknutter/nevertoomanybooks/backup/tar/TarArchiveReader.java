/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.tar;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Implementation of TAR-specific reader functions.
 */
public class TarArchiveReader
        extends ArchiveReaderAbstract {

    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {@link #getInputStream()}.
     */
    @Nullable
    private TarArchiveInputStream inputStream;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    public TarArchiveReader(@NonNull final Context context,
                            @NonNull final ImportHelper helper) {
        super(context, helper);
    }

    @WorkerThread
    @Override
    @NonNull
    public Optional<ArchiveReaderRecord> seek(@NonNull final RecordType type)
            throws DataReaderException, IOException {
        try {
            TarArchiveEntry entry;
            while (true) {
                entry = getInputStream().getNextTarEntry();
                if (entry == null) {
                    return Optional.empty();
                }

                final Optional<RecordType> detectedType = RecordType.getType(entry.getName());
                if (detectedType.isPresent() && type == detectedType.get()) {
                    return Optional.of(new TarArchiveRecord(this, entry));
                }
            }
        } catch (@NonNull final IOException e) {
            //VERY annoying... the apache tar library does not throw a unique exception.
            // We reluctantly look at the message, to give the user better error details
            if ("Error detected parsing the header".equals(e.getMessage())) {
                throw new DataReaderException(e);
            } else {
                throw e;
            }
        }
    }

    @WorkerThread
    @Override
    @NonNull
    public Optional<ArchiveReaderRecord> next()
            throws IOException {

        final TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(new TarArchiveRecord(this, entry));
    }

    /**
     * Get the input stream; (re)creating as needed.
     * <p>
     * <strong>Note:</strong> TarArchiveInputStream does not support marking,
     * so we let {@link #closeInputStream()} close/null the stream,
     * and (re)create it here when needed.
     *
     * @return the stream
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    private TarArchiveInputStream getInputStream()
            throws FileNotFoundException {
        if (inputStream == null) {
            inputStream = new TarArchiveInputStream(openInputStream());
        }
        return inputStream;
    }

    @Override
    protected void closeInputStream()
            throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
    }

    private static class TarArchiveRecord
            implements ArchiveReaderRecord {

        /** The record source stream. */
        @NonNull
        private final TarArchiveReader archiveReader;
        /** Tar archive entry. */
        @NonNull
        private final TarArchiveEntry entry;

        /**
         * Constructor.
         *
         * @param archiveReader Parent
         * @param entry         Corresponding archive entry
         */
        TarArchiveRecord(@NonNull final TarArchiveReader archiveReader,
                         @NonNull final TarArchiveEntry entry) {
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
            return entry.getModTime().getTime();
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws FileNotFoundException {
            // The reader can open/close the stream at will, so always ask the reader
            return archiveReader.getInputStream();
        }
    }
}
