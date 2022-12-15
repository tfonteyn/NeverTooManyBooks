/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMerger;
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
            SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_PUBLISHER) + "=?";

    /** All Books (id only!) for a given Publisher and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID)
            + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_BOOKS, TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_PUBLISHER) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_NAMES =
            SELECT_DISTINCT_ + DBKey.PUBLISHER_NAME
            + ',' + DBKey.PUBLISHER_NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName()
            + _ORDER_BY_ + DBKey.PUBLISHER_NAME_OB + _COLLATION;

    /** {@link Publisher}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_PUBLISHERS.getName();

    /** All Publishers for a Book; ordered by position, name. */
    private static final String PUBLISHER_BY_BOOK_ID =
            SELECT_DISTINCT_ + TBL_PUBLISHERS.dotAs(DBKey.PK_ID,
                                                    DBKey.PUBLISHER_NAME,
                                                    DBKey.PUBLISHER_NAME_OB)
            + ',' + TBL_BOOK_PUBLISHER.dotAs(DBKey.BOOK_PUBLISHER_POSITION)

            + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_PUBLISHERS)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(DBKey.BOOK_PUBLISHER_POSITION)
            + ',' + TBL_PUBLISHERS.dot(DBKey.PUBLISHER_NAME_OB) + _COLLATION;

    /** Get a {@link Publisher} by the Publisher id. */
    private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

    /**
     * Get the id of a {@link Publisher} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Searches PUBLISHER_NAME_OB on both "The Publisher" and "Publisher, The"
     */
    private static final String FIND_ID =
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION
            + _OR_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION;

    private static final String COUNT_ALL =
            SELECT_COUNT_FROM_ + TBL_PUBLISHERS.getName();

    /** Count the number of {@link Book}'s by an {@link Publisher}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + DBKey.FK_BOOK + ") FROM " + TBL_BOOK_PUBLISHER.getName()
            + _WHERE_ + DBKey.FK_PUBLISHER + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_PUBLISHERS.getName()
            + '(' + DBKey.PUBLISHER_NAME
            + ',' + DBKey.PUBLISHER_NAME_OB
            + ") VALUES (?,?)";

    /** Delete a {@link Publisher}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

    /** Purge a {@link Publisher} if no longer in use. */
    private static final String PURGE =
            DELETE_FROM_ + TBL_PUBLISHERS.getName()
            + _WHERE_ + DBKey.PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + DBKey.FK_PUBLISHER
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
        try (Cursor cursor = db.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
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

        final OrderByHelper.OrderByData obd;
        if (lookupLocale) {
            obd = OrderByHelper.createOrderByData(context, publisher.getName(),
                                                  bookLocale, publisher::getLocale);
        } else {
            obd = OrderByHelper.createOrderByData(context, publisher.getName(),
                                                  bookLocale, null);
        }

        try (SynchronizedStatement stmt = db.compileStatement(FIND_ID)) {
            stmt.bindString(1, SqlEncode.orderByColumn(publisher.getName(), obd.locale));
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getNames() {
        return getColumnAsStringArrayList(SELECT_ALL_NAMES);
    }

    @Override
    @NonNull
    public ArrayList<Publisher> getPublishersByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Publisher> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(PUBLISHER_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Publisher(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public ArrayList<Long> getBookIds(final long publisherId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_BOOK_IDS_BY_PUBLISHER_ID,
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
        try (Cursor cursor = db.rawQuery(
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
        return db.rawQuery(SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countBooks(@NonNull final Context context,
                           @NonNull final Publisher publisher,
                           @NonNull final Locale bookLocale) {
        if (publisher.getId() == 0) {
            fixId(context, publisher, true, bookLocale);
            if (publisher.getId() == 0) {
                return 0;
            }
        }

        try (SynchronizedStatement stmt = db
                .compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, publisher.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Publisher> list,
                             final boolean lookupLocale,
                             @NonNull final Locale bookLocale) {
        if (list.isEmpty()) {
            return false;
        }

        final EntityMerger<Publisher> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Publisher current = entityMerger.next();
            fixId(context, current, lookupLocale, bookLocale);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, publisher, lookupLocale, bookLocale);
        publisher.setId(id);
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

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, publisher.getName(), bookLocale, publisher::getLocale);

        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
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

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, publisher.getName(), bookLocale, publisher::getLocale);

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.PUBLISHER_NAME, publisher.getName());
        cv.put(DBKey.PUBLISHER_NAME_OB, SqlEncode.orderByColumn(obd.title, obd.locale));

        return 0 < db.update(TBL_PUBLISHERS.getName(), cv, DBKey.PK_ID + "=?",
                             new String[]{String.valueOf(publisher.getId())});
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, publisher.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            publisher.setId(0);
            repositionPublishers(context);
        }
        return rowsAffected == 1;
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Publisher source,
                          @NonNull final Publisher target)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Publisher,
            // respecting the position of the Publisher in the list for each book.
            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
            for (final long bookId : getBookIds(source.getId())) {
                final Book book = Book.from(bookId);

                final Collection<Publisher> fromBook = book.getPublishers();
                final Collection<Publisher> destList = new ArrayList<>();

                for (final Publisher item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(target);
                    } else {
                        destList.add(item);
                    }
                }

                // delete old links and store all new links
                // We KNOW there are no updates needed.
                bookDao.insertPublishers(context, bookId, false, destList,
                                         true, book.getLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int repositionPublishers(@NonNull final Context context) {
        final String sql = "SELECT " + DBKey.FK_BOOK + " FROM "
                           + "(SELECT " + DBKey.FK_BOOK
                           + ", MIN(" + DBKey.BOOK_PUBLISHER_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_PUBLISHER.getName()
                           + " GROUP BY " + DBKey.FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionPublishers|" + TBL_BOOK_PUBLISHER.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = context.getResources().getConfiguration().getLocales().get(0);
            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();

            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Publisher> list = getPublishersByBookId(bookId);
                    // We KNOW there are no updates needed.
                    bookDao.insertPublishers(context, bookId, false, list, false, bookLocale);
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionPublishers|done");
                }
            }
        }
        return bookIds.size();
    }
}
