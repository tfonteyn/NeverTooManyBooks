/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

/**
 * <ul>
 *     <li>{@link CredentialsException}: We cannot authenticate to the site,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link StorageException}:     Specific local storage issues,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link DataWriterException}:  The embedded Exception has the details,
 *                                       should be reported to the user,
 *                                       but action is optional.</li>
 *    <li>{@link IOException}:           Generic IO issues.</li>
 * </ul>
 */
@FunctionalInterface
public interface DataWriter<RESULT>
        extends Closeable {

    String ERROR_NO_WRITER_AVAILABLE = "No writer available";

    /**
     * Perform a full write.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataWriterException  on a decoding/parsing of data issue
     * @throws StorageException     on storage related failures
     * @throws IOException          on generic/other IO failures
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    RESULT write(@NonNull Context context,
                 @NonNull ProgressListener progressListener)
            throws DataWriterException,
                   CredentialsException,
                   StorageException,
                   IOException;


    default void cancel() {
    }

    /**
     * Override if the implementation needs to close something.
     *
     * @throws IOException on generic/other IO failures
     */
    @Override
    default void close()
            throws IOException {
        // do nothing
    }
}
