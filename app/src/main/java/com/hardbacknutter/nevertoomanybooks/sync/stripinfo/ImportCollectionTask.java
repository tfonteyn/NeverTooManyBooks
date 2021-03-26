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

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

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

    private StripInfoSearchEngine mSearchEngine;
    private SyncProcessor mSyncProcessor;

    @UiThread
    ImportCollectionTask() {
        super(R.id.TASK_ID_IMPORT, TAG);
    }

    @UiThread
    void fetch(@NonNull final SyncProcessor syncProcessor) {
        mSearchEngine = (StripInfoSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.STRIP_INFO_BE);
        mSearchEngine.setCaller(this);

        mSyncProcessor = syncProcessor;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Outcome doWork(@NonNull final Context context)
            throws IOException {

        // existing books are updated as we get them
        final ArrayList<Long> updated = new ArrayList<>();
        // any new books will be handled in a second pass.
        final ArrayList<Long> queued = new ArrayList<>();

        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_connecting));

        final StripInfoAuth loginHelper = new StripInfoAuth(mSearchEngine.getSiteUrl());
        final String userId = loginHelper.login();
        mSearchEngine.setLoginHelper(loginHelper);

        final SynchronizedDb db = ServiceLocator.getDb();

        // Step 1: get the collection.
        final Bookshelf wishListBookshelf = loginHelper.getWishListBookshelf(context);
        final ImportCollection ic = new ImportCollection(userId, wishListBookshelf);
        final List<Bundle> all = ic.fetch(context, this);

        setIndeterminate(false);
        setMaxPos(all.size());
        publishProgress(0, context.getString(R.string.progress_msg_searching));

        Synchronizer.SyncLock txLock = null;
        try (BookDao bookDao = new BookDao(TAG)) {
            txLock = db.beginTransaction(true);

            // Step 2: update the local book or queue new books.
            for (final Bundle cData : all) {
                if (!isCancelled()) {
                    final long externalId = cData.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
                    // lookup locally using the externalId column.
                    try (Cursor cursor = bookDao.fetchBooksByKey(
                            DBKeys.KEY_ESID_STRIP_INFO_BE, externalId)) {

                        if (cursor.moveToFirst()) {
                            final Book book = Book.from(cursor, bookDao);

                            final Map<String, SyncField> fieldsWanted =
                                    mSyncProcessor.filter(context, book);
                            final boolean[] coversWanted = new boolean[]{
                                    fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0]),
                                    fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])};

                            if (coversWanted[1]) {
                                // The back cover is not available on the collection page
                                // Do a full download.
                                final Bundle bookData = mSearchEngine.searchByExternalId(
                                        String.valueOf(externalId), coversWanted);

                                // Merge the *bookData*, and update the database
                                mSyncProcessor.process(context, book.getId(), book,
                                                       fieldsWanted, bookData);
                            } else {
                                // we don't need the back cover, maybe the front cover
                                if (coversWanted[0]) {
                                    downloadFrontCover(externalId, cData);
                                }
                                // Merge the *collection* data, and update the database
                                mSyncProcessor.process(context, book.getId(), book,
                                                       fieldsWanted, cData);
                            }

                            updated.add(book.getId());
                            publishProgress(1, book.getTitle());

                        } else {
                            // It's a new book
                            queued.add(externalId);
                            publishProgress(1, "");
                        }
                    }
                }
            }

            // Step 3: store the queued books.
            if (!isCancelled()) {
                // always done, even if empty as we need to delete any previously queued
                ServiceLocator.getInstance().getStripInfoDao().insert(queued);
            }

            db.setTransactionSuccessful();
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        return new Outcome(updated, queued);
    }

    @WorkerThread
    private void downloadFrontCover(final long externalId,
                                    @NonNull final Bundle cData) {
        final String url = cData.getString(StripInfoSearchEngine.SiteField.FRONT_COVER_URL);
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
        final ArrayList<Long> queued;

        Outcome(@NonNull final ArrayList<Long> updated,
                @NonNull final ArrayList<Long> queued) {
            this.updated = updated;
            this.queued = queued;
        }
    }
}
