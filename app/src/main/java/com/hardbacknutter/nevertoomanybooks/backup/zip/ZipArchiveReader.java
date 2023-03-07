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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Implementation of ZIP-specific reader functions.
 */
public class ZipArchiveReader
        extends ArchiveReaderAbstract {

    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {@link #getZipInputStream()}
     */
    @Nullable
    private ZipInputStream zipInputStream;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param helper       import configuration
     */
    public ZipArchiveReader(@NonNull final Context context,
                            @NonNull final Locale systemLocale,
                            @NonNull final ImportHelper helper) {
        super(context, systemLocale, helper);
    }

    @Override
    @WorkerThread
    @NonNull
    public Optional<ArchiveReaderRecord> seek(@NonNull final RecordType type)
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

    @Override
    @WorkerThread
    @NonNull
    public Optional<ArchiveReaderRecord> next()
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
     * @throws FileNotFoundException on ...
     */
    @NonNull
    private ZipInputStream getZipInputStream()
            throws FileNotFoundException {
        if (zipInputStream == null) {
            zipInputStream = new ZipInputStream(openInputStream());
        }
        return zipInputStream;
    }

    @Override
    protected void closeInputStream()
            throws IOException {
        if (zipInputStream != null) {
            zipInputStream.close();
            zipInputStream = null;
        }
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
