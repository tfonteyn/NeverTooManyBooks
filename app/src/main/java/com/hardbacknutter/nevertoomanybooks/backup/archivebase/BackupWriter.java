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
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;

/**
 * Public interface for any backup archive writer.
 */
public interface BackupWriter
        extends Closeable {

    /**
     * Perform a backup of the database.
     * <p>
     * See BackupWriterAbstract for a default implementation.
     *
     * @param context          Current context
     * @param exportHelper         the import settings
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void backup(@NonNull Context context,
                @NonNull ExportHelper exportHelper,
                @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Get the archive container.
     *
     * @return the container
     */
    @NonNull
    BackupContainer getContainer();

    /**
     * Write the info block to the archive.
     *
     * @param bytes to store in the archive
     *
     * @throws IOException on failure
     */
    void putInfo(@NonNull byte[] bytes)
            throws IOException;

    /**
     * Write a books csv file to the archive.
     *
     * @param file to store in the archive
     *
     * @throws IOException on failure
     */
    void putBooks(@NonNull File file)
            throws IOException;

    /**
     * Write a xml file with the exported tables to the archive.
     *
     * @param file to store in the archive
     *
     * @throws IOException on failure
     */
    void putXmlData(@NonNull File file)
            throws IOException;

    /**
     * Write a generic file to the archive.
     *
     * @param name of the entry in the archive
     * @param file to store in the archive
     *
     * @throws IOException on failure
     */
    void putFile(@NonNull String name,
                 @NonNull File file)
            throws IOException;

    /**
     * Write a collection of Booklist Styles.
     *
     * @param bytes to store in the archive
     *
     * @throws IOException on failure
     */
    void putBooklistStyles(@NonNull byte[] bytes)
            throws IOException;

    /**
     * Store a SharedPreferences.
     *
     * @param bytes to store in the archive
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
}
