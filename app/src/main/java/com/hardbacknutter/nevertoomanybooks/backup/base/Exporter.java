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

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public interface Exporter
        extends Closeable {

    /** Buffer for the Writer. */
    int BUFFER_SIZE = 65535;

    @NonNull
    static String getNamePrefix(@NonNull final Context context) {
        return context.getString(R.string.app_name)
               + '-' + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
               + ".ntmb";
    }

    /**
     * Get the format version that this exporter is writing out.
     *
     * @return the version
     */
    int getVersion();

    /**
     * Wrapper for {@link #write(Context, Writer, ProgressListener)}.
     *
     * @param context          Current context
     * @param file             File to write to
     * @param progressListener Progress and cancellation interface
     *
     * @return {@link ExportResults}
     *
     * @throws IOException on failure
     */
    @WorkerThread
    default ExportResults write(@NonNull final Context context,
                                @NonNull final File file,
                                @NonNull final ProgressListener progressListener)
            throws IOException {
        try (OutputStream os = new FileOutputStream(file);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw, BUFFER_SIZE)) {
            return write(context, writer, progressListener);
        }
    }

    /**
     * Export to a Writer.
     *
     * @param context          Current context
     * @param writer           Writer to write to
     * @param progressListener Progress and cancellation interface
     *
     * @return {@link ExportResults}
     *
     * @throws IOException on failure
     */
    @WorkerThread
    ExportResults write(@NonNull Context context,
                        @NonNull Writer writer,
                        @NonNull ProgressListener progressListener)
            throws IOException;
}
