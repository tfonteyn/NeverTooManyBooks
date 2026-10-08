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
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.cleaning.Purger;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.CalibreBookJsonKey;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Import books from the <strong>given/single</strong> library.
 * <p>
 * If the user asked for "new and updated books" only,
 * the 'last-sync-date' from the library is used to only fetch books added/modified
 * later than this timestamp FROM THE SERVER.
 * <p>
 * Each remote book is compared to the local book 'last-modified' date to
 * decide to update it or not.
 * <p>
 * Supports custom columns:
 * <ul>
 *     <li>read (boolean)</li>
 *     <li>read_start (datetime)</li>
 *     <li>read_end (datetime)</li>
 *     <li>date_read (datetime) -> read_end</li>
 *     <li>notes (text)</li>
 * </ul>
 *
 * <p>
 * Note we're not taking "books.pubdate"; most metadata downloaded by Calibre contains
 * bad/incorrect dates (at least the ones we've seen)
 */
public class CalibreContentServerReader
        implements DataReader<SyncReaderMetaData, ReaderResults> {

    /** Log tag. */
    private static final String TAG = "CalibreServerReader";

    @NonNull
    private final Updates updateOption;
    /**
     * If we want new-books-only {@link Updates#Skip}
     * or new-books-and-updates {@link Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     */
    @Nullable
    private final LocalDateTime syncDate;

    private final boolean doCovers;

    /** cached localised "eBooks" string. */
    @NonNull
    private final String eBookString;

    private final BookRepository bookRepository;

    private final CalibreLibraryDao calibreLibraryDao;

    @NonNull
    private final CalibreContentServer server;

    /** Which fields and how to process them for existing books. */
    @NonNull
    private final SyncReaderProcessor syncProcessor;

    private final DateParser<LocalDateTime> dateParser;
    private final RealNumberParser realNumberParser;

    /** The physical library from which we'll be importing. */
    @Nullable
    private CalibreLibrary library;
    private ReaderResults results;

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param recordTypes   the record types to accept and read
     * @param syncProcessor synchronization configuration
     * @param syncDate      optional cut-off date
     * @param updateOption  options
     * @param extraArgs     Bundle with reader specific arguments
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    CalibreContentServerReader(@NonNull final Context context,
                               @NonNull final Set<RecordType> recordTypes,
                               @NonNull final SyncReaderProcessor syncProcessor,
                               @Nullable final LocalDateTime syncDate,
                               @NonNull final Updates updateOption,
                               @NonNull final Bundle extraArgs)
            throws CertificateException {

        this.updateOption = updateOption;
        this.syncDate = syncDate;
        this.syncProcessor = syncProcessor;

        doCovers = recordTypes.contains(RecordType.Cover);
        //noinspection deprecation
        library = extraArgs.getParcelable(CalibreContentServer.BKEY_LIBRARY);

        server = new CalibreContentServer.Builder(context).build();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        bookRepository = new BookRepository(context);

        final LocaleList systemLocaleList = serviceLocator.getSystemLocaleList();
        final List<Locale> allLocales = LocaleListUtils.asList(systemLocaleList);
        dateParser = new ISODateParser(allLocales.get(0));
        realNumberParser = new RealNumberParser(allLocales);

        eBookString = context.getString(R.string.book_format_ebook);
    }

    @Override
    @AnyThread
    public void cancel() {
        server.cancel();
    }

    private void readLibraryMetaData()
            throws IOException, JSONException {

        server.readMetaData();
        if (library == null) {
            library = server.getDefaultLibrary();
        }
    }

    @Override
    @WorkerThread
    @NonNull
    public Optional<SyncReaderMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException, IOException {

        try {
            readLibraryMetaData();
        } catch (@NonNull final JSONException e) {
            throw new DataReaderException(e);
        }

        final Bundle args = new Bundle();
        // the requested (or default) library
        args.putParcelable(CalibreContentServer.BKEY_LIBRARY, library);
        // and the full list
        args.putParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST,
                                    new ArrayList<>(server.getLibraries()));

        args.putBoolean(CalibreContentServer.BKEY_PLUGIN_INSTALLED, server.isPluginInstalled());

        return Optional.of(new SyncReaderMetaData(args));
    }

    @Override
    @WorkerThread
    @NonNull
    public ReaderResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException {

        results = new ReaderResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            // Always (re)read the metadata here.
            // Don't assume we still have the same instance as when readMetaData was called.
            readLibraryMetaData();

            if (progressListener.isCancelled()) {
                return results;
            }

            @SuppressWarnings("DataFlowIssue")
            final String libraryStringId = library.getLibraryStringId();

            String lastModifiedQuery = null;
            // If we want new-books-only (Updates.Skip)
            // or new-books-and-updates (Updates.OnlyNewer),
            // we limit the fetch to the sync-date. This speeds up the process.
            if (updateOption == DataReader.Updates.Skip
                || updateOption == DataReader.Updates.OnlyNewer) {

                // last_modified:">2021-01-15", so we do a "minusDays(1)" first
                // Due to rounding, we might get some books we don't need, but that's OK.
                // %22: quote
                // %3E: '>'
                // ==> avoids an extra url encoding step
                if (syncDate != null) {
                    lastModifiedQuery = CalibreBookJsonKey.LAST_MODIFIED + ":%22%3E"
                                        + syncDate.minusDays(1)
                                                  .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        + "%22";
                }
            }

            final BookCoder bookCoder = new BookCoder(dateParser, realNumberParser);

            // First go get ALL the applicable book ids in one go.
            final JSONObject response;
            if (lastModifiedQuery == null) {
                response = server.getBookIds(libraryStringId, Integer.MAX_VALUE, 0);
            } else {
                response = server.search(libraryStringId, Integer.MAX_VALUE, 0,
                                         lastModifiedQuery);
            }

            if (progressListener.isCancelled()) {
                return results;
            }

            if (response.getInt(CalibreContentServer.RESPONSE_TAG_TOTAL_NUM) == 0) {
                // abort; this should never happen... flw
                return results;
            }

            final List<Integer> bookIds =
                    parseBookIds(response, CalibreContentServer.RESPONSE_TAG_BOOK_IDS);
            // Paranoia...
            if (bookIds.isEmpty()) {
                // abort; this should never happen... flw
                return results;
            }

            final int totalNum = bookIds.size();

            // The delta value for updating the progress dialog.
            // It's reset to 0 after a fixed time interval.
            int delta = 0;
            // The currentTimeMillis of the last time we updated the progress dialog.
            long lastUpdate = 0;

            progressListener.setMaxPos(totalNum);

            // we handle book objects in pages, i.e. offset and num
            int offset = 0;
            final int num = server.getBooksPerPullRequest();

            while (offset < totalNum && !progressListener.isCancelled()) {
                final int end = Math.min(offset + num, totalNum);
                final List<Integer> slice = bookIds.subList(offset, end);

                // with the sliced book-ids list, get the full book objects
                final JSONObject bookList = server.getBooksById(libraryStringId, slice);

                final Iterator<String> it = bookList.keys();
                while (it.hasNext() && !progressListener.isCancelled()) {
                    final String key = it.next();
                    final JSONObject calibreBook = bookList.getJSONObject(key);
                    final int calibreBookId = calibreBook.getInt(CalibreBookJsonKey.ID);

                    final Book book = bookCoder.decode(context, library,
                                                       calibreBookId, calibreBook);
                    if (doCovers) {
                        bookCoder.decodeCovers(context, server, calibreBookId, calibreBook, book);
                        results.imagesProcessed++;
                    }

                    // TODO: set the results.images* counters
                    importBook(context, book);

                    results.booksProcessed++;

                    // Show progress
                    delta++;
                    final long now = System.currentTimeMillis();
                    if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                        progressListener.publishProgress(
                                delta, results.createBooksSummaryLine(context));
                        lastUpdate = now;
                        delta = 0;
                    }
                }

                offset += num;
            }

            // always set the sync date!
            library.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));
            calibreLibraryDao.update(library);

        } catch (@NonNull final JSONException e) {
            throw new DataReaderException(e);
        }

        return results;
    }

    /**
     * Parse/extract the list of book ids from the given object key.
     *
     * @param response to parse
     * @param key      to extract
     *
     * @return list
     *
     * @throws JSONException upon any parsing error
     */
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private List<Integer> parseBookIds(@NonNull final JSONObject response,
                                       @NonNull final String key)
            throws JSONException {

        final JSONArray a = response.optJSONArray(key);
        if (a == null || a.isEmpty()) {
            return List.of();
        }

        final List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            ids.add(a.getInt(i));
        }
        return ids;
    }

    /**
     * Process the book, and update the local data if allowed, or insert if not present.
     *
     * @param context     Current context
     * @param calibreBook the book data to import
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    private void importBook(@NonNull final Context context,
                            @NonNull final Book calibreBook)
            throws IOException {
        try {
            final String calibreBookUuid = calibreBook.getString(DBKey.CALIBRE.BOOK_UUID);
            // check if we already have the calibre book in the local database
            final long localBookId = calibreLibraryDao.getBookIdFromCalibreUuid(calibreBookUuid);
            if (localBookId == 0) {
                // We don't have it yet
                insertBook(context, calibreBook);
            } else {
                // We have it; update according to the users choice
                switch (updateOption) {
                    case Overwrite: {
                        final Book book = Book.from(localBookId);
                        updateBook(context, calibreBook, book);
                        break;
                    }
                    case OnlyNewer: {
                        final Book book = Book.from(localBookId);
                        if (isRemoteBookNewer(calibreBook, book)) {
                            updateBook(context, calibreBook, book);

                        } else {
                            results.booksSkipped++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                LoggerFactory.getLogger().d(
                                        TAG, "importBook", updateOption, "Skip",
                                        "calibreBookUuid="
                                        + calibreBook.getString(DBKey.CALIBRE.BOOK_UUID, null),
                                        "book=" + book.getId(),
                                        book.getString(DBKey.TITLE, null));
                            }
                        }
                        break;
                    }
                    case Skip: {
                        results.booksSkipped++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                            LoggerFactory.getLogger().d(
                                    TAG, "importBook", updateOption,
                                    "calibreBookUuid="
                                    + calibreBook.getString(DBKey.CALIBRE.BOOK_UUID, null),
                                    calibreBook.getString(CalibreBookJsonKey.TITLE, null));
                        }
                        break;
                    }
                }
            }
        } catch (@NonNull final SQLiteDoneException | JSONException | StorageException e) {
            // log, but don't fail
            LoggerFactory.getLogger().e(TAG, e);
            results.booksFailed++;
        }
    }

    /**
     * Check if the remote book is newer than the local book.
     *
     * @param calibreBook from the server
     * @param book        local
     *
     * @return flag
     */
    private boolean isRemoteBookNewer(@NonNull final Book calibreBook,
                                      @NonNull final Book book) {

        final Optional<LocalDateTime> localDate = book.getLastModified(dateParser);
        final Optional<LocalDateTime> remoteDate = calibreBook.getLastModified(dateParser);

        // Both should always be present, but paranoia...
        return localDate.isPresent() && remoteDate.isPresent()
               // is the server data newer than our data ?
               && remoteDate.get().isAfter(localDate.get());
    }

    @WorkerThread
    private void updateBook(@NonNull final Context context,
                            @NonNull final Book calibreBook,
                            @NonNull final Book book)
            throws IOException, StorageException {

        final Map<String, SyncField> fieldsWanted = syncProcessor.filter(book);

        // Extract the delta from the calibreBook collection
        final Book delta = syncProcessor.process(context, book.getId(), book, calibreBook,
                                                 fieldsWanted);

        if (delta == null) {
            return;
        }

        bookRepository.update(context, delta, EnumSet.of(
                BookDao.ImportFlag.RunInBatch,
                BookDao.ImportFlag.UseUpdateDateIfPresent));
        results.booksUpdated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
            LoggerFactory.getLogger().d(
                    TAG, "updateBook", updateOption,
                    "calibreBookUuid="
                    + calibreBook.getString(DBKey.CALIBRE.BOOK_UUID, null),
                    "book=" + book.getId(),
                    book.getString(DBKey.TITLE, null));
        }
    }

    private void insertBook(@NonNull final Context context,
                            @NonNull final Book book)
            throws StorageException {

        // it's an eBook - duh!
        book.setFormat(eBookString);

        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf();

        bookRepository.insert(context, book, EnumSet.of(BookDao.ImportFlag.RunInBatch));
        results.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
            LoggerFactory.getLogger().d(
                    TAG, "insertBook", updateOption,
                    "calibreBookUuid="
                    + book.getString(DBKey.CALIBRE.BOOK_UUID, null),
                    "book=" + book.getId(),
                    book.getString(DBKey.TITLE, null));
        }
    }

    @Override
    public void close() {
        new Purger().purge();
    }
}
