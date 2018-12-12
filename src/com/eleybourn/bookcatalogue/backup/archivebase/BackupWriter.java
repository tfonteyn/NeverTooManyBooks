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
package com.eleybourn.bookcatalogue.backup.archivebase;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * Public interface for any backup archive reader.
 *
 * @author pjw
 */
public interface BackupWriter extends Closeable {

    /**
     * Perform a restore of the database; a convenience method to loop through
     * all entities in the backup and restore them based on the entity type.
     *
     * See BackupWriterAbstract for a default implementation.
     */
    void backup(final @NonNull ExportSettings settings, final @NonNull BackupWriterListener listener) throws IOException;

    /**
     * Get the containing archive
     */
    @NonNull
    BackupContainer getContainer();

    /**
     * Write an info block to the archive
     */
    void putInfo(final @NonNull BackupInfo info) throws IOException;

    /**
     * Write a generic file to the archive
     *
     * @param name of the entry in the archive
     * @param file actual file to store in the archive
     */
    void putGenericFile(final @NonNull String name, final @NonNull File file) throws IOException;

    /**
     * Write an export file to the archive
     */
    void putBooks(final @NonNull File file) throws IOException;

    /**
     * Write a cover file to the archive
     */
    void putCoverFile(final @NonNull File file) throws IOException;

    /**
     * Store a Booklist Style
     */
    void putBooklistStyle(final @NonNull BooklistStyle style) throws IOException;

    /**
     * Store a SharedPreferences
     */
    void putPreferences(final @NonNull SharedPreferences prefs) throws IOException;

    /**
     * Close the writer
     */
    @Override
    void close() throws IOException;

    /**
     * Interface for processes doing a backup operation; allows for progress indications
     * and saves some useful context
     *
     * @author pjw
     */
    interface BackupWriterListener {
        /**
         * Set the end point for the progress
         */
        void setMax(final int max);

        /**
         * Advance progress by 'delta'
         */
        void step(final @Nullable String message, final int delta);

        /**
         * Check if operation is cancelled
         */
        boolean isCancelled();

        /**
         * Retrieve the total books
         */
        int getTotalBooks();

        /**
         * Save the total books exported
         */
        void setTotalBooks(final int books);
    }
}