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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

public final class SeriesDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "SeriesDao";

    /** All Books (id only) for a given Series. */
    private static final String SELECT_BOOK_IDS_BY_SERIES_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?";

    /** All Books (id only) for a given Series and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_SERIES.ref()
            + TBL_BOOK_SERIES.join(TBL_BOOKS)
            + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_NAMES =
            SELECT_DISTINCT_ + KEY_SERIES_TITLE
            + ',' + KEY_SERIES_TITLE_OB
            + _FROM_ + TBL_SERIES.getName()
            + _ORDER_BY_ + KEY_SERIES_TITLE_OB + _COLLATION;

    /** {@link Series}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_SERIES.getName();

    /** Get a {@link Series} by the Series id. */
    private static final String GET_BY_ID = SELECT_ALL + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Series} by Title.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Searches KEY_SERIES_TITLE_OB on both "The Title" and "Title, The"
     */
    private static final String FIND_ID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_SERIES.getName()
            + _WHERE_ + KEY_SERIES_TITLE_OB + "=?" + _COLLATION
            + " OR " + KEY_SERIES_TITLE_OB + "=?" + _COLLATION;

    /**
     * Get the language (ISO3) code for a Series.
     * This is defined as the language code for the first book in the Series.
     */
    private static final String GET_LANGUAGE =
            SELECT_ + TBL_BOOKS.dotAs(KEY_LANGUAGE)
            + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?"
            + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES)
            + " LIMIT 1";

    private static final String COUNT_ALL =
            "SELECT COUNT(*) FROM " + TBL_SERIES.getName();

    /** Count the number of {@link Book}'s in a {@link Series}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_SERIES.getName()
            + _WHERE_ + KEY_FK_SERIES + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_SERIES.getName()
            + '(' + KEY_SERIES_TITLE
            + ',' + KEY_SERIES_TITLE_OB
            + ',' + KEY_SERIES_IS_COMPLETE
            + ") VALUES (?,?,?)";

    /** Delete a {@link Series}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_SERIES.getName() + _WHERE_ + KEY_PK_ID + "=?";

    /** Purge a {@link Series} if no longer in use. */
    private static final String PURGE =
            DELETE_FROM_ + TBL_SERIES.getName()
            + _WHERE_ + KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + KEY_FK_SERIES + _FROM_ + TBL_BOOK_SERIES.getName() + ')';

    /** Singleton. */
    private static SeriesDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private SeriesDao(@NonNull final Context context,
                      @NonNull final String logTag) {
        super(context, logTag);
    }

    public static SeriesDao getInstance() {
        if (sInstance == null) {
            sInstance = new SeriesDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get the {@link Series} based on the given id.
     *
     * @param id of Series to find
     *
     * @return the {@link Series}, or {@code null} if not found
     */
    @Nullable
    public Series getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(GET_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Series(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Series} by using the appropriate fields of the passed {@link Series}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param series       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long find(@NonNull final Context context,
                     @NonNull final Series series,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale) {

        final Locale seriesLocale;
        if (lookupLocale) {
            seriesLocale = series.getLocale(context, bookLocale);
        } else {
            seriesLocale = bookLocale;
        }

        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, encodeOrderByColumn(series.getTitle(), seriesLocale));
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get a unique list of all series titles.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getNames() {
        return getColumnAsList(SELECT_ALL_NAMES, KEY_SERIES_TITLE);
    }

    /**
     * Get the language (ISO3) code for a Series.
     * This is defined as the language code for the first book in the Series.
     *
     * @param id series to get
     *
     * @return the ISO3 code, or the empty String when none found.
     */
    @NonNull
    public String getLanguage(final long id) {
        try (SynchronizedStatement stmt = mDb.compileStatement(GET_LANGUAGE)) {
            stmt.bindLong(1, id);
            final String code = stmt.simpleQueryForStringOrNull();
            return code != null ? code : "";
        }
    }

    /**
     * Get a list of book ID's for the given Series.
     *
     * @param seriesId id of the Series
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long seriesId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_IDS_BY_SERIES_ID,
                                          new String[]{String.valueOf(seriesId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Series and Bookshelf.
     *
     * @param seriesId    id of the Series
     * @param bookshelfId id of the Bookshelf
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long seriesId,
                                      final long bookshelfId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(
                SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(seriesId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get all series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchAll() {
        return mDb.rawQuery(SELECT_ALL, null);
    }

    public long count() {
        try (SynchronizedStatement stmt = mDb.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the books for the given Series.
     *
     * @param context    Current context
     * @param series     to count the books in
     * @param bookLocale Locale to use if the item has none set
     *
     * @return number of books in series
     */
    public long countBooks(@NonNull final Context context,
                           @NonNull final Series series,
                           @NonNull final Locale bookLocale) {
        if (series.getId() == 0 && fixId(context, series, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mDb.compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, series.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Update the 'complete' status of a Series.
     *
     * @param seriesId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setComplete(final long seriesId,
                               final boolean isComplete) {
        final ContentValues cv = new ContentValues();
        cv.put(KEY_SERIES_IS_COMPLETE, isComplete);

        return 0 < mDb.update(TBL_SERIES.getName(), cv, KEY_PK_ID + "=?",
                              new String[]{String.valueOf(seriesId)});
    }

    /**
     * Try to find the Series. If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param series       to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Context context,
                      @NonNull final Series series,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, series, lookupLocale, bookLocale);
        series.setId(id);
        return id;
    }

    /**
     * Refresh the passed Series from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Series.
     * <p>
     * Will NOT insert a new Series if not found.
     *
     * @param context    Current context
     * @param series     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refresh(@NonNull final Context context,
                        @NonNull final Series series,
                        @NonNull final Locale bookLocale) {

        if (series.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            fixId(context, series, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Series dbSeries = getById(series.getId());
            if (dbSeries != null) {
                // copy any updated fields
                series.copyFrom(dbSeries, false);
            } else {
                // not found?, set as 'new'
                series.setId(0);
            }
        }
    }

    /**
     * Creates a new Series in the database.
     *
     * @param context    Current context
     * @param series     object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Series series,
                       @NonNull final Locale bookLocale) {

        final Locale seriesLocale = series.getLocale(context, bookLocale);

        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            stmt.bindBoolean(3, series.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update a Series.
     *
     * @param context    Current context
     * @param series     to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Series series,
                          @NonNull final Locale bookLocale) {

        final Locale seriesLocale = series.getLocale(context, bookLocale);
        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        final ContentValues cv = new ContentValues();
        cv.put(KEY_SERIES_TITLE, series.getTitle());
        cv.put(KEY_SERIES_TITLE_OB, encodeOrderByColumn(obTitle, seriesLocale));
        cv.put(KEY_SERIES_IS_COMPLETE, series.isComplete());

        return 0 < mDb.update(TBL_SERIES.getName(), cv, KEY_PK_ID + "=?",
                              new String[]{String.valueOf(series.getId())});
    }

    /**
     * Delete the passed {@link Series}.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, series.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            series.setId(0);
            try (BookDao bookDao = new BookDao(context, TAG)) {
                bookDao.repositionSeries(context);
            }
        }
        return rowsAffected == 1;
    }

    /**
     * Moves all books from the 'source' {@link Series}, to the 'destId' {@link Series}.
     * The (now unused) 'source' {@link Series} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Series source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_SERIES, destId);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final Series destination = getById(destId);

            try (BookDao bookDao = new BookDao(context, TAG)) {
                for (final long bookId : getBookIds(source.getId())) {
                    final Book book = Book.from(bookId, bookDao);

                    final Collection<Series> fromBook =
                            book.getParcelableArrayList(Book.BKEY_SERIES_LIST);
                    final Collection<Series> destList = new ArrayList<>();

                    for (final Series item : fromBook) {
                        if (source.getId() == item.getId()) {
                            destList.add(destination);
                        } else {
                            destList.add(item);
                        }
                    }
                    bookDao.insertBookSeries(context, bookId, destList, true,
                                             book.getLocale(context));
                }
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    public void purge() {
        try (SynchronizedStatement stmt = mDb.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad.
            Logger.error(TAG, e);
        }
    }
}
