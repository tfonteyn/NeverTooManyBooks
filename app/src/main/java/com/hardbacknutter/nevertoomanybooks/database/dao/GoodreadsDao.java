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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;

/**
 * Not a singleton; this class will not constantly be in use.
 */
public class GoodreadsDao
        extends BaseDao {

    /**
     * Base SELECT from {@link DBDefinitions#TBL_BOOKS} for the fields
     * we need to send a Book to Goodreads.
     * <p>
     * Must be kept in sync with {@link GoodreadsManager#sendOneBook}
     */
    private static final String BASE_SELECT =
            SELECT_ + KEY_PK_ID
            + ',' + KEY_ISBN
            + ',' + KEY_ESID_GOODREADS_BOOK
            + ',' + KEY_READ
            + ',' + KEY_READ_START
            + ',' + KEY_READ_END
            + ',' + KEY_RATING
            + _FROM_ + TBL_BOOKS.getName();

    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the first Book
     * <p>
     * Send only new/updated books
     */
    private static final String UPDATED_BOOKS =
            BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
            + _AND_ + KEY_UTC_LAST_UPDATED + '>' + KEY_UTC_GOODREADS_LAST_SYNC_DATE
            + _ORDER_BY_ + KEY_PK_ID;


    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the Book
     */
    private static final String SINGLE_BOOK = BASE_SELECT + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Get the needed Book fields for sending to Goodreads.
     * <p>
     * param KEY_PK_ID of the first Book
     * <p>
     * Send all books.
     */
    private static final String ALL_BOOKS = BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
                                            + _ORDER_BY_ + KEY_PK_ID;

    /** A subset of book columns, to be used for searches on Goodreads. */
    private static final String BOOK_COLUMNS_FOR_SEARCH =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
            + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
            + ',' + AuthorDao.getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
            + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST

            + _FROM_ + TBL_BOOKS.ref()
            + TBL_BOOKS.join(TBL_BOOK_AUTHOR) + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
            + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=?";

    /**
     * Update a single Book's last sync date with Goodreads.
     * Do NOT update the {@link DBDefinitions#KEY_UTC_LAST_UPDATED} field.
     */
    private static final String UPDATE_GR_LAST_SYNC_DATE =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + KEY_UTC_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
            + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Update a single Book's Goodreads id.
     * Do NOT update the {@link DBDefinitions#KEY_UTC_LAST_UPDATED} field.
     */
    private static final String UPDATE_GR_BOOK_ID =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + KEY_ESID_GOODREADS_BOOK + "=?"
            + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public GoodreadsDao(@NonNull final Context context,
                        @NonNull final String logTag) {
        super(context, logTag);
    }

    /**
     * Query to get the relevant columns for searching for a Book on Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return A subset of book columns, suitable for searches on Goodreads.
     */
    @NonNull
    public Cursor fetchBookColumnsForSearch(@IntRange(from = 1) final long bookId) {
        return mDb.rawQuery(BOOK_COLUMNS_FOR_SEARCH, new String[]{String.valueOf(bookId)});
    }

    /**
     * Query to get the relevant columns for sending a single Book to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBookForExport(@IntRange(from = 1) final long bookId) {
        return mDb.rawQuery(SINGLE_BOOK, new String[]{String.valueOf(bookId)});
    }

    /**
     * Query to get the relevant columns for sending a set of Book 's to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooksForExport(final long startId,
                                      final boolean updatesOnly) {
        if (updatesOnly) {
            return mDb.rawQuery(UPDATED_BOOKS, new String[]{String.valueOf(startId)});
        } else {
            return mDb.rawQuery(ALL_BOOKS, new String[]{String.valueOf(startId)});
        }
    }

    /**
     * Set the Goodreads book id for this book.
     *
     * @param bookId          the/our book id
     * @param goodreadsBookId the Goodreads book id
     */
    public void setGoodreadsBookId(@IntRange(from = 1) final long bookId,
                                   final long goodreadsBookId) {

        try (SynchronizedStatement stmt = mDb.compileStatement(UPDATE_GR_BOOK_ID)) {
            stmt.bindLong(1, goodreadsBookId);
            stmt.bindLong(2, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Set the Goodreads sync date to the current time.
     *
     * @param bookId the book
     */
    public void setSyncDate(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(UPDATE_GR_LAST_SYNC_DATE)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }
}
