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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;
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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReader;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderConfig;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;
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
        implements SyncReader {

    @SuppressWarnings("WeakerAccess")
    public static final String SYNC_PROCESSOR_PREFIX = StripInfoAuth.PREF_KEY + ".fields.update.";
    private static final String TAG = "StripInfoReader";
    @NonNull
    private final SyncReaderConfig.Updates mUpdateOption;

    private final boolean[] mCoversForNewBooks;
    @NonNull
    private final StripInfoSearchEngine mSearchEngine;
    /** Which fields and how to process them for existing books. */
    @NonNull
    private final SyncReaderProcessor mSyncProcessor;
    /** cached localized progress string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final BookDao mBookDao;

    private ReaderResults mResults;

    public StripInfoReader(@NonNull final Context context,
                           @NonNull final SyncReaderConfig config) {

        mUpdateOption = config.getUpdateOption();

        final boolean doCovers = config.getImportEntries().contains(RecordType.Cover);
        mCoversForNewBooks = new boolean[]{doCovers, doCovers};

        // Get either the custom passed-in, or the builtin default.
        final SyncReaderProcessor sp = config.getSyncProcessor();
        mSyncProcessor = sp != null ? sp : getDefaultSyncProcessor();

        mBookDao = ServiceLocator.getInstance().getBookDao();

        mSearchEngine = (StripInfoSearchEngine) SearchEngineRegistry
                .getInstance().createSearchEngine(SearchSites.STRIP_INFO_BE);

        mBooksString = context.getString(R.string.lbl_books);
    }

    /**
     * Get the default SyncProcessor. The simple fields are CopyIfBlank
     * <p>
     * //ENHANCE: pass an optional user configurable copy into the {@link SyncReaderConfig}
     *
     * @return a CopyIfBlank SyncProcessor
     */
    @NonNull
    public static SyncReaderProcessor getDefaultSyncProcessor() {
        return new SyncReaderProcessor.Builder(SYNC_PROCESSOR_PREFIX)
                .add(R.string.site_stripinfo_be, DBKey.SID_STRIP_INFO)

                .add(R.string.lbl_cover_front, DBKey.COVER_IS_USED[0])
                .addRelatedField(DBKey.COVER_IS_USED[0], Book.BKEY_TMP_FILE_SPEC[0])
                .add(R.string.lbl_cover_back, DBKey.COVER_IS_USED[1])
                .addRelatedField(DBKey.COVER_IS_USED[1], Book.BKEY_TMP_FILE_SPEC[1])

                // the wishlist
                .addList(R.string.lbl_bookshelves, DBKey.FK_BOOKSHELF, Book.BKEY_BOOKSHELF_LIST)

                .add(R.string.lbl_date_acquired, DBKey.DATE_ACQUIRED)
                .add(R.string.lbl_location, DBKey.KEY_LOCATION)
                .add(R.string.lbl_personal_notes, DBKey.KEY_PRIVATE_NOTES)
                .add(R.string.lbl_rating, DBKey.KEY_RATING)
                .add(R.string.lbl_read, DBKey.BOOL_READ)

                .add(R.string.lbl_price_paid, DBKey.PRICE_PAID)
                .addRelatedField(DBKey.PRICE_PAID_CURRENCY, DBKey.PRICE_PAID)

                // The site specific keys
                .add(R.string.lbl_owned, DBKey.BOOL_STRIP_INFO_OWNED)
                .add(R.string.lbl_wishlist, DBKey.BOOL_STRIP_INFO_WANTED)
                .add(R.string.lbl_number, DBKey.KEY_STRIP_INFO_AMOUNT)
                .add(R.string.site_stripinfo_be, DBKey.KEY_STRIP_INFO_COLL_ID)

                .build();
    }

    @NonNull
    @Override
    public ReaderResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException,
                   ImportException,
                   CredentialsException,
                   DiskFullException,
                   CoverStorageException {

        // Got internet?
        if (!NetworkUtils.isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        NetworkUtils.ping(mSearchEngine.getSiteUrl());

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));

        mSearchEngine.setCaller(progressListener);

        final StripInfoAuth loginHelper = new StripInfoAuth(mSearchEngine.getSiteUrl());
        final String userId = loginHelper.login();
        mSearchEngine.setLoginHelper(loginHelper);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SynchronizedDb db = serviceLocator.getDb();

        final UserCollection uc = new UserCollection(context, mSearchEngine, userId,
                                                     new Bookshelfmapper());

        mResults = new ReaderResults();

        try {
            while (uc.hasMore() && !progressListener.isCancelled()) {
                final List<Bundle> page = uc.fetchPage(context, progressListener);
                if (page != null && !page.isEmpty()) {
                    // We're committing by page.
                    Synchronizer.SyncLock txLock = null;
                    try {
                        txLock = db.beginTransaction(true);

                        processPage(context, page, progressListener);

                        db.setTransactionSuccessful();
                    } finally {
                        if (txLock != null) {
                            db.endTransaction(txLock);
                        }
                    }
                }
            }
        } catch (@NonNull final SearchException e) {
            throw new ImportException(e);
        }

        // always set the sync date!
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        global.edit()
              .putString(StripInfoAuth.PK_LAST_SYNC, LocalDateTime.now(ZoneOffset.UTC).format(
                      DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .apply();
        return mResults;
    }

    private void processPage(@NonNull final Context context,
                             @NonNull final List<Bundle> page,
                             @NonNull final ProgressListener progressListener)
            throws DiskFullException,
                   CoverStorageException,
                   SearchException,
                   CredentialsException {

        final String progressMessage =
                context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);

        for (final Bundle colBook : page) {
            if (!progressListener.isCancelled()) {
                final long externalId = colBook.getLong(DBKey.SID_STRIP_INFO);
                // lookup locally using the externalId column.
                try (Cursor cursor = mBookDao.fetchByKey(DBKey.SID_STRIP_INFO,
                                                         String.valueOf(externalId))) {
                    // check if we already have the StripInfo book in the local database
                    if (cursor.moveToFirst()) {
                        // yes, we do - handle the update according to the users choice
                        switch (mUpdateOption) {
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
                                mResults.booksSkipped++;
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
                                    Log.d(TAG, "externalId=" + externalId
                                               + "|" + mUpdateOption);
                                }
                                break;
                            }
                        }
                    } else {
                        insertBook(context, externalId);
                    }

                    mResults.booksProcessed++;

                } catch (@NonNull final DaoWriteException | SQLiteDoneException | JSONException e) {
                    // log, but don't fail
                    Logger.error(TAG, e);
                    mResults.booksFailed++;
                }

                final String msg = String.format(progressMessage,
                                                 mBooksString,
                                                 mResults.booksCreated,
                                                 mResults.booksUpdated,
                                                 mResults.booksSkipped);
                progressListener.publishProgress(1, msg);
            }
        }
    }

    private void updateBook(@NonNull final Context context,
                            final long externalId,
                            @NonNull final Bundle colBook,
                            @NonNull final Book book)
            throws DiskFullException,
                   CoverStorageException,
                   SearchException,
                   CredentialsException,
                   DaoWriteException {

        // The delta values we'll be updating
        final Book delta;

        final Map<String, SyncField> fieldsWanted = mSyncProcessor.filter(book);
        final boolean[] coversWanted = {
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0]),
                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])};

        if (coversWanted[1]) {
            // The back cover is not available on the collection page
            // Do a full download.
            final Bundle bookData = mSearchEngine
                    .searchByExternalId(context, String.valueOf(externalId), coversWanted);

            // Extract the delta from the *bookData*
            delta = mSyncProcessor.process(context, book.getId(), book, fieldsWanted, bookData);
        } else {
            // we don't need the back cover, but maybe the front cover
            if (coversWanted[0]) {
                downloadFrontCover(externalId, colBook);
            }

            // Extract the delta from the *collection* data
            delta = mSyncProcessor.process(context, book.getId(), book, fieldsWanted, colBook);
        }

        if (delta != null) {
            mBookDao.update(context, delta, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                            | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
            mResults.booksUpdated++;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
                Log.d(TAG, "externalId=" + externalId
                           + "|" + mUpdateOption
                           + "|UPDATE|book=" + book.getId() + "|" + book.getTitle());
            }
        }
    }

    private void insertBook(@NonNull final Context context,
                            final long externalId)
            throws DiskFullException,
                   CoverStorageException,
                   SearchException,
                   CredentialsException,
                   DaoWriteException {
        // It's a new book; download it from the server and insert it into the db
        final Bundle bookData = mSearchEngine
                .searchByExternalId(context, String.valueOf(externalId), mCoversForNewBooks);

        final Book book = Book.from(bookData);
        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf(context);

        mBookDao.insert(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION);
        mResults.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_STRIP_INFO_BOOKS) {
            Log.d(TAG, "externalId=" + externalId
                       + "|" + mUpdateOption
                       + "|INSERT|book=" + book.getId() + "|" + book.getTitle());
        }
    }

    @WorkerThread
    private void downloadFrontCover(@IntRange(from = 1) final long externalId,
                                    @NonNull final Bundle cData)
            throws DiskFullException,
                   CoverStorageException {
        final String url = cData.getString(UserCollection.BKEY_FRONT_COVER_URL);
        if (url != null) {
            final String fileSpec = mSearchEngine
                    .saveImage(url, String.valueOf(externalId), 0, null);
            if (fileSpec != null) {
                cData.putString(Book.BKEY_TMP_FILE_SPEC[0], fileSpec);
            }
        }
    }
}
