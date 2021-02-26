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
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;

public final class PublisherDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "PublisherDao";

    /** All Books (id only!) for a given Publisher. */
    private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.ref()
            + TBL_BOOK_PUBLISHER.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER) + "=?";

    /** All Books (id only!) for a given Publisher and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.ref()
            + TBL_BOOK_PUBLISHER.join(TBL_BOOKS)
            + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_NAMES =
            SELECT_DISTINCT_ + KEY_PUBLISHER_NAME
            + ',' + KEY_PUBLISHER_NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName()
            + _ORDER_BY_ + KEY_PUBLISHER_NAME_OB + _COLLATION;

    /** {@link Publisher}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_PUBLISHERS.getName();

    /** Get a {@link Publisher} by the Publisher id. */
    private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Publisher} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Searches KEY_PUBLISHER_NAME_OB on both "The Publisher" and "Publisher, The"
     */
    private static final String FIND_ID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION
            + _OR_ + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION;

    private static final String COUNT_ALL =
            "SELECT COUNT(*) FROM " + TBL_PUBLISHERS.getName();

    /** Count the number of {@link Book}'s by an {@link Publisher}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_PUBLISHER.getName()
            + _WHERE_ + KEY_FK_PUBLISHER + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_PUBLISHERS.getName()
            + '(' + KEY_PUBLISHER_NAME
            + ',' + KEY_PUBLISHER_NAME_OB
            + ") VALUES (?,?)";

    /** Delete a {@link Publisher}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + KEY_PK_ID + "=?";

    /** Purge a {@link Publisher} if no longer in use. */
    private static final String PURGE =
            DELETE_FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + KEY_FK_PUBLISHER
            + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';

    /** Singleton. */
    private static PublisherDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private PublisherDao(@NonNull final Context context,
                         @NonNull final String logTag) {
        super(context, logTag);
    }

    public static PublisherDao getInstance() {
        if (sInstance == null) {
            sInstance = new PublisherDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get the {@link Publisher} based on the given id.
     *
     * @param id of Publisher to find
     *
     * @return the {@link Publisher}, or {@code null} if not found
     */
    @Nullable
    public Publisher getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Publisher(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Publisher} by using the appropriate fields of the passed {@link Publisher}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param publisher    to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long find(@NonNull final Context context,
                     @NonNull final Publisher publisher,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale) {

        final Locale publisherLocale;
        if (lookupLocale) {
            publisherLocale = publisher.getLocale(context, bookLocale);
        } else {
            publisherLocale = bookLocale;
        }

        final String obName = publisher.reorderTitleForSorting(context, publisherLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, encodeOrderByColumn(publisher.getName(), publisherLocale));
            stmt.bindString(2, encodeOrderByColumn(obName, publisherLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get a unique list of all publisher names.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getNames() {
        try (Cursor cursor = mDb.rawQuery(SELECT_ALL_NAMES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a list of book ID's for the given Publisher.
     *
     * @param publisherId id of the Publisher
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long publisherId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_IDS_BY_PUBLISHER_ID,
                                          new String[]{String.valueOf(publisherId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Publisher and Bookshelf.
     *
     * @param publisherId id of the Publisher
     * @param bookshelfId id of the Bookshelf
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long publisherId,
                                      final long bookshelfId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(
                SELECT_BOOK_IDS_BY_PUBLISHER_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(publisherId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get all publishers; mainly for the purpose of backups.
     *
     * @return Cursor over all publishers
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
     * Count the books for the given Publisher.
     *
     * @param context    Current context
     * @param publisher  to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link Book} this {@link Publisher} has
     */
    public long countBooks(@NonNull final Context context,
                           @NonNull final Publisher publisher,
                           @NonNull final Locale bookLocale) {
        if (publisher.getId() == 0 && fixId(context, publisher, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mDb
                .compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, publisher.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Try to find the Publisher. If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param publisher    to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, publisher, lookupLocale, bookLocale);
        publisher.setId(id);
        return id;
    }

    /**
     * Refresh the passed Publisher from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Publisher.
     * <p>
     * Will NOT insert a new Publisher if not found.
     *
     * @param context    Current context
     * @param publisher  to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refresh(@NonNull final Context context,
                        @NonNull final Publisher publisher,
                        @NonNull final Locale bookLocale) {

        if (publisher.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            fixId(context, publisher, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Publisher dbPublisher = getById(publisher.getId());
            if (dbPublisher != null) {
                // copy any updated fields
                publisher.copyFrom(dbPublisher);
            } else {
                // not found?, set as 'new'
                publisher.setId(0);
            }
        }
    }

    /**
     * Creates a new Publisher in the database.
     *
     * @param context    Current context
     * @param publisher  object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, encodeOrderByColumn(obTitle, publisherLocale));
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                publisher.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update a Publisher.
     *
     * @param context    Current context
     * @param publisher  to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Publisher publisher,
                          @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        final ContentValues cv = new ContentValues();
        cv.put(KEY_PUBLISHER_NAME, publisher.getName());
        cv.put(KEY_PUBLISHER_NAME_OB, encodeOrderByColumn(obTitle, publisherLocale));

        return 0 < mDb.update(TBL_PUBLISHERS.getName(), cv, KEY_PK_ID + "=?",
                              new String[]{String.valueOf(publisher.getId())});
    }

    /**
     * Delete the passed {@link Publisher}.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, publisher.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            publisher.setId(0);

            try (BookDao bookDao = new BookDao(context, TAG)) {
                bookDao.repositionPublishers(context);
            }
        }
        return rowsAffected == 1;
    }

    /**
     * Moves all books from the 'source' {@link Publisher}, to the 'destId' {@link Publisher}.
     * The (now unused) 'source' {@link Publisher} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Publisher source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_PUBLISHER, destId);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final Publisher destination = getById(destId);

            try (BookDao bookDao = new BookDao(context, TAG)) {
                for (final long bookId : getBookIds(source.getId())) {
                    final Book book = Book.from(bookId, bookDao);

                    final Collection<Publisher> fromBook =
                            book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
                    final Collection<Publisher> destList = new ArrayList<>();

                    for (final Publisher item : fromBook) {
                        if (source.getId() == item.getId()) {
                            destList.add(destination);
                        } else {
                            destList.add(item);
                        }
                    }
                    bookDao.insertBookPublishers(context, bookId, destList, true,
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
        }
    }
}
