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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.PublisherMergeHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;

public class PublisherDaoImpl
        extends BaseDaoImpl
        implements PublisherDao {

    /** Log tag. */
    private static final String TAG = "PublisherDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     */
    public PublisherDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @Nullable
    public Publisher getById(final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Publisher(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    @Override
    @Nullable
    public Publisher findByName(@NonNull final Context context,
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

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                SqlEncode.orderByColumn(publisher.getName(), obd.locale),
                SqlEncode.orderByColumn(obd.title, obd.locale)})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return new Publisher(rowData.getLong(DBKey.PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getNames() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES);
    }

    @Override
    @NonNull
    public ArrayList<Publisher> getByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Publisher> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.PUBLISHER_BY_BOOK_ID,
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
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_IDS_BY_PUBLISHER_ID,
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
                Sql.SELECT_BOOK_IDS_BY_PUBLISHER_ID_AND_BOOKSHELF_ID,
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
        return db.rawQuery(Sql.SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
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
                .compileStatement(Sql.COUNT_BOOKS)) {
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

        final PublisherMergeHelper mergeHelper = new PublisherMergeHelper();
        return mergeHelper.merge(list,
                                 current -> {
                                     final Locale locale;
                                     if (lookupLocale) {
                                         locale = current.getLocale(context, bookLocale);
                                     } else {
                                         locale = bookLocale;
                                     }
                                     // Don't lookup the locale a 2nd time.
                                     fixId(context, current, false, locale);
                                 });
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final Publisher found = findByName(context, publisher, lookupLocale, bookLocale);
        publisher.setId(found == null ? 0 : found.getId());
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Publisher publisher,
                        final boolean lookupLocale,
                        @NonNull final Locale bookLocale) {

        // If needed, check if we already have it in the database.
        if (publisher.getId() == 0) {
            fixId(context, publisher, lookupLocale, bookLocale);
        }

        // If we do already have it, update the object
        if (publisher.getId() > 0) {
            final Publisher dbPublisher = getById(publisher.getId());
            // Sanity check
            if (dbPublisher != null) {
                // copy any updated fields
                publisher.copyFrom(dbPublisher);
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                publisher.setId(0);
            }
        }
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale)
            throws DaoWriteException {

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, publisher.getName(), bookLocale, publisher::getLocale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                publisher.setId(iId);
                return iId;
            }

            throw new DaoWriteException(ERROR_INSERT_FROM + publisher);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_INSERT_FROM + publisher, e);
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale)
            throws DaoWriteException {

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, publisher.getName(), bookLocale, publisher::getLocale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
            stmt.bindLong(3, publisher.getId());

            final boolean success = 0 < stmt.executeUpdateDelete();
            if (success) {
                return;
            }

            throw new DaoWriteException(ERROR_UPDATE_FROM + publisher);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATE_FROM + publisher, e);
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, publisher.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            publisher.setId(0);
            fixPositions(context);
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
                        // We could 'break' here as there should be no duplicates,
                        // but paranoia...
                    } else {
                        // just keep/copy
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
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int fixPositions(@NonNull final Context context) {

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = context.getResources().getConfiguration().getLocales().get(0);
            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();

            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Publisher> list = getByBookId(bookId);
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
            }
        }
        return bookIds.size();
    }

    private static class Sql {

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

        /** Get a {@link Publisher} by the Publisher id. */
        private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

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

        /**
         * Find a {@link Publisher} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches PUBLISHER_NAME_OB on both "The Publisher" and "Publisher, The"
         */
        private static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION
                + _OR_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION;

        private static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_PUBLISHERS.getName();

        /** Count the number of {@link Book}'s by an {@link Publisher}. */
        private static final String COUNT_BOOKS =
                SELECT_ + "COUNT(" + DBKey.FK_BOOK + ')'
                + _FROM_ + TBL_BOOK_PUBLISHER.getName()
                + _WHERE_ + DBKey.FK_PUBLISHER + "=?";

        private static final String INSERT =
                INSERT_INTO_ + TBL_PUBLISHERS.getName()
                + '(' + DBKey.PUBLISHER_NAME
                + ',' + DBKey.PUBLISHER_NAME_OB
                + ") VALUES (?,?)";

        private static final String UPDATE =
                UPDATE_ + TBL_PUBLISHERS.getName()
                + _SET_ + DBKey.PUBLISHER_NAME + "=?"
                + ',' + DBKey.PUBLISHER_NAME_OB + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Publisher}. */
        private static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge a {@link Publisher} if no longer in use. */
        private static final String PURGE =
                DELETE_FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_PUBLISHER
                + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';

        private static final String REPOSITION =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_
                + '(' + SELECT_ + DBKey.FK_BOOK
                + ",MIN(" + DBKey.BOOK_PUBLISHER_POSITION + ')' + _AS_ + "mp"
                + _FROM_ + TBL_BOOK_PUBLISHER.getName()
                + _GROUP_BY_ + DBKey.FK_BOOK
                + ')'
                + _WHERE_ + "mp>1";
    }
}
