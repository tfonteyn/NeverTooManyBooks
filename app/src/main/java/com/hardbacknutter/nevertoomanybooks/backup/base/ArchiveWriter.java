/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public interface ArchiveWriter
        extends Closeable {

    /**
     * Get the format version that this archiver is writing out.
     *
     * @return the version
     */
    @AnyThread
    int getVersion();

    /**
     * Perform a write.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @return the export results summary
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    ExportResults write(@NonNull Context context,
                        @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Override if the implementation needs to close something.
     *
     * @throws IOException on failure
     */
    @Override
    default void close()
            throws IOException {
        // do nothing
    }
}
