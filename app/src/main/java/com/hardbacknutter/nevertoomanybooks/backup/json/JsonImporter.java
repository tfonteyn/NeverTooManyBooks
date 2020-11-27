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
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

public class JsonImporter
        implements Importer {

    private static final String TAG = "JsonImporter";

    /** Buffer for the Reader. */
    private static final int BUFFER_SIZE = 65535;

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final Locale mUserLocale;

    /** import configuration. */
    private final boolean mSyncBooks;
    private final boolean mOverwriteBooks;

    private final BookCoder mBookCoder;

    private final ImportResults mResults = new ImportResults();

    public JsonImporter(@NonNull final Context context,
                        @ImportHelper.Options final int options) {

        mSyncBooks = (options & ImportHelper.OPTIONS_UPDATED_BOOKS_SYNC) != 0;
        mOverwriteBooks = (options & ImportHelper.OPTIONS_UPDATED_BOOKS) != 0;

        mDb = new DAO(TAG);

        mUserLocale = AppLocale.getInstance().getUserLocale(context);

        mBookCoder = new BookCoder(StyleDAO.getDefault(context, mDb));
    }

    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ReaderEntity entity,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        // we only support books, return empty results
        if (entity.getType() != ArchiveContainerEntry.BooksJson) {
            return mResults;
        }

        // Don't close this stream!
        final InputStream is = entity.getInputStream();
        final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

        // We read the whole file/list into memory.
        final StringBuilder sb = new StringBuilder();

        int counter = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
            counter++;
        }
        if (sb.length() == 0) {
            return mResults;
        }

        // not perfect, but good enough
        if (progressListener.getMaxPos() < counter) {
            progressListener.setMaxPos(counter);
        }

        try {
            final JSONArray books = new JSONArray(sb.toString());
            for (int i = 0; i < books.length(); i++) {

                Book book = mBookCoder.decode(books.getJSONObject(i));

                Log.d(TAG, "book=" + book);

            }

        } catch (@NonNull final JSONException e) {
            throw new ImportException(context.getString(R.string.error_import_failed), e);
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "read|mResults=" + mResults);
        }
        return mResults;
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}
