/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Public interface for any backup archive reader.
 */
public interface ArchiveReader
        extends Closeable {

    /**
     * Perform a restore/import.
     * <p>
     * See {@link ArchiveReaderAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException             on failure
     * @throws ImportException         on failure
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    ImportResults read(@NonNull Context context,
                       @NonNull ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException;

    /**
     * Checks if the current archive looks valid. Does not need to be exhaustive.
     *
     * @param context Current context
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    void validate(@NonNull Context context)
            throws InvalidArchiveException, IOException;

    /**
     * Get the {@link ArchiveInfo} object read from the backup.
     *
     * @return info
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    ArchiveInfo getInfo()
            throws IOException, InvalidArchiveException;

    /**
     * Read the next {@link ReaderEntity} from the backup.
     *
     * @return The next entity, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    ReaderEntity nextEntity()
            throws IOException;

    /**
     * Scan the input for the desired entity type.
     * It's the responsibility of the caller to call {@link #reset} as needed.
     *
     * @param type to get
     *
     * @return the entity if found, or {@code null} if not found.
     *
     * @throws IOException on failure
     */
    @Nullable
    ReaderEntity findEntity(@NonNull ReaderEntity.Type type)
            throws IOException;

    /**
     * Reset the reader so {@link #nextEntity()} will get the first entity.
     *
     * @throws IOException on failure
     */
    void reset()
            throws IOException;

    /**
     * Close the reader.
     *
     * @throws IOException on failure
     */
    @Override
    void close()
            throws IOException;

    /**
     * Get the <strong>minimum</strong> version of the archive that the reader can still handle.
     *
     * @return the minimum version
     */
    @SuppressWarnings("SameReturnValue")
    int canReadVersion();
}
