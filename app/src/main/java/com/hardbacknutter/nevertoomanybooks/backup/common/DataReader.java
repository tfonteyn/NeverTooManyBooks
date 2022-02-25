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
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

@FunctionalInterface
public interface DataReader<METADATA, RESULTS>
        extends Closeable {

    String ERROR_NO_READER_AVAILABLE = "No reader available";

    /**
     * Checks if the current archive looks valid.
     * Does not need to be exhaustive. The default implementation does nothing.
     *
     * @param context Current context
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @WorkerThread
    default void validate(@NonNull final Context context)
            throws InvalidArchiveException,
                   ImportException,
                   IOException {
        // do nothing
    }

    /**
     * Get the {@link METADATA} object read from the backup.
     *
     * @param context Current context
     *
     * @return Optional with {@link METADATA}
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    @WorkerThread
    default Optional<METADATA> readMetaData(@NonNull final Context context)
            throws InvalidArchiveException,
                   IOException,
                   ImportException,
                   StorageException {
        return Optional.empty();
    }

    /**
     * Perform a restore/import.
     * <p>
     * See {@link ArchiveReaderAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @return the import results summary
     *
     * @throws IOException             on failure
     * @throws ImportException         on failure
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @NonNull
    @WorkerThread
    RESULTS read(@NonNull Context context,
                 @NonNull ProgressListener progressListener)
            throws InvalidArchiveException,
                   IOException,
                   ImportException,
                   StorageException,
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
