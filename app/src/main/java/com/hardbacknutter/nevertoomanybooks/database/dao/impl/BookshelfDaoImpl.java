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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.BookshelfMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

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
     */
    public BookshelfDaoImpl() {
        super(TAG);
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
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');
    }

    @Override
    @Nullable
    public Bookshelf getById(final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Bookshelf(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    @Override
    @Nullable
    public Bookshelf findByName(@NonNull final String name) {

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    @Override
    public Bookshelf findByName(@NonNull final Bookshelf bookshelf) {
        return findByName(bookshelf.getName());
    }

    @Override
    @NonNull
    public List<Bookshelf> getAll() {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchAllUserShelves()) {
            final DataHolder rowData = new CursorRow(cursor);
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
            final DataHolder rowData = new CursorRow(cursor);
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
    public boolean pruneList(@NonNull final Collection<Bookshelf> list) {
        if (list.isEmpty()) {
            return false;
        }

        final BookshelfMergeHelper mergeHelper = new BookshelfMergeHelper();
        return mergeHelper.merge(list, this::fixId);
    }

    @Override
    public void fixId(@NonNull final Bookshelf bookshelf) {
        final Bookshelf found = findByName(bookshelf);
        bookshelf.setId(found == null ? 0 : found.getId());
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf)
            throws DaoWriteException {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, bookshelf.getFirstVisibleItemPosition());
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
        final long styleId = bookshelf.getStyle(context).getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final boolean success;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, bookshelf.getFirstVisibleItemPosition());
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

    @Override
    @NonNull
    public ArrayList<Bookshelf> getByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.BOOKSHELVES_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
            return list;
        }
    }

    private static class Sql {

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
