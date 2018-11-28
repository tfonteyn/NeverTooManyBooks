/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Public interface for any backup archive reader.
 *
 * @author pjw
 */
public interface BackupReader extends Closeable {

    /**
     * Perform a restore of the database; a convenience method to loop through
     * all entities in the backup and restore them based on the entity type.
     *
     * See BackupReaderAbstract for a default implementation.
     *
     * @param listener Listener to receive progress information.
     */
    void restore(final @NonNull ImportSettings settings, final @NonNull BackupReaderListener listener) throws IOException;

    /**
     * Read the next ReaderEntity from the backup.
     *
     * Currently, backup files are read sequentially.
     *
     * @return The next entity, or null if at end
     */
    @Nullable
    ReaderEntity nextEntity() throws IOException;

    /**
     * Close the reader
     */
    @Override
    void close() throws IOException;

    /**
     * @return the INFO object read from the backup
     */
    @NonNull
    BackupInfo getInfo();

    /**
     * Interface for processes doing a restore operation; allows for progress indications
     *
     * @author pjw
     */
    interface BackupReaderListener {
        /**
         * Set the end point for the progress
         */
        void setMax(final int max);

        /**
         * Advance progress by 'delta'
         */
        void step(final @NonNull String message, final int delta);

        /**
         * Check if operation is cancelled
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean isCancelled();
    }

}