/*
 * @copyright 2013 Philip Warner.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.ExportSettings;

/**
 * Public interface for any backup archive writer.
 *
 * @author pjw
 */
public interface BackupWriter
        extends Closeable {

    /**
     * Perform a backup of the database.
     * <p>
     * See BackupWriterAbstract for a default implementation.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void backup(@NonNull ExportSettings settings,
                @NonNull BackupWriter.BackupWriterListener listener)
            throws IOException;

    /**
     * @return the containing archive.
     */
    @NonNull
    BackupContainer getContainer();

    /**
     * Write the info block to the archive.
     *
     * @throws IOException on failure
     */
    void putInfo(@NonNull byte[] bytes)
            throws IOException;

    /**
     * Write a books csv file to the archive.
     *
     * @throws IOException on failure
     */
    void putBooks(@NonNull File file)
            throws IOException;

    /**
     * Write a xml file with the exported tables to the archive.
     *
     * @throws IOException on failure
     */
    void putXmlData(@NonNull File file)
            throws IOException;

    /**
     * Write a generic file to the archive.
     *
     * @param name of the entry in the archive
     * @param file actual file to store in the archive
     *
     * @throws IOException on failure
     */
    void putFile(@NonNull String name,
                 @NonNull File file)
            throws IOException;

    /**
     * Write a collection of Booklist Styles.
     *
     * @throws IOException on failure
     */
    void putBooklistStyles(@NonNull byte[] bytes)
            throws IOException;

    /**
     * Store a SharedPreferences.
     *
     * @throws IOException on failure
     */
    void putPreferences(@NonNull byte[] bytes)
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
     * and saves some useful context.
     *
     * @author pjw
     */
    interface BackupWriterListener {

        /**
         * Set the end point for the progress.
         */
        void setMax(int max);

        /**
         * Advance progress by 'delta'.
         */
        void onProgressStep(@Nullable String message,
                            int delta);

        /**
         * @return {@code true} if operation is cancelled.
         */
        boolean isCancelled();
    }
}
