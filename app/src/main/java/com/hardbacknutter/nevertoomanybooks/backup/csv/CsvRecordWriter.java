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

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONException;

/**
 * <ul>Supports:
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 * <p>
 *  * <strong>LIMITATIONS:</strong> see {@link BookCoder}.
 */
public class CsvRecordWriter
        implements RecordWriter {

    @Nullable
    private final LocalDateTime utcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public CsvRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        this.utcSinceDateTime = utcSinceDateTime;
    }

    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<RecordType> recordTypes,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final ExportResults results = new ExportResults();

        try {
            if (recordTypes.contains(RecordType.Books)) {

                final boolean collectCoverFilenames = recordTypes.contains(RecordType.Cover);
                final BookCoder bookCoder = new BookCoder(context);

                int delta = 0;
                long lastUpdate = 0;

                final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
                try (Cursor cursor = bookDao.fetchBooksForExport(utcSinceDateTime)) {

                    writer.write(bookCoder.createHeader());
                    writer.write("\n");

                    while (cursor.moveToNext() && !progressListener.isCancelled()) {

                        final Book book = Book.from(cursor);

                        writer.write(bookCoder.encode(book));
                        writer.write("\n");

                        results.addBook(book.getId());

                        if (collectCoverFilenames) {
                            for (int cIdx = 0; cIdx < 2; cIdx++) {
                                book.getPersistedCoverFile(cIdx).ifPresent(results::addCover);
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
        } catch (@NonNull final JSONException e) {
            throw new DataWriterException(e);
        }

        return results;
    }
}
