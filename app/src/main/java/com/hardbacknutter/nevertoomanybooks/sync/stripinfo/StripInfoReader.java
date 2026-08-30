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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.network.HttpFutureFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A wrapper for {@link UserCollection}.
 * <p>
 * No options for now, just fetch all books in the user collection on the site.
 * This includes:
 * <ul>
 *     <li>owned: imported to the current Bookshelf</li>
 *     <li>wanted: imported to the mapped Wishlist Bookshelf</li>
 *     <li>rated: ignored unless owned/wanted</li>
 *     <li>added a note: ignored unless owned/wanted</li>
 * </ul>
 * ENHANCE: add 1 or 2 more mapped shelves for the last two options above?
 */
public class StripInfoReader
        implements DataReader<SyncReaderMetaData, ReaderResults> {

    private static final String TAG = "StripInfoReader";
    @NonNull
    private final Updates updateOption;
    @NonNull
    private final boolean[] coversForNewBooks;

    @NonNull
    private final StripInfoSearchEngine searchEngine;

    /** Which fields and how to process them for existing books. */
    @NonNull
    private final SyncReaderProcessor syncProcessor;

    @NonNull
    private final BookDao bookDao;
    private final BookRepository bookRepository;

    private final IdentifierValueDao bookIdentifierDao;

    private ReaderResults results;

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param recordTypes   the record types to accept and read
     * @param syncProcessor synchronization configuration
     * @param updateOption  options
     */
    public StripInfoReader(@NonNull final Context context,
                           @NonNull final Set<RecordType> recordTypes,
                           @NonNull final SyncReaderProcessor syncProcessor,
                           @NonNull final Updates updateOption) {

        this.updateOption = updateOption;
        this.syncProcessor = syncProcessor;

        final boolean doCovers = recordTypes.contains(RecordType.Cover);
        coversForNewBooks = new boolean[]{doCovers, doCovers, doCovers, doCovers};

        // create a new instance just for our own use
        searchEngine = EngineId.StripInfoBe.createSearchEngine(context);

        final ServiceLocator locator = ServiceLocator.getInstance();
        bookDao = locator.getBookDao();
        bookIdentifierDao = locator.getBookIdentifierDao();

        bookRepository = new BookRepository(context);
    }

    @Override
    @WorkerThread
    @NonNull
    public ReaderResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   CredentialsException,
                   StorageException,
                   IOException {

        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        searchEngine.ping();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));

        searchEngine.setCaller(progressListener);

        final HttpFutureFactory httpCallFactory = searchEngine.getHttpFutureFactory();

        final SiteAuthModule siteAuthModule = new StripInfoAuth(httpCallFactory);
        final String userId = siteAuthModule.login(context);
        searchEngine.setAuthModule(siteAuthModule);

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        final UserCollection uc = new UserCollection(context, httpCallFactory, userId,
                                                     new BookshelfMapper());

        results = new ReaderResults();

        int pageNr = 0;
        try {
            while (uc.getMaxPages() > pageNr
                   && !searchEngine.isCancelled()) {

                final List<Book> page = uc.fetchPage(context, ++pageNr, progressListener);
                if (!page.isEmpty()) {
                    // We're committing by page.
                    Synchronizer.SyncLock txLock = null;
                    try {
                        txLock = db.beginTransaction(true);

                        for (final Book siBook : page) {
                            if (!searchEngine.isCancelled()) {
                                importBook(context, siBook);
                                results.booksProcessed++;

                                // Due to the network access, we're not adding
                                // any additional interval/delay for each message
                                progressListener.publishProgress(
                                        1, results.createBooksSummaryLine(context));
                            }
                        }

                        db.setTransactionSuccessful();
                    } finally {
                        if (txLock != null) {
                            db.endTransaction(txLock);
                        }
                    }
                }
            }
        } catch (@NonNull final CredentialsException | SearchException e) {
            throw new DataReaderException(e);
        }

        // always set the sync date!
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putString(StripInfoHandler.PK_LAST_SYNC,
                                 LocalDateTime.now(ZoneOffset.UTC).format(
                                         DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                      .apply();

        return results;
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (searchEngine) {
            searchEngine.cancel();
        }
    }

    /**
     * Process the book, and update the local data if allowed, or insert if not present.
     *
     * @param context Current context
     * @param siBook  the book data to import
     *
     * @throws SearchException          on generic exceptions (wrapped) during search
     * @throws CredentialsException     on authentication/login failures
     * @throws StorageException         The covers directory is not available
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    private void importBook(@NonNull final Context context,
                            @NonNull final Book siBook)
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   IOException {

        final String externalId = siBook.requireIdentifierValue(Identifier.SID_STRIP_INFO);

        // lookup locally using the externalId.
        final Optional<Long> oBookId = bookIdentifierDao
                .findIdentifierOwnerId(Identifier.SID_STRIP_INFO, externalId);
        try {
            // check if we already have the StripInfo book in the local database
            if (oBookId.isPresent()) {
                try (Cursor cursor = bookDao.fetchById(oBookId.get())) {
                    if (cursor.moveToFirst()) {
                        // handle the update according to the users choice
                        switch (updateOption) {
                            case Overwrite: {
                                final Book book = Book.from(cursor);
                                final Book delta = createUpdateDelta(context, siBook, book);
                                if (delta != null) {
                                    updateBook(context, externalId, book, delta);
                                }
                                break;
                            }
                            case OnlyNewer: {
                                // The site does not provide a last-updated date.
                                // This option is disabled in SyncServer#StripInfo class
                                break;
                            }
                            case Skip: {
                                skipBook(externalId);
                                break;
                            }
                        }
                    }
                }
            } else {
                // It's a new book. Download the full data set from the server.
                final BookSearchCriteria criteria = new BookSearchCriteria();
                criteria.setSids(Map.of(EngineId.StripInfoBe, externalId));
                criteria.setFetchCovers(coversForNewBooks);
                final Book book = searchEngine.searchByExternalId(context, criteria);
                CoverFileSpecArray.process(book);

                insertBook(context, book);
            }
        } catch (@NonNull final DaoWriteException | SQLiteDoneException | JSONException e) {
            // log, but don't fail
            LoggerFactory.getLogger().e(TAG, e);
            results.booksFailed++;
        }
    }

    @WorkerThread
    @Nullable
    private Book createUpdateDelta(@NonNull final Context context,
                                   @NonNull final Book siBook,
                                   @NonNull final Book book)
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   IOException {

        final Map<String, SyncField> fieldsWanted = syncProcessor.filter(book);
        final boolean[] coversWanted = {
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0]),
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])
        };

        final String externalId = siBook.requireIdentifierValue(Identifier.SID_STRIP_INFO);

        final Book dataToMerge;
        if (coversWanted[1]) {
            // The back cover is *not* available on the collection page.
            // Download the full data set from the server.
            // The siBook data is superseded by this new data.
            final BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setSids(Map.of(EngineId.StripInfoBe, externalId));
            criteria.setFetchCovers(coversWanted);
            dataToMerge = searchEngine.searchByExternalId(context, criteria);
            CoverFileSpecArray.process(dataToMerge);
        } else {
            // We have all we need in the incoming siBook
            dataToMerge = siBook;
            // but while we don't need the back cover, we might need the front cover
            // which *is* available on the collection page.
            // Try to get it, and stick it straight into the BKEY_TMP_FILE_SPEC[0]
            if (coversWanted[0]) {
                final String url = dataToMerge.getString(UserCollection.BKEY_FRONT_COVER_URL, null);
                if (url != null && !url.isEmpty()) {
                    searchEngine.getHttpCallFactory()
                                .saveImage(url, null, externalId, 0, null)
                                .ifPresent(fileSpec -> dataToMerge
                                        .putString(Book.BKEY_TMP_FILE_SPEC[0], fileSpec));
                }
            }
        }

        // Extract the delta from the dataToMerge
        return syncProcessor.process(context, book.getId(), book, dataToMerge, fieldsWanted);
    }

    private void insertBook(@NonNull final Context context,
                            @NonNull final Book book)
            throws StorageException,
                   DaoWriteException {

        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf();

        final String preImportUuid = book.getString(DBKey.BOOK_UUID, null);
        final long preImportId = book.getId();

        final long id = bookRepository.insert(context, book,
                                              EnumSet.of(BookDao.BookFlag.RunInBatch));
        results.bookCreated(id);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "insertBook",
                                        "preImport=" + preImportId, preImportUuid,
                                        "postImport=" + book.getId(),
                                        book.getString(DBKey.BOOK_UUID, null),
                                        book.getString(DBKey.TITLE, null));
        }
    }

    private void updateBook(@NonNull final Context context,
                            @NonNull final String externalId,
                            @NonNull final Book book,
                            @NonNull final Book delta)
            throws StorageException, DaoWriteException {
        bookRepository.update(context, delta,
                              EnumSet.of(BookDao.BookFlag.RunInBatch,
                                         BookDao.BookFlag.UseUpdateDateIfPresent));
        results.bookUpdated(book.getId());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "updateBook", updateOption,
                                        "externalId=" + externalId,
                                        "book=" + book.getId(),
                                        book.getString(DBKey.TITLE, null));
        }
    }

    private void skipBook(@NonNull final String externalId) {
        results.booksSkipped++;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "processPage",
                                        "externalId=" + externalId);
        }
    }
}
