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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.FieldSync;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.SyncAction;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.SyncProcessor;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

/**
 * A simple task wrapping {@link CollectionImporter}.
 * <p>
 * No options for now, just fetch all books in the user collection on the site.
 * This includes:
 * <ul>
 *     <li>owned: imported to the current Bookshelf</li>
 *     <li>wanted: imported to the mapped Wishlist Bookshelf</li>
 *     <li>rated: ignored unless also owned/wanted</li>
 *     <li>added a note: ignored unless also owned/wanted</li>
 * </ul>
 * ENHANCE: add 2 more mapped shelves for the last two options above?
 */
public class CollectionImporterTask
        extends MTask<List<Long>> {

    /** Log tag. */
    private static final String TAG = "StripInfoFetchCollTask";

    private final SearchCoordinator.CoverFilter mCoverFilter =
            new SearchCoordinator.CoverFilter();

    @NonNull
    private final StripInfoSearchEngine mSearchEngine;

    private SyncProcessor mSyncProcessor;

    private boolean[] mFetchThumbnailsDefault;

    CollectionImporterTask() {
        super(R.id.TASK_ID_IMPORT, TAG);

        mSearchEngine = (StripInfoSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.STRIP_INFO_BE);
        mSearchEngine.setCaller(this);
    }

    void fetch(@NonNull final SyncProcessor syncProcessor) {
        mSyncProcessor = syncProcessor;
        mFetchThumbnailsDefault = new boolean[]{
                mSyncProcessor.getSyncAction(DBKeys.COVER_IS_USED[0]) != SyncAction.Skip,
                mSyncProcessor.getSyncAction(DBKeys.COVER_IS_USED[1]) != SyncAction.Skip
        };

        execute();
    }

    @NonNull
    @Override
    protected List<Long> doWork(@NonNull final Context context)
            throws IOException {
        final List<Long> bookIdList = new ArrayList<>();

        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_connecting));

        final StripinfoLoginHelper loginHelper = new StripinfoLoginHelper();
        if (loginHelper.login()) {
            mSearchEngine.setLoginHelper(loginHelper);

            final String userId = loginHelper.getUserId();
            if (userId != null && !userId.isEmpty()) {
                final Bookshelf wishListBookshelf = loginHelper.getWishListBookshelf(context);
                final Bookshelf currentBookshelf =
                        Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT);

                // Step 1: get the collection.
                final CollectionImporter ic = new CollectionImporter(userId, wishListBookshelf);
                final List<Bundle> all = ic.fetch(context, this);

                setIndeterminate(false);
                setMaxPos(all.size());
                // for lack of a better message...
                publishProgress(0, context.getString(R.string.progress_msg_searching));

                // Step 2: update the local book or import a new book.
                try (BookDao bookDao = new BookDao(TAG)) {
                    for (final Bundle cData : all) {
                        final long externalId = cData.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
                        Book localBook = null;

                        // exists locally ?
                        try (Cursor cursor = bookDao.fetchBooksByKey(DBKeys.KEY_ESID_STRIP_INFO_BE,
                                                                     externalId)) {
                            if (cursor.moveToFirst()) {
                                localBook = Book.from(cursor, bookDao);
                                final Map<String, FieldSync> fieldsWanted =
                                        mSyncProcessor.filter(context, localBook);

                                //URGENT: need to download covers at this point!
                                if (fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[0])) {

                                }
                                if (fieldsWanted.containsKey(Book.BKEY_TMP_FILE_SPEC[1])) {

                                }
                                //mCoverFilter.filter(localBook);

                                mSyncProcessor.processOne(context, localBook.getId(),
                                                          localBook, fieldsWanted, cData);
                                bookIdList.add(localBook.getId());

                                publishProgress(1, localBook.getTitle());
                            }
                        }

                        // It's a new book
                        if (localBook == null) {
                            try {
                                // Get the site default book data
                                final Bundle bookData = mSearchEngine
                                        .searchByExternalId(String.valueOf(externalId),
                                                            mFetchThumbnailsDefault);
                                // If the engine added the bookshelves list
                                // (with potentially the wishlist)
                                // then make sure not to overwrite it.
                                if (bookData.containsKey(Book.BKEY_BOOKSHELF_LIST)) {
                                    cData.remove(Book.BKEY_BOOKSHELF_LIST);
                                }

                                // Merge the collection data.
                                bookData.putAll(cData);

                                // remove what we don't want.
                                mCoverFilter.filter(bookData);

                                // Always add the current bookshelf
                                ArrayList<Bookshelf> bookshelves =
                                        bookData.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
                                if (bookshelves == null) {
                                    bookshelves = new ArrayList<>();
                                }
                                bookshelves.add(0, currentBookshelf);
                                bookData.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST,
                                                                bookshelves);

                                // finally create the new Book
                                localBook = Book.from(bookData);
                                final long id = bookDao.insert(context, localBook, 0);
                                if (id > 0) {
                                    bookIdList.add(id);
                                    publishProgress(1, localBook.getTitle());
                                }
                            } catch (@NonNull final IOException | DaoWriteException ignore) {
                                // ignore, just move to next book
                            }
                        }
                    }
                }
            }
        }

        return bookIdList;
    }
}
