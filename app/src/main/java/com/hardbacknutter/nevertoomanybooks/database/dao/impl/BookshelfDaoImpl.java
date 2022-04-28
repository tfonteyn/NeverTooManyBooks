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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMerger;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public class BookshelfDaoImpl
        extends BaseDaoImpl
        implements BookshelfDao {

    /** Log tag. */
    private static final String TAG = "BookshelfDaoImpl";

    private static final String INSERT =
            INSERT_INTO_ + TBL_BOOKSHELF.getName()
            + '(' + DBKey.KEY_BOOKSHELF_NAME
            + ',' + DBKey.FK_STYLE
            + ',' + DBKey.KEY_BOOKSHELF_BL_TOP_POS
            + ',' + DBKey.KEY_BOOKSHELF_BL_TOP_OFFSET
            + ") VALUES (?,?,?,?)";

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
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
            + _WHERE_ + DBKey.KEY_BOOKSHELF_NAME + "=?" + _COLLATION;

    /** All {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL =
            SELECT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                          DBKey.KEY_BOOKSHELF_NAME,
                                          DBKey.KEY_BOOKSHELF_BL_TOP_POS,
                                          DBKey.KEY_BOOKSHELF_BL_TOP_OFFSET,
                                          DBKey.FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.KEY_STYLE_UUID)
            + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOKLIST_STYLES);

    /** User defined {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL_USER_SHELVES =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + ">0"
            + _ORDER_BY_ + DBKey.KEY_BOOKSHELF_NAME + _COLLATION;

    /** Get a {@link Bookshelf} by the Bookshelf id; linked with the styles table. */
    private static final String SELECT_BY_ID =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + "=?";

    /** Get a {@link Bookshelf} by its name; linked with the styles table. */
    private static final String FIND =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.KEY_BOOKSHELF_NAME) + "=?"
            + _COLLATION;

    /** All Bookshelves for a Book; ordered by name. */
    private static final String BOOKSHELVES_BY_BOOK_ID =
            SELECT_DISTINCT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                                   DBKey.KEY_BOOKSHELF_NAME,
                                                   DBKey.KEY_BOOKSHELF_BL_TOP_POS,
                                                   DBKey.KEY_BOOKSHELF_BL_TOP_OFFSET,
                                                   DBKey.FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.KEY_STYLE_UUID)

            + _FROM_ + TBL_BOOK_BOOKSHELF.startJoin(TBL_BOOKSHELF, TBL_BOOKLIST_STYLES)
            + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOKSHELF.dot(DBKey.KEY_BOOKSHELF_NAME) + _COLLATION;

    /**
     * Constructor.
     */
    public BookshelfDaoImpl() {
        super(TAG);
    }

    @Override
    @Nullable
    public Bookshelf getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
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

        try (Cursor cursor = mDb.rawQuery(FIND, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    @Override
    public long find(@NonNull final Bookshelf bookshelf) {

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
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
        return mDb.rawQuery(SELECT_ALL_USER_SHELVES, null);
    }

    @Override
    public boolean pruneList(@NonNull final Collection<Bookshelf> list) {
        if (list.isEmpty()) {
            return false;
        }

        final EntityMerger<Bookshelf> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Bookshelf current = entityMerger.next();
            fixId(current);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    @Override
    public long fixId(@NonNull final Bookshelf bookshelf) {
        final long id = find(bookshelf);
        bookshelf.setId(id);
        return id;
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, bookshelf.getName());
            stmt.bindLong(2, styleId);
            stmt.bindLong(3, bookshelf.getFirstVisibleItemPosition());
            stmt.bindLong(4, bookshelf.getFirstVisibleItemViewOffset());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                bookshelf.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.KEY_BOOKSHELF_NAME, bookshelf.getName());
        cv.put(DBKey.KEY_BOOKSHELF_BL_TOP_POS, bookshelf.getFirstVisibleItemPosition());
        cv.put(DBKey.KEY_BOOKSHELF_BL_TOP_OFFSET, bookshelf.getFirstVisibleItemViewOffset());

        cv.put(DBKey.FK_STYLE, styleId);

        return 0 < mDb.update(TBL_BOOKSHELF.getName(), cv, DBKey.PK_ID + "=?",
                              new String[]{String.valueOf(bookshelf.getId())});
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Bookshelf bookshelf) {

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            purgeNodeStates(bookshelf.getId());

            try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
                stmt.bindLong(1, bookshelf.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        if (rowsAffected > 0) {
            bookshelf.setId(0);
        }
        return rowsAffected == 1;
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public int merge(@NonNull final Bookshelf source,
                     final long destId) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.FK_BOOKSHELF, destId);

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // we don't hold 'position' for shelves... so just do a mass update
            rowsAffected = mDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                      DBKey.FK_BOOKSHELF + "=?",
                                      new String[]{String.valueOf(source.getId())});

            // delete the obsolete source.
            delete(source);

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        return rowsAffected;
    }

    @Override
    public void purgeNodeStates(final long bookshelfId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(BOOK_LIST_NODE_STATE_BY_BOOKSHELF)) {
            stmt.bindLong(1, bookshelfId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    @NonNull
    public ArrayList<Bookshelf> getBookshelvesByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(BOOKSHELVES_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
            return list;
        }
    }
}
