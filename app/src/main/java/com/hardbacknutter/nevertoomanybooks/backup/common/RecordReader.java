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
package com.hardbacknutter.nevertoomanybooks.backup.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Implements Closeable to enforce a cleanup structure.
 */
public interface RecordReader
        extends Closeable {

    /** Buffer for the readers. */
    int BUFFER_SIZE = 65535;

    /**
     * Read the archive information block.
     *
     * @param record to read data from
     *
     * @return the archive info
     *
     * @throws ImportException on a decoding/parsing of data issue
     * @throws IOException     on failure
     */
    @Nullable
    default ArchiveMetaData readMetaData(@NonNull final ArchiveReaderRecord record)
            throws IOException, ImportException {
        return null;
    }

    /**
     * Read an {@link ArchiveReaderRecord}.
     *
     * @param context          Current context
     * @param record           to read data from
     * @param helper           ImportHelper for options etc...
     * @param progressListener Progress and cancellation provider
     *
     * @return {@link ImportResults}
     *
     * @throws ImportException  on a decoding/parsing of data issue
     * @throws StorageException there is an issue with the storage media
     * @throws IOException      on failure
     */
    @NonNull
    ImportResults read(@NonNull Context context,
                       @NonNull ArchiveReaderRecord record,
                       @NonNull ImportHelper helper,
                       @NonNull ProgressListener progressListener)
            throws ImportException, IOException, StorageException;

    /**
     * Override if the implementation needs to close something.
     */
    @Override
    default void close() {
        // do nothing
    }
}
