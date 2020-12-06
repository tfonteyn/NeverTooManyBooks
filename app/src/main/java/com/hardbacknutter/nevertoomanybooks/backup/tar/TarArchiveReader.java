/*
 * @Copyright 2020 HardBackNutter
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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;

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
    private TarArchiveInputStream mInputStream;

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

    @Override
    @Nullable
    public ArchiveReaderRecord seek(@NonNull final ArchiveReaderRecord.Type type)
            throws InvalidArchiveException, IOException {
        try {
            TarArchiveEntry entry;
            while (true) {
                entry = getInputStream().getNextTarEntry();
                if (entry == null) {
                    return null;
                }

                if (type == ArchiveReaderRecord.Type.getType(entry.getName())) {
                    return new TarArchiveRecord(this, entry);
                }
            }
        } catch (@NonNull final IOException e) {
            //VERY annoying... the apache tar library does not throw a unique exception.
            // We reluctantly look at the message, to give the user better error details
            if ("Error detected parsing the header".equals(e.getMessage())) {
                throw new InvalidArchiveException(e);
            } else {
                throw e;
            }
        }
    }

    @Override
    @Nullable
    public ArchiveReaderRecord next()
            throws IOException {

        final TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return null;
        }

        return new TarArchiveRecord(this, entry);
    }

    /**
     * Get the input stream; (re)creating as needed.
     *
     * <strong>Note:</strong> TarArchiveInputStream does not support marking,
     * so we let {@link #closeInputStream()} close/null the stream,
     * and (re)create it here when needed.
     *
     * @return the stream
     *
     * @throws IOException on failure
     */
    @NonNull
    private TarArchiveInputStream getInputStream()
            throws IOException {
        if (mInputStream == null) {
            mInputStream = new TarArchiveInputStream(openInputStream());
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

    private static class TarArchiveRecord
            implements ArchiveReaderRecord {

        /** The record source stream. */
        @NonNull
        private final TarArchiveReader mReader;
        /** Tar archive entry. */
        @NonNull
        private final TarArchiveEntry mEntry;

        /**
         * Constructor.
         *
         * @param reader Parent
         * @param entry  Corresponding archive entry
         */
        TarArchiveRecord(@NonNull final TarArchiveReader reader,
                         @NonNull final TarArchiveEntry entry) {
            mReader = reader;
            mEntry = entry;
        }

        @Nullable
        public Type getType() {
            return Type.getType(mEntry.getName());
        }

        @Nullable
        @Override
        public Encoding getEncoding() {
            return Encoding.getEncoding(mEntry.getName());
        }

        @NonNull
        @Override
        public String getName() {
            return mEntry.getName();
        }

        @Override
        public long getLastModifiedEpochMilli() {
            return mEntry.getModTime().getTime();
        }

        @NonNull
        @Override
        public InputStream getInputStream()
                throws IOException {
            // The reader can open/close the stream at will, so always ask the reader
            return mReader.getInputStream();
        }
    }
}
