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
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * A simple task wrapping {@link ImportCollection}.
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
public class ImportCollectionTask
        extends MTask<ImportCollectionTask.Outcome> {

    private static final String TAG = "ImportCollectionTask";

    /** existing books. */
    private final ArrayList<Long> mUpdated = new ArrayList<>();
    /** any new books. */
    private final ArrayList<Long> mAdded = new ArrayList<>();

    private StripInfoSearchEngine mSearchEngine;
    /** Which fields and how to process them for existing books. */
    private SyncProcessor mSyncProcessor;
    /** How to fetch covers for new books. */
    private boolean[] mCoversForNewBooks;

    @UiThread
    ImportCollectionTask() {
        super(R.id.TASK_ID_IMPORT, TAG);
    }

    @UiThread
    void startImport(@NonNull final SyncProcessor syncProcessor,
                     @NonNull final boolean[] coversForNewBooks) {
        mSearchEngine = (StripInfoSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.STRIP_INFO_BE);
        mSearchEngine.setCaller(this);

        mSyncProcessor = syncProcessor;
        mCoversForNewBooks = coversForNewBooks;

        mUpdated.clear();
        mAdded.clear();
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Outcome doWork(@NonNull final Context context)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException,
                   DaoWriteException, IOException {

        // Got internet?
        if (!NetworkUtils.isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        NetworkUtils.ping(mSearchEngine.getSiteUrl());

        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_connecting));

        final StripInfoAuth loginHelper = new StripInfoAuth(mSearchEngine.getSiteUrl());
        final String userId = loginHelper.login();
        mSearchEngine.setLoginHelper(loginHelper);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SynchronizedDb db = serviceLocator.getDb();
        final BookDao bookDao = serviceLocator.getBookDao();

        final ImportCollection ic = new ImportCollection(context, new SyncConfig(), userId);

        while (ic.hasMore() && !isCancelled()) {
            final List<Bundle> page = ic.fetchPage(context, this);
            if (page != null && !page.isEmpty()) {
                // We're committing by page.
                Synchronizer.SyncLock txLock = null;
                try {
                    txLock = db.beginTransaction(true);

                    processPage(context, bookDao, page);

                    db.setTransactionSuccessful();
                } finally {
                    if (txLock != null) {
                        db.endTransaction(txLock);
                    }
                }
            }
        }

        return new Outcome(mUpdated, mAdded);
    }

    private void processPage(@NonNull final Context context,
                             @NonNull final BookDao bookDao,
                             @NonNull final List<Bundle> page)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException,
                   DaoWriteException {

        for (final Bundle cData : page) {
            if (!isCancelled()) {
                final long externalId = cData.getLong(DBKey.SID_STRIP_INFO);
                // lookup locally using the externalId column.
                try (Cursor cursor = bookDao.fetchByKey(
                        DBKey.SID_STRIP_INFO, String.valueOf(externalId))) {

                    if (cursor.moveToFirst()) {
                        // The full local data
                        final Book book = Book.from(cursor);
                        // The delta values we'll be updating
                        final Book delta;

                        final Map<String, SyncField> fieldsWanted =
                                mSyncProcessor.filter(book);
                        final boolean[] coversWanted = new boolean[]{
                                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0]),
                                fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])};

                        if (coversWanted[1]) {
                            // The back cover is not available on the collection page
                            // Do a full download.
                            final Bundle bookData = mSearchEngine
                                    .searchByExternalId(String.valueOf(externalId), coversWanted);

                            // Extract the delta from the *bookData*
                            delta = mSyncProcessor.process(context, book.getId(), book,
                                                           fieldsWanted, bookData);
                        } else {
                            // we don't need the back cover, but maybe the front cover
                            if (coversWanted[0]) {
                                downloadFrontCover(externalId, cData);
                            }

                            // Extract the delta from the *collection* data
                            delta = mSyncProcessor.process(context, book.getId(), book,
                                                           fieldsWanted, cData);
                        }

                        if (delta != null) {
                            bookDao.update(context, delta, 0);
                        }

                        mUpdated.add(book.getId());
                        publishProgress(1, book.getTitle());

                    } else {
                        // It's a new book, do a full download.
                        final Bundle bookData = mSearchEngine
                                .searchByExternalId(String.valueOf(externalId), mCoversForNewBooks);

                        final Book book = Book.from(bookData);
                        book.ensureBookshelf(context);

                        final long id = bookDao.insert(context, book, 0);
                        if (id > 0) {
                            mAdded.add(id);
                            publishProgress(1, book.getTitle());
                        }
                    }
                }
            }
        }
    }

    @WorkerThread
    private void downloadFrontCover(@IntRange(from = 1) final long externalId,
                                    @NonNull final Bundle cData)
            throws DiskFullException, CoverStorageException {
        final String url = cData.getString(ImportCollection.BKEY_FRONT_COVER_URL);
        if (url != null) {
            final String fileSpec = mSearchEngine
                    .saveImage(url, String.valueOf(externalId), 0, null);
            if (fileSpec != null) {
                cData.putString(Book.BKEY_TMP_FILE_SPEC[0], fileSpec);
            }
        }
    }

    static class Outcome {

        @NonNull
        final ArrayList<Long> updated;
        @NonNull
        final ArrayList<Long> created;

        Outcome(@NonNull final ArrayList<Long> updated,
                @NonNull final ArrayList<Long> created) {
            this.updated = updated;
            this.created = created;
        }
    }
}
