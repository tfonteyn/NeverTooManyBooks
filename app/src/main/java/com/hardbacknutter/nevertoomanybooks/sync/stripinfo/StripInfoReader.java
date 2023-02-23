/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderHelper;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONException;

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

    @SuppressWarnings("WeakerAccess")
    public static final String SYNC_PROCESSOR_PREFIX = EngineId.StripInfoBe.getPreferenceKey()
                                                       + ".fields.update.";
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
    /** cached localized progress string. */
    @NonNull
    private final String booksString;
    @NonNull
    private final BookDao bookDao;

    private ReaderResults results;

    public StripInfoReader(@NonNull final Context context,
                           @NonNull final SyncReaderHelper helper) {

        updateOption = helper.getUpdateOption();

        final boolean doCovers = helper.getRecordTypes().contains(RecordType.Cover);
        coversForNewBooks = new boolean[]{doCovers, doCovers};

        // Get either the custom passed-in, or the builtin default.
        final SyncReaderProcessor sp = helper.getSyncProcessor();
        syncProcessor = sp != null ? sp : getDefaultSyncProcessor(context);

        bookDao = ServiceLocator.getInstance().getBookDao();

        // create a new instance just for our own use
        searchEngine = (StripInfoSearchEngine) EngineId.StripInfoBe.createSearchEngine();

        booksString = context.getString(R.string.lbl_books);
    }

    /**
     * Get the default SyncProcessor. The simple fields are CopyIfBlank
     * <p>
     * //ENHANCE: pass an optional user configurable copy into the {@link SyncReaderHelper}
     *
     * @param context Current context
     *
     * @return a CopyIfBlank SyncProcessor
     */
    @NonNull
    private static SyncReaderProcessor getDefaultSyncProcessor(@NonNull final Context context) {
        final SortedMap<String, String[]> map = new TreeMap<>();
        map.put(context.getString(R.string.site_stripinfo_be), new String[]{DBKey.SID_STRIP_INFO});

        map.put(context.getString(R.string.lbl_cover_front),
                new String[]{FieldVisibility.COVER[0]});
        map.put(context.getString(R.string.lbl_cover_back),
                new String[]{FieldVisibility.COVER[1]});

        // the wishlist
        map.put(context.getString(R.string.lbl_bookshelves),
                new String[]{DBKey.FK_BOOKSHELF, Book.BKEY_BOOKSHELF_LIST});
        map.put(context.getString(R.string.lbl_date_acquired),
                new String[]{DBKey.DATE_ACQUIRED});
        map.put(context.getString(R.string.lbl_location),
                new String[]{DBKey.LOCATION});
        map.put(context.getString(R.string.lbl_personal_notes),
                new String[]{DBKey.PERSONAL_NOTES});
        map.put(context.getString(R.string.lbl_rating),
                new String[]{DBKey.RATING});
        map.put(context.getString(R.string.lbl_read),
                new String[]{DBKey.READ__BOOL});
        map.put(context.getString(R.string.lbl_price_paid),
                new String[]{DBKey.PRICE_PAID});

        // The site specific keys
        map.put(context.getString(R.string.lbl_owned),
                new String[]{DBKey.STRIP_INFO_OWNED});
        map.put(context.getString(R.string.bookshelf_my_wishlist),
                new String[]{DBKey.STRIP_INFO_WANTED});
        map.put(context.getString(R.string.lbl_number),
                new String[]{DBKey.STRIP_INFO_AMOUNT});
        map.put(context.getString(R.string.site_stripinfo_be),
                new String[]{DBKey.STRIP_INFO_COLL_ID});


        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(context, SYNC_PROCESSOR_PREFIX);

        // add the sorted fields
        map.forEach(builder::add);

        builder.addRelatedField(FieldVisibility.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(FieldVisibility.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
               .addRelatedField(DBKey.PRICE_PAID, DBKey.PRICE_PAID_CURRENCY);

        return builder.build();
    }

    @NonNull
    @Override
    public ReaderResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   CredentialsException,
                   StorageException,
                   IOException {

        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable(context)) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        searchEngine.ping(context);

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));

        searchEngine.setCaller(progressListener);

        final StripInfoAuth loginHelper = new StripInfoAuth(context);
        final String userId = loginHelper.login();

        searchEngine.setLoginHelper(loginHelper);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SynchronizedDb db = serviceLocator.getDb();

        final UserCollection uc = new UserCollection(context, searchEngine, userId,
                                                     new BookshelfMapper());

        results = new ReaderResults();

        int pageNr = 0;
        try {
            // haven't started yet, or there are more pages.
            while ((pageNr == 0 || uc.getMaxPages() > pageNr)
                   && !searchEngine.isCancelled()) {

                pageNr++;
                final Optional<List<Book>> page = uc.fetchPage(context, pageNr,
                                                               progressListener);
                if (page.isPresent()) {
                    // We're committing by page.
                    Synchronizer.SyncLock txLock = null;
                    try {
                        txLock = db.beginTransaction(true);

                        processPage(context, page.get(), progressListener);

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
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(StripInfoAuth.PK_LAST_SYNC, LocalDateTime.now(ZoneOffset.UTC).format(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .apply();

        return results;
    }

    @Override
    public void cancel() {
        synchronized (searchEngine) {
            searchEngine.cancel();
        }
    }

    private void processPage(@NonNull final Context context,
                             @NonNull final List<Book> page,
                             @NonNull final ProgressListener progressListener)
            throws StorageException,
                   SearchException,
                   CredentialsException {

        final String progressMessage =
                context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);

        for (final Book colBook : page) {
            if (!searchEngine.isCancelled()) {
                final long externalId = colBook.getLong(DBKey.SID_STRIP_INFO);
                // lookup locally using the externalId column.
                try (Cursor cursor = bookDao.fetchByKey(DBKey.SID_STRIP_INFO,
                                                        String.valueOf(externalId))) {
                    // check if we already have the StripInfo book in the local database
                    if (cursor.moveToFirst()) {
                        // yes, we do - handle the update according to the users choice
                        switch (updateOption) {
                            case Overwrite: {
                                final Book book = Book.from(cursor);
                                updateBook(context, externalId, colBook, book);
                                break;
                            }
                            case OnlyNewer: {
                                // The site does not provide a last-updated date.
                                // This option is disabled in SyncServer#StripInfo class
                                break;
                            }
                            case Skip: {
                                results.booksSkipped++;
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
                                    Log.d(TAG, "externalId=" + externalId
                                               + "|" + updateOption);
                                }
                                break;
                            }
                        }
                    } else {
                        insertBook(context, externalId);
                    }

                    results.booksProcessed++;

                } catch (@NonNull final DaoWriteException | SQLiteDoneException | JSONException e) {
                    // log, but don't fail
                    LoggerFactory.getLogger().e(TAG, e);
                    results.booksFailed++;
                }

                final String msg = String.format(progressMessage,
                                                 booksString,
                                                 results.booksCreated,
                                                 results.booksUpdated,
                                                 results.booksSkipped);
                progressListener.publishProgress(1, msg);
            }
        }
    }

    private void updateBook(@NonNull final Context context,
                            final long externalId,
                            @NonNull final Book colBook,
                            @NonNull final Book book)
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   DaoWriteException {

        // The delta values we'll be updating
        final Book delta;

        final Map<String, SyncField> fieldsWanted = syncProcessor.filter(book);
        final boolean[] coversWanted = {
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0]),
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])};

        final Book dataToMerge;
        if (coversWanted[1]) {
            // The back cover is not available on the collection page
            // Do a full download.
            dataToMerge = searchEngine
                    .searchByExternalId(context, String.valueOf(externalId), coversWanted);
        } else {
            dataToMerge = colBook;
            // we don't need the back cover, but might need the front cover
            if (coversWanted[0]) {
                downloadFrontCover(externalId, dataToMerge);
            }
        }

        // Extract the delta from the dataToMerge
        delta = syncProcessor.process(context, book.getId(), book, fieldsWanted, dataToMerge);

        if (delta != null) {
            bookDao.update(context, delta, Set.of(BookDao.BookFlag.RunInBatch,
                                                  BookDao.BookFlag.UseUpdateDateIfPresent));
            results.booksUpdated++;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
                Log.d(TAG, "externalId=" + externalId
                           + "|" + updateOption
                           + "|UPDATE|book=" + book.getId() + "|" + book.getTitle());
            }
        }
    }

    private void insertBook(@NonNull final Context context,
                            final long externalId)
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   DaoWriteException {
        // It's a new book; download it from the server and insert it into the db
        final Book book = searchEngine
                .searchByExternalId(context, String.valueOf(externalId), coversForNewBooks);

        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf(context);

        bookDao.insert(context, book, Set.of(BookDao.BookFlag.RunInBatch));
        results.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
            Log.d(TAG, "externalId=" + externalId
                       + "|" + updateOption
                       + "|INSERT|book=" + book.getId() + "|" + book.getTitle());
        }
    }

    @WorkerThread
    private void downloadFrontCover(@IntRange(from = 1) final long externalId,
                                    @NonNull final Book cData)
            throws StorageException {
        final String url = cData.getString(UserCollection.BKEY_FRONT_COVER_URL, null);
        if (url != null && !url.isEmpty()) {
            final String fileSpec = searchEngine
                    .saveImage(url, String.valueOf(externalId), 0, null);
            if (fileSpec != null) {
                cData.putString(Book.BKEY_TMP_FILE_SPEC[0], fileSpec);
            }
        }
    }
}
