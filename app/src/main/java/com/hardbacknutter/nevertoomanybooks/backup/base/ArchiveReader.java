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

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public interface ArchiveReader
        extends Closeable {

    /**
     * Get the {@link ArchiveInfo} object read from the backup.
     * The default implementation returns {@code null}.
     *
     * @param context Current context
     *
     * @return info object
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    default ArchiveInfo readArchiveInfo(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        return null;
    }

    /**
     * Checks if the current archive looks valid.
     * Does not need to be exhaustive. The default implementation does nothing.
     *
     * @param context Current context
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    default void validate(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        // do nothing
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
    ImportResults read(@NonNull Context context,
                       @NonNull ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException;

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
