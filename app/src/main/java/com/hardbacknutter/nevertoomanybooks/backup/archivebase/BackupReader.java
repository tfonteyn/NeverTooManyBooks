/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;

/**
 * Public interface for any backup archive reader.
 */
public interface BackupReader
        extends Closeable {

    /**
     * Perform a restore of the database; a convenience method to loop through
     * all entities in the backup and restore them based on the entity type.
     * <p>
     * See {@link BackupReaderAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param settings         the import settings
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    void restore(@NonNull Context context,
                 @NonNull ImportHelper settings,
                 @NonNull ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException;

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
     * Get the {@link BackupInfo} object read from the backup.
     *
     * @return info
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    BackupInfo getInfo()
            throws IOException, InvalidArchiveException;
}
