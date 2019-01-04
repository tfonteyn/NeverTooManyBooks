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

import com.eleybourn.bookcatalogue.backup.ExportSettings;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Public interface for any backup archive writer.
 *
 * @author pjw
 */
public interface BackupWriter extends Closeable {

    /**
     * Perform a backup of the database.
     *
     * See BackupWriterAbstract for a default implementation.
     *
     * @throws IOException on failure
     */
    void backup(@NonNull final ExportSettings settings,
                @NonNull final BackupReader.BackupReaderListener listener)
            throws IOException;

    /**
     * Get the containing archive.
     */
    @NonNull
    BackupContainer getContainer();

    /**
     * Write the info block to the archive.
     *
     * @throws IOException on failure
     */
    void putInfo(@NonNull final byte[] bytes)
            throws IOException;

    /**
     * Write a books csv file to the archive.
     *
     * @throws IOException on failure
     */
    void putBooks(@NonNull final File file)
            throws IOException;

    /**
     * Write a xml file with the exported tables to the archive.
     *
     * @throws IOException on failure
     */
    void putXmlData(@NonNull final File file)
            throws IOException;

    /**
     * Write a generic file to the archive.
     *
     * @param name of the entry in the archive
     * @param file actual file to store in the archive
     *
     * @throws IOException on failure
     */
    void putFile(@NonNull final String name, @NonNull final File file)
            throws IOException;

    /**
     * Write a collection of Booklist Styles.
     *
     * @throws IOException on failure
     */
    void putBooklistStyles(@NonNull final byte[] bytes)
            throws IOException;

    /**
     * Store a SharedPreferences.
     *
     * @throws IOException on failure
     */
    void putPreferences(@NonNull final byte[] bytes)
            throws IOException;

    /**
     * Close the writer.
     *
     * @throws IOException on failure
     */
    @Override
    void close()
            throws IOException;

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
        void onProgress(@Nullable final String message, final int delta);

        /**
         * Check if operation is cancelled
         */
        boolean isCancelled();
    }
}