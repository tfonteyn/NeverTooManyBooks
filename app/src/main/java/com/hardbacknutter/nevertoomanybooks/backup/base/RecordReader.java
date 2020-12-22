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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

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
     * @throws IOException on failure
     */
    @Nullable
    default ArchiveMetaData readMetaData(@NonNull final ArchiveReaderRecord record)
            throws IOException {
        return null;
    }

    /**
     * Read an {@link ArchiveReaderRecord}.
     *
     * @param context          Current context
     * @param record           to read data from
     * @param options          any applicable options
     * @param progressListener Progress and cancellation provider
     *
     * @return {@link ImportResults}
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    @NonNull
    ImportResults read(@NonNull Context context,
                       @NonNull ArchiveReaderRecord record,
                       @ImportHelper.Options int options,
                       @NonNull ProgressListener progressListener)
            throws IOException, ImportException;

    /**
     * Override if the implementation needs to close something.
     *
     * @throws IOException on failure
     */
    @Override
    default void close()
            throws IOException {
        // do nothing
    }
}
