/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.PublisherMergeHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

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
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ID, new String[]{String.valueOf(id)})) {
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
        final String name = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, name, locale);

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                SqlEncode.orderByColumn(name, locale),
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
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
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
        try (Cursor cursor = db.rawQuery(Sql.FIND_BOOK_IDS_BY_PUBLISHER_ID,
                                         new String[]{String.valueOf(publisherId)})) {
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
    public long countBooks(@NonNull final Publisher publisher) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_BOOKS)) {
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
                               @NonNull final Function<Publisher, Locale> localeSupplier)
            throws DaoInsertException, DaoUpdateException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

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
                final Locale locale = localeSupplier.apply(publisher);
                fixId(context, publisher, locale);

                // create if needed - do NOT do updates unless explicitly allowed
                if (publisher.getId() == 0) {
                    insert(context, publisher, locale);
                } else if (doUpdates) {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise the trigger "after_update_on" + TBL_PUBLISHER
                    // would set DATE_LAST_UPDATED__UTC for ALL books by that publisher
                    // while not needed.
                    final Optional<Publisher> found = findById(publisher.getId());
                    // Check for the name AND user fields being equals.
                    if (found.isPresent() && !found.get().isIdentical(publisher)) {
                        update(context, publisher, locale);
                    }
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
                       @NonNull final Locale locale)
            throws DaoInsertException {

        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String name = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, name, locale);

        final long iId;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindString(1, name);
            stmt.bindString(2, SqlEncode.orderByColumn(obName, locale));
            iId = stmt.executeInsert();
        }

        if (iId != -1) {
            publisher.setId(iId);
            return iId;
        }

        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + publisher);
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Publisher publisher,
                       @NonNull final Locale locale)
            throws DaoUpdateException {

        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String text = publisher.getName();
        final String obName = reorderHelper.reorderForSorting(context, text, locale);

        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(obName, locale));

            stmt.bindLong(3, publisher.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            return;
        }

        throw new DaoUpdateException(ERROR_UPDATE_FROM + publisher);
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
        } catch (@NonNull final DaoWriteException e) {
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
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Publisher,
            // respecting the position of the Publisher in the list for each book.
            for (final long bookId : getBookIds(source.getId())) {
                final Book book = Book.from(bookId);

                final List<Publisher> fromBook = book.getPublishers();
                final List<Publisher> destList = new ArrayList<>();

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
                final Locale bookLocale = book.getLocaleOrUserLocale(context);
                insertOrUpdate(context, bookId, false, destList, publisher -> bookLocale);
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
    @WorkerThread
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int fixPositions(@NonNull final Context context)
            throws DaoWriteException {

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
                    final Locale bookLocale = book.getLocaleOrUserLocale(context);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, false, list, publisher -> bookLocale);
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }
        }
        return bookIds.size();
    }

    private static final class Sql {

        /** Insert a {@link Publisher}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_PUBLISHERS.getName()
                + '(' + DBKey.PUBLISHER_NAME
                + ',' + DBKey.PUBLISHER_NAME_OB
                + ") VALUES (?,?)";

        /** Update a {@link Publisher}. */
        static final String UPDATE =
                UPDATE_ + TBL_PUBLISHERS.getName()
                + _SET_ + DBKey.PUBLISHER_NAME + "=?"
                + ',' + DBKey.PUBLISHER_NAME_OB + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Publisher}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge all {@link Publisher}s which are no longer in use. */
        static final String PURGE =
                DELETE_FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_PUBLISHER
                + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';

        /** Insert the link between a {@link Book} and a {@link Publisher}. */
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

        /** Get a count of the {@link Publisher}s. */
        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_PUBLISHERS.getName();

        /** Count the number of {@link Book}'s by an {@link Publisher}. */
        static final String COUNT_BOOKS =
                SELECT_ + "COUNT(" + DBKey.FK_BOOK + ')'
                + _FROM_ + TBL_BOOK_PUBLISHER.getName()
                + _WHERE_ + DBKey.FK_PUBLISHER + "=?";


        /** A list of all {@link Publisher}s, unordered. */
        static final String SELECT_ALL = "SELECT * FROM " + TBL_PUBLISHERS.getName();

        /** Find a {@link Publisher} by its id. */
        static final String FIND_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Find a {@link Publisher} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches PUBLISHER_NAME_OB on both original and (potentially) reordered name.
         */
        static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION
                + _OR_ + DBKey.PUBLISHER_NAME_OB + "=?" + _COLLATION;


        /**
         * All {@link Publisher}s for a {@link Book}.
         * Ordered by position in the book.
         */
        static final String FIND_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_PUBLISHERS.dotAs(DBKey.PK_ID,
                                                        DBKey.PUBLISHER_NAME,
                                                        DBKey.PUBLISHER_NAME_OB)
                + ',' + TBL_BOOK_PUBLISHER.dotAs(DBKey.BOOK_PUBLISHER_POSITION)

                + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(DBKey.BOOK_PUBLISHER_POSITION);

        /** All {@link Book}s (id only!) for a given {@link Publisher}. */
        static final String FIND_BOOK_IDS_BY_PUBLISHER_ID =
                SELECT_ + TBL_BOOK_PUBLISHER.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_PUBLISHER.ref()
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_PUBLISHER) + "=?";

        /** Get a list of {@link Publisher} names for use in a dropdown selection. */
        static final String SELECT_ALL_NAMES =
                SELECT_DISTINCT_ + DBKey.PUBLISHER_NAME
                + ',' + DBKey.PUBLISHER_NAME_OB
                + _FROM_ + TBL_PUBLISHERS.getName()
                + _ORDER_BY_ + DBKey.PUBLISHER_NAME_OB + _COLLATION;

        static final String REPOSITION =
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
