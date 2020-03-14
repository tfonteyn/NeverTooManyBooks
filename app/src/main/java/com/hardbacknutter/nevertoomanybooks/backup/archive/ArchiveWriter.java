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
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Public interface for any backup archive writer.
 */
public interface ArchiveWriter
        extends Closeable {

    /**
     * Perform a backup.
     * <p>
     * See BackupWriterAbstract for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    ExportResults write(@NonNull Context context,
                        @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Write the archive header information.
     * <p>
     * See {@link ArchiveWriterAbstract} for a default implementation.
     *
     * @param archiveInfo header
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void writeArchiveHeader(@NonNull ArchiveInfo archiveInfo)
            throws IOException;

    /**
     * Write the styles.
     * <p>
     * See {@link ArchiveWriterAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    void writeStyles(@NonNull Context context,
                     @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Write the preference settings.
     * <p>
     * See {@link ArchiveWriterAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    void writePreferences(@NonNull final Context context,
                          @NonNull final ProgressListener progressListener)
            throws IOException;

    /**
     * Prepare the books. This is step 1 of writing books.
     * Implementations should count the number of books and populate the archive header.
     * <p>
     * See {@link ArchiveWriterAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void prepareBooks(@NonNull final Context context,
                      @NonNull final ProgressListener progressListener)
            throws IOException;

    /**
     * Write the books. This is step 2 of writing books.
     * <p>
     * See {@link ArchiveWriterAbstract} for a default implementation.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void writeBooks(@NonNull final Context context,
                    @NonNull final ProgressListener progressListener)
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
     * Write a generic byte array to the archive.
     *
     * @param name  of the entry in the archive
     * @param bytes to store in the archive
     *
     * @throws IOException on failure
     */
    default void putByteArray(@NonNull String name,
                              @NonNull byte[] bytes)
            throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Close the writer.
     *
     * @throws IOException on failure
     */
    @Override
    void close()
            throws IOException;

    /**
     * Get the version of the underlying archiver used to write archives.
     *
     * @return the version
     */
    int getVersion();

    interface SupportsBinary {

    }
}
