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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public class JsonExporter
        implements Exporter {

    /** The format version of this exporter. */
    public static final int VERSION = 1;
    private static final String TAG = "JsonExporter";
    /** Database Access. */
    @NonNull
    private final DAO mDb;

    private final ExportResults mResults = new ExportResults();
    /** export configuration. */
    @ExportHelper.Options
    private final int mOptions;
    private final boolean mCollectCoverFilenames;

    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    private final BookCoder mBookCoder;

    /**
     * Constructor.
     *
     * @param context          Current context
     * @param options          {@link ExportHelper} flags
     * @param utcSinceDateTime (optional) UTC based date to select only books modified or added
     *                         since.
     */
    public JsonExporter(@NonNull final Context context,
                        @ExportHelper.Options final int options,
                        @Nullable final LocalDateTime utcSinceDateTime) {

        mOptions = options;
        mCollectCoverFilenames = (mOptions & ExportHelper.OPTIONS_COVERS) != 0;
        mUtcSinceDateTime = utcSinceDateTime;

        mDb = new DAO(TAG);

        mBookCoder = new BookCoder(StyleDAO.getDefault(context, mDb));
    }

    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final ProgressListener progressListener)
            throws IOException {
        final boolean writeBooks = (mOptions & ExportHelper.OPTIONS_BOOKS) != 0;
        // Sanity check: if we don't do books, return empty results
        if (!writeBooks) {
            return mResults;
        }

        int delta = 0;
        long lastUpdate = 0;

        boolean isFirst = true;

        try (Cursor cursor = mDb.fetchBooksForExport(mUtcSinceDateTime)) {
            // We write the array manually instead of using a JSONArray
            writer.write("[");
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book book = Book.from(cursor, mDb);
                if (!isFirst) {
                    writer.write(",\n");
                } else {
                    isFirst = false;
                }
                // manually written to prevent having to keep the full output in memory
                writer.write(mBookCoder.encode(book).toString());

                mResults.addBook(book.getId());

                if (mCollectCoverFilenames) {
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        final File cover = book.getUuidCoverFile(context, cIdx);
                        if (cover != null && cover.exists()) {
                            mResults.addCover(cover.getName());
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
            writer.write("]");
        }

        return mResults;
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
