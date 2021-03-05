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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;

public class GoodreadsDaoImpl
        extends BaseDaoImpl
        implements GoodreadsDao {

    /** Log tag. */
    private static final String TAG = "GoodreadsDaoImpl";

    /**
     * Base SELECT from {@link DBDefinitions#TBL_BOOKS} for the fields
     * we need to send a Book to Goodreads.
     * <p>
     * Must be kept in sync with {@link GoodreadsManager#sendOneBook}
     */
    private static final String BASE_SELECT =
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_ISBN
            + ',' + DBKeys.KEY_ESID_GOODREADS_BOOK
            + ',' + DBKeys.KEY_READ
            + ',' + DBKeys.KEY_READ_START
            + ',' + DBKeys.KEY_READ_END
            + ',' + DBKeys.KEY_RATING
            + _FROM_ + TBL_BOOKS.getName();

    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the first Book
     * <p>
     * Send only new/updated books
     */
    private static final String UPDATED_BOOKS =
            BASE_SELECT + _WHERE_ + DBKeys.KEY_PK_ID + ">?"
            + _AND_ + DBKeys.KEY_UTC_LAST_UPDATED + '>' + DBKeys.KEY_UTC_GOODREADS_LAST_SYNC_DATE
            + _ORDER_BY_ + DBKeys.KEY_PK_ID;


    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the Book
     */
    private static final String SINGLE_BOOK = BASE_SELECT + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the first Book
     * <p>
     * Send all books.
     */
    private static final String ALL_BOOKS = BASE_SELECT + _WHERE_ + DBKeys.KEY_PK_ID + ">?"
                                            + _ORDER_BY_ + DBKeys.KEY_PK_ID;

    /** A subset of book columns, to be used for searches on Goodreads. */
    private static final String BOOK_COLUMNS_FOR_SEARCH =
            SELECT_ + TBL_BOOKS.dotAs(DBKeys.KEY_PK_ID)
            + ',' + TBL_BOOKS.dotAs(DBKeys.KEY_TITLE)
            + ',' + TBL_BOOKS.dotAs(DBKeys.KEY_ISBN)
            + ',' + AuthorDaoImpl.getDisplayAuthor(true)
            + _AS_ + DBKeys.KEY_AUTHOR_FORMATTED_GIVEN_FIRST

            + _FROM_ + TBL_BOOKS.ref()
            + TBL_BOOKS.join(TBL_BOOK_AUTHOR) + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
            + _WHERE_ + TBL_BOOKS.dot(DBKeys.KEY_PK_ID) + "=?";

    /**
     * Update a single Book's last sync date with Goodreads.
     * Do NOT update the {@link DBKeys#KEY_UTC_LAST_UPDATED} field.
     */
    private static final String UPDATE_GR_LAST_SYNC_DATE =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + DBKeys.KEY_UTC_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
            + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Update a single Book's Goodreads id.
     * Do NOT update the {@link DBKeys#KEY_UTC_LAST_UPDATED} field.
     */
    private static final String UPDATE_GR_BOOK_ID =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + DBKeys.KEY_ESID_GOODREADS_BOOK + "=?"
            + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Constructor.
     */
    public GoodreadsDaoImpl() {
        super(TAG);
    }

    @Override
    @NonNull
    public Cursor fetchBookColumnsForSearch(@IntRange(from = 1) final long bookId) {
        return mDb.rawQuery(BOOK_COLUMNS_FOR_SEARCH, new String[]{String.valueOf(bookId)});
    }

    @Override
    @NonNull
    public Cursor fetchBookForExport(@IntRange(from = 1) final long bookId) {
        return mDb.rawQuery(SINGLE_BOOK, new String[]{String.valueOf(bookId)});
    }

    @Override
    @NonNull
    public Cursor fetchBooksForExport(final long startId,
                                      final boolean updatesOnly) {
        if (updatesOnly) {
            return mDb.rawQuery(UPDATED_BOOKS, new String[]{String.valueOf(startId)});
        } else {
            return mDb.rawQuery(ALL_BOOKS, new String[]{String.valueOf(startId)});
        }
    }

    @Override
    public void setGoodreadsBookId(@IntRange(from = 1) final long bookId,
                                   final long goodreadsBookId) {

        try (SynchronizedStatement stmt = mDb.compileStatement(UPDATE_GR_BOOK_ID)) {
            stmt.bindLong(1, goodreadsBookId);
            stmt.bindLong(2, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void setSyncDate(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(UPDATE_GR_LAST_SYNC_DATE)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }
}
