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
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
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
 * ENHANCE: add 2 more mapped shelves for the last two options above?
 */
public class ImportQueuedBooksTask
        extends MTask<ArrayList<Long>> {

    /** Log tag. */
    private static final String TAG = "StripInfoFetchCollTask";

    private StripInfoSearchEngine mSearchEngine;

    /** Whether to fetch covers. */
    private boolean[] mFetchCovers;

    ImportQueuedBooksTask() {
        super(R.id.TASK_ID_IMPORT, TAG);
    }

    /**
     * Start the task.
     *
     * @param fetchCovers the default for new books.
     */
    void fetch(@NonNull final boolean[] fetchCovers) {
        mSearchEngine = (StripInfoSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.STRIP_INFO_BE);
        mSearchEngine.setCaller(this);

        mFetchCovers = fetchCovers;
        execute();
    }

    @NonNull
    @Override
    protected ArrayList<Long> doWork(@NonNull final Context context)
            throws IOException {

        final ArrayList<Long> booksAdded = new ArrayList<>();

        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_connecting));

        final StripInfoAuth loginHelper = new StripInfoAuth(mSearchEngine.getSiteUrl());
        loginHelper.login();
        mSearchEngine.setLoginHelper(loginHelper);

        final SynchronizedDb db = ServiceLocator.getDb();

        final StripInfoDao stripInfoDao = ServiceLocator.getInstance().getStripInfoDao();

        final List<Long> all = stripInfoDao.getAll();

        setIndeterminate(false);
        setMaxPos(all.size());
        publishProgress(0, context.getString(R.string.progress_msg_searching));

        try (BookDao bookDao = new BookDao(TAG)) {
            for (final long externalId : all) {
                if (!isCancelled()) {
                    try {
                        final Bundle bookData = mSearchEngine
                                .searchByExternalId(String.valueOf(externalId), mFetchCovers);

                        final Book book = Book.from(bookData);
                        book.ensureBookshelf(context);

                        Synchronizer.SyncLock txLock = null;
                        try {
                            txLock = db.beginTransaction(true);
                            bookDao.insert(context, book, 0);
                            stripInfoDao.deleteOne(externalId);
                            db.setTransactionSuccessful();
                        } finally {
                            if (txLock != null) {
                                db.endTransaction(txLock);
                            }
                        }
                        final long id = book.getId();
                        if (id > 0) {
                            booksAdded.add(id);
                            publishProgress(1, book.getTitle());
                        }
                    } catch (@NonNull final IOException | DaoWriteException ignore) {
                        // ignore, just move to next book
                    }
                }
            }
        }

        return booksAdded;
    }
}
