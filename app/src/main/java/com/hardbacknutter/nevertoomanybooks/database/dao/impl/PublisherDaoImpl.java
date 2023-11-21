/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.database.SQLException;
import android.database.sqlite.SQLiteException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.database.UncheckedDaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.PublisherMergeHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

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
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}
     */
    public PublisherDaoImpl(@NonNull final SynchronizedDb db,
                            @NonNull final Supplier<ReorderHelper> reorderHelperSupplier) {
        super(db, TAG);
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    @NonNull
    @Override
    public Optional<Publisher> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Publisher(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Publisher> findByName(@NonNull final Context context,
                                          @NonNull final Publisher publisher,
                                          @NonNull final Locale locale) {

        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String text = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, text, locale);

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                SqlEncode.orderByColumn(publisher.getName(), locale),
                SqlEncode.orderByColumn(obName, locale)})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Publisher(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<String> getNames() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES);
    }

    @Override
    @NonNull
    public List<Publisher> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Publisher> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.PUBLISHER_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Publisher(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long publisherId) {
        final List<Long> list = new ArrayList<>();
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
    public List<Long> getBookIds(final long publisherId,
                                 final long bookshelfId) {
        final List<Long> list = new ArrayList<>();
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
            fixId(context, publisher, publisher.getLocale(context).orElse(bookLocale));
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
                             final boolean normalize,
                             @NonNull final Function<Publisher, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        if (normalize) {
            final ReorderHelper reorderHelper = ServiceLocator.getInstance().getReorderHelper();
            final List<Locale> locales = LocaleListUtils.asList(context);
            list.forEach(publisher -> {
                final String name = reorderHelper.reverse(context, publisher.getName(),
                                                          localeSupplier.apply(publisher), locales);
                publisher.setName(name);
            });
        }

        final PublisherMergeHelper mergeHelper = new PublisherMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher,
                      @NonNull final Locale locale) {
        final long found = findByName(context, publisher, locale)
                .map(Publisher::getId).orElse(0L);
        publisher.setId(found);
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Publisher publisher,
                        @NonNull final Locale locale) {

        // If needed, check if we already have it in the database.
        if (publisher.getId() == 0) {
            fixId(context, publisher, locale);
        }

        // If we do already have it, update the object
        if (publisher.getId() > 0) {
            final Optional<Publisher> dbPublisher = findById(publisher.getId());
            // Sanity check
            if (dbPublisher.isPresent()) {
                // copy any updated fields
                publisher.copyFrom(dbPublisher.get());
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                publisher.setId(0);
            }
        }
    }


    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               final boolean doUpdates,
                               @NonNull final Collection<Publisher> list,
                               final boolean lookupLocale,
                               @NonNull final Locale bookLocale)
            throws DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final Function<Publisher, Locale> localeSupplier = item -> {
            if (lookupLocale) {
                return item.getLocale(context).orElse(bookLocale);
            } else {
                return bookLocale;
            }
        };

        pruneList(context, list, localeSupplier);

        // Just delete all current links; we'll re-insert them for easier positioning
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        int position = 0;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Publisher publisher : list) {
                fixId(context, publisher, localeSupplier.apply(publisher));

                // create if needed - do NOT do updates unless explicitly allowed
                if (publisher.getId() == 0) {
                    insert(context, publisher, bookLocale);
                } else if (doUpdates) {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise the trigger after_update_on" + TBL_PUBLISHER
                    // thereby setting DATE_LAST_UPDATED__UTC for
                    // ALL books by that publisher
                    findById(publisher.getId()).ifPresent(current -> {
                        if (!current.equals(publisher)) {
                            try {
                                update(context, publisher, bookLocale);
                            } catch (@NonNull final DaoUpdateException e) {
                                throw new UncheckedDaoWriteException(e);
                            }
                        }
                    });
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, publisher.getId());
                stmt.bindLong(3, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert Book-Publisher");
                }
            }
        }
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale)
            throws DaoInsertException {

        final Locale locale = publisher.getLocale(context).orElse(bookLocale);
        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String text = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, text, locale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obName, locale));
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                publisher.setId(iId);
                return iId;
            }

            throw new DaoInsertException(ERROR_INSERT_FROM + publisher);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoInsertException(ERROR_INSERT_FROM + publisher, e);
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale bookLocale)
            throws DaoUpdateException {

        final Locale locale = publisher.getLocale(context).orElse(bookLocale);
        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String text = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, text, locale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obName, locale));
            stmt.bindLong(3, publisher.getId());

            final int rowsAffected = stmt.executeUpdateDelete();
            if (rowsAffected > 0) {
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATE_FROM + publisher);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoUpdateException(ERROR_UPDATE_FROM + publisher, e);
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, publisher.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                publisher.setId(0);
                fixPositions(context);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return true;
            }
            return false;
        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            return false;

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Publisher source,
                          @NonNull final Publisher target)
            throws DaoInsertException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Publisher,
            // respecting the position of the Publisher in the list for each book.
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
                insertOrUpdate(context, bookId, false, destList,
                               true, book.getLocaleOrUserLocale(context));
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

        final List<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final Book book = Book.from(bookId);
                    final List<Publisher> list = getByBookId(bookId);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, false, list, false,
                                   book.getLocaleOrUserLocale(context));
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoInsertException e) {
                LoggerFactory.getLogger().e(TAG, e);

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }
        }
        return bookIds.size();
    }

    private static final class Sql {

        /**
         * Insert the link between a {@link Book} and a {@link Publisher}.
         */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_PUBLISHER.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_PUBLISHER
                + ',' + DBKey.BOOK_PUBLISHER_POSITION
                + ") VALUES(?,?,?)";
        /**
         * Delete the link between a {@link Book} and a {@link Publisher}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_PUBLISHER.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** All Books (id only!) for a given Publisher. */
        private static final String SELECT_BOOK_IDS_BY_PUBLISHER_ID =
                SELECT_ + TBL_BOOK_PUBLISHER.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_PUBLISHER.ref()
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
         * Searches PUBLISHER_NAME_OB on both original and (potentially) reordered name.
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
