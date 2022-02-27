/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

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

    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    /**
     * Constructor.
     *
     * @param helper export configuration
     */
    public JsonArchiveWriter(@NonNull final ExportHelper helper) {
        mHelper = helper;
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException, IOException {

        final LocalDateTime dateSince = mHelper.getLastDone();

        final int booksToExport = ServiceLocator.getInstance().getBookDao()
                                                .countBooksForExport(dateSince);

        if (booksToExport > 0) {
            progressListener.setMaxPos(booksToExport);

            final ExportResults results;

            final Set<RecordType> recordTypes = mHelper.getRecordTypes();
            // If we're doing books, then we MUST do Bookshelves (and Calibre libraries)
            if (recordTypes.contains(RecordType.Books)) {
                recordTypes.add(RecordType.Bookshelves);
                recordTypes.add(RecordType.CalibreLibraries);
            }

            try (OutputStream os = mHelper.createOutputStream(context);
                 Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
                 RecordWriter recordWriter = new JsonRecordWriter(dateSince)) {

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
                recordWriter.writeMetaData(bw, ArchiveMetaData.create(context, VERSION, results));

                // 5. close the envelope
                bw.write("}}");
            }

            // If the backup was a full backup remember that.
            mHelper.setLastDone();

            return results;
        } else {
            return new ExportResults();
        }
    }
}
