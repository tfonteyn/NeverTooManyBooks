/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.database.Cursor;
import android.os.LocaleList;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.cleaning.Purger;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.CalibreBookJsonKey;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Export <strong>all</strong> libraries currently present on the server.
 * <p>
 * If the user asked for "new and updated books" only,
 * the 'last-sync-date' from the library is used to only fetch books added/modified
 * later than this timestamp FROM THE LOCAL DATABASE.
 * <p>
 * Each local book is compared to the remote book 'last-modified' date to
 * decide to update it or not.
 * <p>
 * We only UPDATE books which exist on the server.
 * We're not pushing new books to the server !
 */
public class CalibreContentServerWriter
        implements DataWriter<SyncWriterResults> {

    /** Log tag. */
    private static final String TAG = "CalibreServerWriter";

    @NonNull
    private final CalibreContentServer server;
    /** Export configuration. */
    private final boolean doCovers;
    /** Export configuration. */
    private final boolean deleteLocalBook;
    /** Export configuration. */
    private final boolean incremental;

    @NonNull
    private final DateParser<LocalDateTime> dateParser;
    private final BookDao bookDao;
    private final CalibreDao calibreDao;
    private final CalibreLibraryDao calibreLibraryDao;
    private final BookCoder bookCoder;
    private SyncWriterResults results;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param recordTypes     the record types to write
     * @param incremental     flag: if {@link CalibreLibrary#getLastSyncDateAsString()} should
     *                        be used to do an incremental write
     * @param deleteLocalBook flag: delete the local book if it no longer exists on the remote
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerWriter(@NonNull final Context context,
                                      @NonNull final Set<RecordType> recordTypes,
                                      final boolean incremental,
                                      final boolean deleteLocalBook)
            throws CertificateException {

        this.doCovers = recordTypes.contains(RecordType.Cover);
        this.incremental = incremental;
        this.deleteLocalBook = deleteLocalBook;

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookDao = serviceLocator.getBookDao();
        calibreDao = serviceLocator.getCalibreDao();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        server = new CalibreContentServer.Builder(context).build();

        dateParser = new ISODateParser(serviceLocator.getSystemLocaleList().get(0));

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final RealNumberParser realNumberParser = new RealNumberParser(allLocales);
        bookCoder = new BookCoder(dateParser, realNumberParser);
    }

    @Override
    @AnyThread
    public void cancel() {
        server.cancel();
    }

    @WorkerThread
    @Override
    @NonNull
    public SyncWriterResults write(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        results = new SyncWriterResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            server.readMetaData();

            for (final CalibreLibrary library : server.getLibraries()) {
                if (progressListener.isCancelled()) {
                    return results;
                }

                @Nullable
                final LocalDateTime dateSince;
                if (incremental) {
                    dateSince = dateParser.parse(library.getLastSyncDateAsString()).orElse(null);
                } else {
                    dateSince = null;
                }

                // sanity check, we only update existing books... no books -> skip library.
                if (library.getTotalBooks() > 0) {
                    syncLibrary(context, library, dateSince, progressListener);
                }
                // always set the sync date!
                library.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));
                calibreLibraryDao.update(library);
            }
        } catch (@NonNull final JSONException e) {
            throw new DataWriterException(e);
        }
        return results;
    }

    private void syncLibrary(@NonNull final Context context,
                             @NonNull final CalibreLibrary library,
                             @Nullable final LocalDateTime dateSince,
                             @NonNull final ProgressListener progressListener)
            throws IOException {

        final String libraryStringId = library.getLibraryStringId();

        // we handle book objects in pages, i.e. offset and num
        int offset = 0;
        final int num = server.getBooksPerPushRequest();

        try (Cursor cursor = bookDao.fetchBooksForExportToCalibre(library.getId(), dateSince)) {
            // The delta value for updating the progress dialog.
            // It's reset to 0 after a fixed time interval.
            int delta = 0;
            // The currentTimeMillis of the last time we updated the progress dialog.
            long lastUpdate = 0;

            final int totalNum = cursor.getCount();

            progressListener.setMaxPos(totalNum);

            while (offset < totalNum && !progressListener.isCancelled()) {
                // Read 'num' number of books from the database
                final int batchSize = Math.min(num, totalNum - offset);
                final Map<String, Book> books = new HashMap<>(batchSize);
                for (int i = 0; i < batchSize && cursor.moveToNext(); i++) {
                    final Book localBook = Book.from(cursor);
                    final String calibreBookUuid = localBook.getString(DBKey.CALIBRE.BOOK_UUID);
                    books.put(calibreBookUuid, localBook);
                }

                // Fetch the matching books from the Calibre server
                final List<String> uuids = new ArrayList<>(books.keySet());
                final JSONObject calibreBooks = server.getBooksByUuid(libraryStringId, uuids);

                // For each pair of local and remote books,
                // check if the local one is newer, and push those to the server.
                // The endpoint can only handle one book at a time.
                for (final String uuid : uuids) {
                    final Book localBook = books.get(uuid);
                    // Paranoia, it should never be null, flw...
                    if (localBook != null) {
                        // it's an int, but getJSONObject wants a string
                        final String calibreBookId = localBook.getString(DBKey.CALIBRE.BOOK_ID);
                        // Don't decode the calibreBook, we only need the last-modified
                        // and the identifier fields.
                        final JSONObject calibreBook = calibreBooks.getJSONObject(calibreBookId);
                        final JSONObject changes = filter(context, library, localBook, calibreBook);
                        if (changes != null) {
                            if (pushChanges(library, localBook, changes)) {
                                results.addBook();
                            }
                        }
                    }
                }

                // Advance the offset for the next batch
                offset += books.size();

                // Show progress, using the title of the first book in the batch
                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    // Paranoia check
                    if (!books.isEmpty()) {
                        final String title = books.values().iterator().next().getTitle();
                        progressListener.publishProgress(delta, title);
                    }
                    lastUpdate = now;
                    delta = 0;
                }
            }
        }
    }

    /**
     * Check if the local book is 'newer' than the remote.
     * If it is, encode the changes and return them; otherwise skip and return {@code null}.
     *
     * @param context     Current context
     * @param library     the library to which the given book belongs
     * @param book        to potentially push to the server
     * @param calibreBook the remote book to check for last-modified datetime
     *
     * @return encoded changes, or {@code null} to skip
     *
     * @throws JSONException upon any parsing error
     * @throws IOException   on generic/other IO failures
     */
    @Nullable
    private JSONObject filter(@NonNull final Context context,
                              @NonNull final CalibreLibrary library,
                              @NonNull final Book book,
                              @NonNull final JSONObject calibreBook)
            throws JSONException, IOException {

        if (isLocalBookNewer(book, calibreBook)) {
            final JSONObject calibreBookIdentifiers =
                    calibreBook.optJSONObject(CalibreBookJsonKey.IDENTIFIERS);

            final JSONObject changes = bookCoder.encode(library, calibreBookIdentifiers, book);
            if (doCovers && bookCoder.encodeCovers(context, book, changes)) {
                results.addCover();
            }
            return changes;
        }
        return null;
    }

    /**
     * Check if the local book is newer than the remote book.
     *
     * @param book        local
     * @param calibreBook from the server
     *
     * @return flag
     */
    private boolean isLocalBookNewer(@NonNull final Book book,
                                     @NonNull final JSONObject calibreBook) {
        final Optional<LocalDateTime> localDate = book.getLastModified(dateParser);
        final Optional<LocalDateTime> remoteDate = bookCoder.decodeLastModifiedDate(calibreBook);

        // Both should always be present, but paranoia...
        return localDate.isPresent() && remoteDate.isPresent()
               // is our data newer then the server data ?
               && localDate.get().isAfter(remoteDate.get());
    }

    /**
     * Send the changes for a single book.
     *
     * @param library the library to which the given book belongs
     * @param book    to send
     * @param delta   fields to update
     *
     * @return {@code true} if successful
     *
     * @throws IOException on generic/other IO failures
     */
    private boolean pushChanges(@NonNull final CalibreLibrary library,
                                @NonNull final Book book,
                                @NonNull final JSONObject delta)
            throws IOException {

        final long bookId = book.getId();
        final int calibreBookId = book.getInt(DBKey.CALIBRE.BOOK_ID);

        try {
            server.pushChanges(library.getLibraryStringId(), calibreBookId, delta);
            return true;

        } catch (@NonNull final HttpNotFoundException e404) {
            // The book no longer exists on the server.
            if (deleteLocalBook) {
                bookDao.delete(book);
            } else {
                // keep the book but remove the calibre data for it
                calibreDao.delete(book);
                book.setCalibreLibrary(null);
            }
            return true;

        } catch (@NonNull final JSONException e) {
            LoggerFactory.getLogger().e(TAG, e, "bookId=" + bookId);
            return false;
        }
    }

    @Override
    public void close() {
        new Purger().purge();
    }
}
