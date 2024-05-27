/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterHelper;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.util.logger.LoggerFactory;

public class StripInfoWriter
        implements DataWriter<SyncWriterResults> {

    /** Log tag. */
    private static final String TAG = "StripInfoWriter";

    /** Export configuration. */
    @NonNull
    private final SyncWriterHelper syncWriterHelper;

    private final boolean deleteLocalBook;

    @NonNull
    private final CollectionFormUploader collectionForm;
    @NonNull
    private final DateParser dateParser;
    @SuppressWarnings("FieldCanBeLocal")
    private SyncWriterResults results;

    /**
     * Constructor.
     *
     * @param context          Current context
     * @param syncWriterHelper export configuration
     * @param systemLocale     to use for ISO date parsing
     */
    public StripInfoWriter(@NonNull final Context context,
                           @NonNull final SyncWriterHelper syncWriterHelper,
                           @NonNull final Locale systemLocale) {
        this.syncWriterHelper = syncWriterHelper;
        this.dateParser = new ISODateParser(systemLocale);
        collectionForm = new CollectionFormUploader(context);
        deleteLocalBook = this.syncWriterHelper.isDeleteLocalBooks();
    }

    @Override
    public void cancel() {
        collectionForm.cancel();
    }

    @NonNull
    @Override
    public SyncWriterResults write(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws IOException {

        results = new SyncWriterResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        @Nullable
        final LocalDateTime dateSince;
        if (syncWriterHelper.isIncremental()) {
            dateSince = dateParser.parse(global.getString(StripInfoAuth.PK_LAST_SYNC, null))
                                  .orElse(null);
        } else {
            dateSince = null;
        }

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final BookDao bookDao = serviceLocator.getBookDao();
        final StripInfoDao stripInfoDao = serviceLocator.getStripInfoDao();
        try (Cursor cursor = bookDao.fetchBooksForExportToStripInfo(dateSince)) {
            int delta = 0;
            long lastUpdate = 0;
            progressListener.setMaxPos(cursor.getCount());

            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book book = Book.from(cursor);
                try {
                    collectionForm.send(book);
                    results.addBook(book.getId());

                } catch (@NonNull final HttpNotFoundException e404) {
                    // The book no longer exists on the server.
                    if (deleteLocalBook) {
                        bookDao.delete(book);
                    } else {
                        // keep the book itself, but remove the stripInfo data for it
                        stripInfoDao.delete(book);
                        collectionForm.removeFields(book);
                    }
                } catch (@NonNull final JSONException e) {
                    // ignore, just move on to the next book
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "bookId=" + book.getId());
                } catch (@NonNull final StorageException ignore) {
                    // ignore, can't happen here
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

        // always set the sync date!
        global.edit()
              .putString(StripInfoAuth.PK_LAST_SYNC, LocalDateTime.now(ZoneOffset.UTC).format(
                      DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .apply();
        return results;
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }
}
