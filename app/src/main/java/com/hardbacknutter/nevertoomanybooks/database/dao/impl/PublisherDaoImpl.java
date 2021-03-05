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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;

public class PublisherDaoImpl
        extends BaseDaoImpl
        implements PublisherDao {

    /** Log tag. */
    private static final String TAG = "PublisherDaoImpl";

    /** All Books (id only!) for a given Publisher. */
    private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKeys.KEY_PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.ref()
            + TBL_BOOK_PUBLISHER.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKeys.KEY_FK_PUBLISHER) + "=?";

    /** All Books (id only!) for a given Publisher and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKeys.KEY_PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.ref()
            + TBL_BOOK_PUBLISHER.join(TBL_BOOKS)
            + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKeys.KEY_FK_PUBLISHER) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKeys.KEY_FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_NAMES =
            SELECT_DISTINCT_ + DBKeys.KEY_PUBLISHER_NAME
            + ',' + DBKeys.KEY_PUBLISHER_NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName()
            + _ORDER_BY_ + DBKeys.KEY_PUBLISHER_NAME_OB + _COLLATION;

    /** {@link Publisher}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_PUBLISHERS.getName();

    /** Get a {@link Publisher} by the Publisher id. */
    private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Publisher} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Searches KEY_PUBLISHER_NAME_OB on both "The Publisher" and "Publisher, The"
     */
    private static final String FIND_ID =
            SELECT_ + DBKeys.KEY_PK_ID + _FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + DBKeys.KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION
            + _OR_ + DBKeys.KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION;

    private static final String COUNT_ALL =
            "SELECT COUNT(*) FROM " + TBL_PUBLISHERS.getName();

    /** Count the number of {@link Book}'s by an {@link Publisher}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + DBKeys.KEY_FK_BOOK + ") FROM " + TBL_BOOK_PUBLISHER.getName()
            + _WHERE_ + DBKeys.KEY_FK_PUBLISHER + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_PUBLISHERS.getName()
            + '(' + DBKeys.KEY_PUBLISHER_NAME
            + ',' + DBKeys.KEY_PUBLISHER_NAME_OB
            + ") VALUES (?,?)";

    /** Delete a {@link Publisher}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /** Purge a {@link Publisher} if no longer in use. */
    private static final String PURGE =
            DELETE_FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + DBKeys.KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + DBKeys.KEY_FK_PUBLISHER
            + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';


    /**
     * Constructor.
     */
    public PublisherDaoImpl() {
        super(TAG);
    }


    @Override
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

    @Override
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
            stmt.bindString(1, BaseDaoImpl
                    .encodeOrderByColumn(publisher.getName(), publisherLocale));
            stmt.bindString(2, BaseDaoImpl.encodeOrderByColumn(obName, publisherLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getNames() {
        try (Cursor cursor = mDb.rawQuery(SELECT_ALL_NAMES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    @Override
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

    @Override
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

    @Override
    @NonNull
    public Cursor fetchAll() {
        return mDb.rawQuery(SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = mDb.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
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

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, publisher, lookupLocale, bookLocale);
        publisher.setId(id);
        return id;
    }

    @Override
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

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, BaseDaoImpl.encodeOrderByColumn(obTitle, publisherLocale));
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                publisher.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Publisher publisher,
                          @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_PUBLISHER_NAME, publisher.getName());
        cv.put(DBKeys.KEY_PUBLISHER_NAME_OB,
               BaseDaoImpl.encodeOrderByColumn(obTitle, publisherLocale));

        return 0 < mDb.update(TBL_PUBLISHERS.getName(), cv, DBKeys.KEY_PK_ID + "=?",
                              new String[]{String.valueOf(publisher.getId())});
    }

    @Override
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

            try (BookDao bookDao = new BookDao(TAG)) {
                bookDao.repositionPublishers(context);
            }
        }
        return rowsAffected == 1;
    }

    @Override
    public void merge(@NonNull final Context context,
                      @NonNull final Publisher source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_FK_PUBLISHER, destId);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final Publisher destination = getById(destId);

            try (BookDao bookDao = new BookDao(TAG)) {
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

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = mDb.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        }
    }
}
