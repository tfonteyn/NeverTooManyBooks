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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

@FunctionalInterface
@SuppressWarnings("RedundantThrows")
public interface SyncReader
        extends Closeable {

    /**
     * Checks if the current archive looks valid.
     * Does not need to be exhaustive. The default implementation does nothing.
     *
     * @param context Current context
     *
     * @throws IOException on other failures
     */
    @WorkerThread
    default void validate(@NonNull final Context context)
            throws ImportException,
                   IOException {
        // do nothing
    }

    /**
     * Get the {@link SyncReaderMetaData} object read from the backup.
     * The default implementation returns {@code null}.
     *
     * @param context Current context
     *
     * @return info object
     *
     * @throws IOException on other failures
     */
    @Nullable
    @WorkerThread
    default SyncReaderMetaData readMetaData(@NonNull final Context context)
            throws IOException,
                   ImportException,
                   StorageException {
        return null;
    }

    /**
     * Perform a restore/import.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @return the import results summary
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    @NonNull
    @WorkerThread
    ReaderResults read(@NonNull Context context,
                       @NonNull ProgressListener progressListener)
            throws IOException,
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
}
