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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.BookshelfMergeHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public class BookshelfDaoImpl
        extends BaseDaoImpl
        implements BookshelfDao {

    /** Log tag. */
    private static final String TAG = "BookshelfDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public BookshelfDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at installation time to add the 'all' and default shelves to the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL(INSERT_INTO_ + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL(INSERT_INTO_ + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');
    }

    @NonNull
    @Override
    public Optional<Bookshelf> getById(final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Bookshelf(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Bookshelf> findByName(@NonNull final String name) {

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Bookshelf> findByName(@NonNull final Bookshelf bookshelf) {
        return findByName(bookshelf.getName());
    }

    @Override
    @NonNull
    public List<Bookshelf> getAll() {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchAllUserShelves()) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAllUserShelves() {
        return db.rawQuery(Sql.SELECT_ALL_USER_SHELVES, null);
    }

    @NonNull
    @Override
    public List<PFilter<?>> getFilters(final long bookshelfId) {
        final List<PFilter<?>> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_FILTERS,
                                         new String[]{String.valueOf(bookshelfId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final String dbKey = rowData.getString(DBKey.FILTER_DBKEY);
                final String value = rowData.getString(DBKey.FILTER_VALUE, null);
                // setPersistedValue accepts null values, but is there any point using null?
                if (value != null) {
                    final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                    if (filter != null) {
                        filter.setPersistedValue(value);
                        list.add(filter);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Store the <strong>active filter</strong>.
     *
     * @param context     Current context
     * @param bookshelfId the Bookshelf id; passed separately to allow clean inserts
     * @param bookshelf   to store
     */
    private void storeFilters(@NonNull final Context context,
                              final long bookshelfId,
                              @NonNull final Bookshelf bookshelf) {

        // prune the filters so we only keep the active ones
        final List<PFilter<?>> list = bookshelf.pruneFilters(context);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_FILTERS)) {
                stmt.bindLong(1, bookshelfId);
                stmt.executeUpdateDelete();
            }

            if (list.isEmpty()) {
                return;
            }

            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_FILTER)) {
                list.forEach(filter -> {
                    stmt.bindLong(1, bookshelfId);
                    stmt.bindString(2, filter.getDBKey());
                    stmt.bindString(3, filter.getPersistedValue());
                    stmt.executeInsert();
                });
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

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Bookshelf> list) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final BookshelfMergeHelper mergeHelper = new BookshelfMergeHelper();
        return mergeHelper.merge(context, list, current -> userLocale,
                                 (current, locale) -> fixId(current));
    }

    @Override
    public void fixId(@NonNull final Bookshelf bookshelf) {
        bookshelf.setId(findByName(bookshelf).map(Bookshelf::getId).orElse(0L));
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * The list is pruned before storage.
     * New shelves are added, existing ones are NOT updated.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  of the book
     * @param list    the list of bookshelves
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               @NonNull final Collection<Bookshelf> list)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates; shelves don't use a Locale, hence no lookup done.
        pruneList(context, list);

        // Just delete all current links; we'll insert them from scratch.
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }


        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Bookshelf bookshelf : list) {
                // create if needed - do NOT do updates here
                if (bookshelf.getId() == 0) {
                    insert(context, bookshelf);
                }
                //2023-06-11: If we ever do updates here, then we need to check the triggers!
                // also: look at AuthorDaoImpl/PublisherDaoImpl how we avoid unneeded updates

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Bookshelf");
                }
            }
        }
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf)
            throws DaoWriteException {

        // validate the style first
        final long styleId = bookshelf.getStyle().getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, bookshelf.getBooklistAdapterPosition());
                stmt.bindLong(4, bookshelf.getFirstVisibleItemViewOffset());
                iId = stmt.executeInsert();
            }

            if (iId > 0) {
                storeFilters(context, iId, bookshelf);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }

                bookshelf.setId(iId);
                return iId;
            }

            throw new DaoWriteException(ERROR_INSERT_FROM + bookshelf);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_INSERT_FROM + bookshelf, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf)
            throws DaoWriteException {

        // validate the style first
        final long styleId = bookshelf.getStyle().getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final boolean success;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, bookshelf.getBooklistAdapterPosition());
                stmt.bindLong(4, bookshelf.getFirstVisibleItemViewOffset());
                stmt.bindLong(5, bookshelf.getId());

                success = 0 < stmt.executeUpdateDelete();
            }
            if (success) {
                storeFilters(context, bookshelf.getId(), bookshelf);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoWriteException(ERROR_UPDATE_FROM + bookshelf);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATE_FROM + bookshelf, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Bookshelf bookshelf) {

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            purgeNodeStates(bookshelf.getId());

            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, bookshelf.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        if (rowsAffected > 0) {
            bookshelf.setId(0);
        }
        return rowsAffected == 1;
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Bookshelf source,
                          @NonNull final Bookshelf target) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Bookshelf.
            // We don't hold 'position' for bookshelves... just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(DBKey.FK_BOOKSHELF, target.getId());
            db.update(TBL_BOOK_BOOKSHELF.getName(), cv, DBKey.FK_BOOKSHELF + "=?",
                      new String[]{String.valueOf(source.getId())});

            // delete the obsolete source.
            delete(source);

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
    public void purgeNodeStates(final long bookshelfId) {
        try (SynchronizedStatement stmt = db
                .compileStatement(Sql.BOOK_LIST_NODE_STATE_BY_BOOKSHELF)) {
            stmt.bindLong(1, bookshelfId);
            stmt.executeUpdateDelete();
        }
    }

    @NonNull
    @Override
    public List<Long> getBookIds(final long bookshelfId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_IDS_BY_BOOKSHELF_ID,
                                         new String[]{String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Bookshelf> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.BOOKSHELVES_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
            return list;
        }
    }

    private static final class Sql {

        /**
         * Insert the link between a {@link Book} and a {@link Bookshelf}.
         */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_BOOKSHELF.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_BOOKSHELF
                + ") VALUES (?,?)";
        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";
        private static final String INSERT =
                INSERT_INTO_ + TBL_BOOKSHELF.getName()
                + '(' + DBKey.BOOKSHELF_NAME
                + ',' + DBKey.FK_STYLE
                + ',' + DBKey.BOOKSHELF_BL_TOP_POS
                + ',' + DBKey.BOOKSHELF_BL_TOP_OFFSET
                + ") VALUES (?,?,?,?)";

        private static final String UPDATE =
                UPDATE_ + TBL_BOOKSHELF.getName()
                + _SET_ + DBKey.BOOKSHELF_NAME + "=?"
                + ',' + DBKey.FK_STYLE + "=?"
                + ',' + DBKey.BOOKSHELF_BL_TOP_POS + "=?"
                + ',' + DBKey.BOOKSHELF_BL_TOP_OFFSET + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        private static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
                DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

        /** Delete a {@link Bookshelf}. */
        private static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Get the id of a {@link Bookshelf} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        private static final String FIND_ID =
                SELECT_ + DBKey.PK_ID
                + _FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + DBKey.BOOKSHELF_NAME + "=?" + _COLLATION;

        /** All {@link Bookshelf}, all columns; linked with the styles table. */
        private static final String SELECT_ALL =
                SELECT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                              DBKey.BOOKSHELF_NAME,
                                              DBKey.BOOKSHELF_BL_TOP_POS,
                                              DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                              DBKey.FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)
                + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOKLIST_STYLES);

        /** Get a {@link Bookshelf} by its name; linked with the styles table. */
        private static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + "=?" + _COLLATION;

        /** Get a {@link Bookshelf} by the Bookshelf id; linked with the styles table. */
        private static final String SELECT_BY_ID =
                SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + "=?";

        /** User defined {@link Bookshelf}, all columns; linked with the styles table. */
        private static final String SELECT_ALL_USER_SHELVES =
                SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + ">0"
                + _ORDER_BY_ + DBKey.BOOKSHELF_NAME + _COLLATION;

        /** All Bookshelves for a Book; ordered by name. */
        private static final String BOOKSHELVES_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                                       DBKey.BOOKSHELF_NAME,
                                                       DBKey.BOOKSHELF_BL_TOP_POS,
                                                       DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                                       DBKey.FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)

                + _FROM_ + TBL_BOOK_BOOKSHELF.startJoin(TBL_BOOKSHELF, TBL_BOOKLIST_STYLES)
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + _COLLATION;

        /** All Books (id only!) for a given Bookshelf. */
        private static final String SELECT_BOOK_IDS_BY_BOOKSHELF_ID =
                SELECT_ + TBL_BOOK_BOOKSHELF.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?";

        private static final String SELECT_FILTERS =
                SELECT_ + DBKey.FILTER_DBKEY + ',' + DBKey.FILTER_VALUE
                + _FROM_ + TBL_BOOKSHELF_FILTERS.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

        private static final String DELETE_FILTERS =
                DELETE_FROM_ + TBL_BOOKSHELF_FILTERS.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

        private static final String INSERT_FILTER =
                INSERT_INTO_ + TBL_BOOKSHELF_FILTERS.getName()
                + '(' + DBKey.FK_BOOKSHELF
                + ',' + DBKey.FILTER_DBKEY
                + ',' + DBKey.FILTER_VALUE
                + ") VALUES (?,?,?)";
    }
}
