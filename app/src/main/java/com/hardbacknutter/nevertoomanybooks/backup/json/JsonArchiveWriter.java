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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;

/**
 * <strong>WARNING - EXPERIMENTAL:</strong> format can/will change, splitting of authors etc...
 *  <ul>
 *      <li>Root element {@link JsonCoder#TAG_APPLICATION_ROOT} contains:
 *          <ul>
 *              <li>{@link RecordType#AutoDetect}, which contains:
 *                  <ul>
 *                      <li>list of {@link RecordType}</li>
 *                  </ul>
 *              </li>
 *              <li>{@link RecordType#MetaData}</li>
 *          </ul>
 *     </li>
 * </ul>
 */
public class JsonArchiveWriter
        implements DataWriter<ExportResults> {

    private static final int VERSION = 1;

    /**
     * Arbitrary number of steps added to the progress max value.
     * This covers the styles/prefs/etc... and a small extra safety.
     */
    private static final int EXTRA_STEPS = 10;

    @NonNull
    private final Set<RecordType> recordTypes;
    @NonNull
    private final File destFile;
    @Nullable
    private final LocalDateTime sinceDateTime;

    /**
     * Constructor.
     *
     * @param recordTypes   the record types to accept and read
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     * @param destFile      {@link File} to write to
     */
    public JsonArchiveWriter(@NonNull final Set<RecordType> recordTypes,
                             @Nullable final LocalDateTime sinceDateTime,
                             @NonNull final File destFile) {
        this.recordTypes = recordTypes;
        this.destFile = destFile;
        this.sinceDateTime = sinceDateTime;
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        // do a cleanup before we start writing
        serviceLocator.getMaintenanceDao().purge();

        int steps = 0;
        if (recordTypes.contains(RecordType.Books)) {
            steps = serviceLocator.getBookDao().countBooksForExport(sinceDateTime);
            if (steps == 0) {
                // no books to backup. We ignore all other record types!
                return new ExportResults();
            }
        }

        // set as an estimated max value
        progressListener.setMaxPos(steps + EXTRA_STEPS);

        final ExportResults results;

        try (OutputStream os = new FileOutputStream(destFile);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = new JsonRecordWriter(sinceDateTime)) {

            // manually concat
            // 1. archive envelope
            bw.write("{\"" + JsonCoder.TAG_APPLICATION_ROOT + "\":{");
            // add the archive version at the top to facilitate parsing
            bw.write("\"" + ArchiveMetaData.INFO_ARCHIVER_VERSION + "\":" + VERSION);

            // 2. container object
            bw.write(",\"" + RecordType.AutoDetect.getName() + "\":");

            // 3. the actual data inside the container
            results = recordWriter.write(context, bw, recordTypes, progressListener);

            // 4. the metadata
            bw.write(",\"" + RecordType.MetaData.getName() + "\":");
            recordWriter.writeMetaData(context, bw,
                                       ArchiveMetaData.create(context, VERSION, results));

            // 5. close the envelope
            bw.write("}}");
        }

        return results;
    }
}
