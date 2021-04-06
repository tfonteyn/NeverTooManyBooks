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
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * <ul>Supports:
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 * <p>
 *  * <strong>LIMITATIONS:</strong> see {@link BookCoder}.
 */
public class CsvRecordWriter
        implements RecordWriter {

    /**
     * The format version of this RecordWriter.
     * <p>
     * Not used; technically we could add some sort of header to the CSV file,
     * but that defeats the CSV format. The {@link CsvRecordReader} is (should be)
     * capable of detecting the format based on the data.
     * (which is why we're moving to the JSON format)
     */
    private static final int VERSION = 1;

    /** Log tag. */
    private static final String TAG = "CsvRecordWriter";

    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public CsvRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        mUtcSinceDateTime = utcSinceDateTime;
    }

    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<RecordType> entries,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        final ExportResults results = new ExportResults();

        if (entries.contains(RecordType.Books)) {

            final boolean collectCoverFilenames = entries.contains(RecordType.Cover);
            final BookCoder bookCoder = new BookCoder(context);

            int delta = 0;
            long lastUpdate = 0;

            try (BookDao bookDao = new BookDao(TAG);
                 Cursor cursor = bookDao.fetchBooksForExport(mUtcSinceDateTime)) {

                writer.write(bookCoder.encodeHeader());
                writer.write("\n");

                while (cursor.moveToNext() && !progressListener.isCancelled()) {

                    final Book book = Book.from(cursor, bookDao);

                    writer.write(bookCoder.encode(book));
                    writer.write("\n");

                    results.addBook(book.getId());

                    if (collectCoverFilenames) {
                        for (int cIdx = 0; cIdx < 2; cIdx++) {
                            final File cover = book.getUuidCoverFile(cIdx);
                            if (cover != null && cover.exists()) {
                                results.addCover(cover.getName());
                            }
                        }
                    }

                    delta++;
                    final long now = System.currentTimeMillis();
                    if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                        progressListener.publishProgress(delta, book.getTitle());
                        lastUpdate = now;
                        delta = 0;
                    }
                }
            }
        }

        return results;
    }
}
