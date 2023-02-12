/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

@FunctionalInterface
public interface RecordWriter
        extends Closeable {

    /** Buffer for the Writer. */
    int BUFFER_SIZE = 65535;

    /**
     * Write a {@link RecordType#MetaData} record.
     *
     * @param writer   Writer to write to
     * @param metaData the bundle of information to write
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    @WorkerThread
    default void writeMetaData(@NonNull final Writer writer,
                               @NonNull final ArchiveMetaData metaData)
            throws DataWriterException,
                   IOException {
        // do nothing
    }

    /**
     * Write a Set of {@link RecordType} records.
     * Unsupported record types should/will be silently skipped.
     * <p>
     * <strong>Dev. note:</strong> there is no StorageException because we don't implement
     * this interface for writing covers (which is just a File copy)
     *
     * @param context          Current context
     * @param writer           Writer to write to
     * @param recordTypes      The set of records which should be written.
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    ExportResults write(@NonNull Context context,
                        @NonNull Writer writer,
                        @NonNull Set<RecordType> recordTypes,
                        @NonNull ProgressListener progressListener)
            throws DataWriterException,
                   IOException;

    /**
     * Override if the implementation needs to close something.
     */
    @Override
    default void close() {
        // do nothing
    }
}
