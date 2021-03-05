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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.backup.Backup;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Hardcoded to only write {@link RecordType#Books} into an CSV file.
 *
 * <strong>LIMITATIONS:</strong> see {@link BookCoder}.
 */
public class CsvArchiveWriter
        implements ArchiveWriter {

    private static final String TAG = "CsvArchiveWriter";

    protected static final int VERSION = 1;

    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    /**
     * Constructor.
     *
     * @param helper export configuration
     */
    public CsvArchiveWriter(@NonNull final ExportHelper helper) {
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
            throws GeneralParsingException, IOException {

        final LocalDateTime dateSince;
        if (mHelper.isIncremental()) {
            dateSince = Backup.getLastFullExportDate(context, ArchiveEncoding.Csv);
        } else {
            dateSince = null;
        }

        final int booksToExport;
        try (BookDao bookDao = new BookDao(TAG)) {
            booksToExport = bookDao.countBooksForExport(dateSince);
        }

        if (booksToExport > 0) {
            progressListener.setMaxPos(booksToExport);

            final ExportResults results;

            try (OutputStream os = mHelper.createOutputStream(context);
                 Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
                 RecordWriter recordWriter = new CsvRecordWriter(dateSince)) {

                results = recordWriter
                        .write(context, bw, EnumSet.of(RecordType.Books), progressListener);
            }

            // If the backup was a full backup remember that.
            if (!mHelper.isIncremental()) {
                Backup.setLastFullExportDate(context, ArchiveEncoding.Csv,
                                             LocalDateTime.now(ZoneOffset.UTC));
            }
            return results;
        } else {
            return new ExportResults();
        }
    }
}
