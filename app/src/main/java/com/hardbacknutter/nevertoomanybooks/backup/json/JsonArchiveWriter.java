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

import java.io.IOException;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * EXPERIMENTAL: only meant to be run from a test.
 */
public class JsonArchiveWriter
        implements ArchiveWriter {

    protected static final int VERSION = 1;

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

        // This is a flat json, books-only file,so we *only* pass in OPTIONS_BOOKS.
        // and disregard whatever was set in the helper.
        try (RecordWriter recordWriter = new JsonRecordWriter(mHelper.getUtcDateTimeSince())) {
            return recordWriter.write(context, mHelper.getTempOutputFile(context),
                                      EnumSet.of(ArchiveWriterRecord.Type.Books),
                                      mHelper.getOptions(),
                                      progressListener);
        }
    }
}
