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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Hardcoded to only write {@link RecordType#Books} into a JSON file.
 *
 * <strong>WARNING - EXPERIMENTAL:</strong> format can/will change, splitting of authors etc...
 * <p>
 * <br>
 *     <ul>
 *         <li>Root element {@link JsonCoder#TAG_APPLICATION_ROOT} contains:
 * <ul>
 *      <li>{@link RecordType#AutoDetect}, which contains:
 *          <ul>
 *              <li>{@link RecordType#Books}</li>
 *              <li>future expansion</li>
 *          </ul>
 *      </li>
 *      <li>{@link RecordType#MetaData}</li>
 * </ul>
 *         </li>
 *     </ul>
 */
public class JsonArchiveWriter
        implements ArchiveWriter {

    protected static final int VERSION = 1;

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

    @Override
    public int getVersion() {
        return VERSION;
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        final ExportResults results;

        try (OutputStream os = mHelper.createOutputStream(context);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = new JsonRecordWriter(mHelper.getUtcDateTimeSince())) {

            // manually concat
            // 1. archive envelope
            bw.write("{\"" + JsonCoder.TAG_APPLICATION_ROOT + "\":{");
            // 2. container object
            bw.write("\"" + RecordType.AutoDetect.getName() + "\":");
            // 3. the actual data inside the container
            results = recordWriter.write(context, bw,
                                         EnumSet.of(RecordType.Books),
                                         mHelper.getOptions(),
                                         progressListener);
            // 4. the metadata
            bw.write(",\"" + RecordType.MetaData.getName() + "\":");
            recordWriter.writeMetaData(bw, ArchiveMetaData.create(context, VERSION, results));
            // close the envelope
            bw.write("}}");
        }
        return results;
    }
}
