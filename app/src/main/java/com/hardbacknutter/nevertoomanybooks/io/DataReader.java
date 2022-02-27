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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

@FunctionalInterface
public interface DataReader<METADATA, RESULTS>
        extends Closeable {

    /**
     * Checks if the current archive looks valid.
     * The default implementation does nothing.
     *
     * @param context Current context
     *
     * @throws DataReaderException on a decoding/parsing of data issue
     * @throws IOException         on other failures
     */
    @WorkerThread
    default void validate(@NonNull final Context context)
            throws DataReaderException,
                   IOException {
        // do nothing
    }

    /**
     * Read the meta-data about what we'll attempt to import.
     *
     * @param context Current context
     *
     * @return Optional with {@link METADATA}
     *
     * @throws DataReaderException     on a decoding/parsing of data issue
     * @throws StorageException        there is an issue with the storage media
     * @throws IOException             on other failures
     */
    @WorkerThread
    @NonNull
    default Optional<METADATA> readMetaData(@NonNull final Context context)
            throws DataReaderException,
                   StorageException,
                   IOException {
        return Optional.empty();
    }

    /**
     * Perform a full read.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataReaderException     on a decoding/parsing of data issue
     * @throws StorageException        there is an issue with the storage media
     * @throws IOException             on other failures
     */
    @WorkerThread
    @NonNull
    RESULTS read(@NonNull Context context,
                 @NonNull ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException,
                   CredentialsException;

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

    /**
     * Existing Books/Covers handling.
     */
    enum Updates {
        /** skip updates entirely. Current data is untouched. */
        Skip,
        /** Overwrite current data with incoming data. */
        Overwrite,
        /** check the "update_date" field and only import newer data. */
        OnlyNewer
    }
}
