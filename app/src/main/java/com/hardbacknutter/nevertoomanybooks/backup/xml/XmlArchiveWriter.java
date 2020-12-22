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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

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
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Hardcoded to only write {@link RecordType#Books} into an XML file.
 */
public class XmlArchiveWriter
        implements ArchiveWriter {

    /**
     * v4 writes out an XML envelope with two elements inside:<br>
     * Books, <strong>followed</strong> by MetaData.
     * <p>
     * v3 used to write metadata,styles,preferences,books (in that order).
     */
    protected static final int VERSION = 4;

    /** The xml root tag. */
    private static final String TAG_APPLICATION_ROOT = "NeverTooManyBooks";

    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    /**
     * Constructor.
     *
     * @param helper export configuration
     */
    public XmlArchiveWriter(@NonNull final ExportHelper helper) {
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

        try (OutputStream os = mHelper.createOutputStream(context);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = new XmlRecordWriter(mHelper.getUtcDateTimeSince())) {

            bw.write(XmlUtils.XML_VERSION_1_0_ENCODING_UTF_8
                     + '<' + TAG_APPLICATION_ROOT + XmlUtils.versionAttr(VERSION) + ">\n");

            final ExportResults results =
                    recordWriter.write(context, bw, EnumSet.of(RecordType.Books),
                                       mHelper.getOptions(), progressListener);

            recordWriter.writeMetaData(bw, ArchiveMetaData.create(context, VERSION, results));

            bw.write("</" + TAG_APPLICATION_ROOT + ">\n");

            return results;
        }
    }
}
