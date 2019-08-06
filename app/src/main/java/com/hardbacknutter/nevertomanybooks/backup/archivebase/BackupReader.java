/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.archivebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.backup.ImportException;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.backup.ProgressListener;

/**
 * Public interface for any backup archive reader.
 */
public interface BackupReader
        extends Closeable {

    /**
     * Perform a restore of the database; a convenience method to loop through
     * all entities in the backup and restore them based on the entity type.
     * <p>
     * See BackupReaderAbstract for a default implementation.
     *
     * @param settings the import settings
     * @param listener Listener to receive progress information.
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    void restore(@NonNull ImportOptions settings,
                 @NonNull ProgressListener listener)
            throws IOException, ImportException;

    /**
     * Read the next ReaderEntity from the backup.
     * <p>
     * Currently, backup files are read sequentially.
     *
     * @return The next entity, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    ReaderEntity nextEntity()
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
     * Get the INFO object read from the backup.
     *
     * @return info
     */
    @NonNull
    BackupInfo getInfo();
}
