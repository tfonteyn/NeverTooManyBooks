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
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * <ul>Supports:
 *      <li>{@link ArchiveWriterRecord.Type#Styles}</li>
 *      <li>{@link ArchiveWriterRecord.Type#Preferences}</li>
 *      <li>{@link ArchiveWriterRecord.Type#Books}</li>
 * </ul>
 */
public class JsonRecordWriter
        implements RecordWriter {

    /** The format version of this exporter. */
    public static final int VERSION = 1;

    /** Log tag. */
    private static final String TAG = "JsonRecordWriter";

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public JsonRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        mUtcSinceDateTime = utcSinceDateTime;
        mDb = new DAO(TAG);
    }

    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<ArchiveWriterRecord.Type> entry,
                               @ExportHelper.Options final int options,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        final ExportResults results = new ExportResults();
        final JSONObject output = new JSONObject();

        try {
            if (entry.contains(ArchiveWriterRecord.Type.Books)) {
                final boolean collectCoverFilenames = entry.contains(
                        ArchiveWriterRecord.Type.Cover);

                final JsonCoder<Book> coder = new BookCoder(StyleDAO.getDefault(context, mDb));

                int delta = 0;
                long lastUpdate = 0;

                final JSONArray bookArray = new JSONArray();
                try (Cursor cursor = mDb.fetchBooksForExport(mUtcSinceDateTime)) {
                    //TEST: performance between manual loop(book/encode) versus list / encode:
                    // We could read all books from the cursor into a list,
                    // and then encode in one go.
                    // Memory:  now: JSONArray + 1 Book at a time / versus JSONArray AND all Books.
                    while (cursor.moveToNext() && !progressListener.isCancelled()) {
                        final Book book = Book.from(cursor, mDb);
                        bookArray.put(coder.encode(book));
                        results.addBook(book.getId());

                        if (collectCoverFilenames) {
                            for (int cIdx = 0; cIdx < 2; cIdx++) {
                                final File cover = book.getUuidCoverFile(context, cIdx);
                                if (cover != null && cover.exists()) {
                                    results.addCover(cover.getName());
                                }
                            }
                        }

                        delta++;
                        final long now = System.currentTimeMillis();
                        if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                            progressListener.publishProgressStep(delta, book.getTitle());
                            lastUpdate = now;
                            delta = 0;
                        }
                    }
                }

                if (bookArray.length() > 0) {
                    output.put(JsonTags.BOOK_LIST, bookArray);
                }
            }
        } catch (@NonNull final JSONException e) {
            throw new IOException(e);
        }

        // Write the complete json output
        if (output.length() > 0) {
            writer.write(output.toString());
        }
        return results;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public void close() {
        mDb.close();
    }
}
