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

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

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
            + '(' + DBKeys.KEY_BOOKSHELF_NAME
            + ',' + DBKeys.KEY_FK_STYLE
            + ',' + DBKeys.KEY_BOOKSHELF_BL_TOP_POS
            + ',' + DBKeys.KEY_BOOKSHELF_BL_TOP_OFFSET
            + ") VALUES (?,?,?,?)";

    private static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
            DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE + _WHERE_ + DBKeys.KEY_FK_BOOKSHELF + "=?";

    /** Delete a {@link Bookshelf}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_BOOKSHELF.getName() + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Bookshelf} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     */
    private static final String FIND_ID =
            SELECT_ + DBKeys.KEY_PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
            + _WHERE_ + DBKeys.KEY_BOOKSHELF_NAME + "=?" + _COLLATION;

    /** All {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL =
            SELECT_ + TBL_BOOKSHELF.dotAs(DBKeys.KEY_PK_ID)
            + ',' + TBL_BOOKSHELF.dotAs(DBKeys.KEY_BOOKSHELF_NAME)
            + ',' + TBL_BOOKSHELF.dotAs(DBKeys.KEY_BOOKSHELF_BL_TOP_POS)
            + ',' + TBL_BOOKSHELF.dotAs(DBKeys.KEY_BOOKSHELF_BL_TOP_OFFSET)
            + ',' + TBL_BOOKSHELF.dotAs(DBKeys.KEY_FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKeys.KEY_STYLE_UUID)
            + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

    /** User defined {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL_USER_SHELVES =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKeys.KEY_PK_ID) + ">0"
            + _ORDER_BY_ + DBKeys.KEY_BOOKSHELF_NAME + _COLLATION;

    /** Get a {@link Bookshelf} by the Bookshelf id; linked with the styles table. */
    private static final String SELECT_BY_ID =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKeys.KEY_PK_ID) + "=?";

    /** Get a {@link Bookshelf} by its name; linked with the styles table. */
    private static final String FIND =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKeys.KEY_BOOKSHELF_NAME) + "=?"
            + _COLLATION;

    /**
     * Constructor.
     */
    public BookshelfDaoImpl() {
        super(TAG);
    }

    @Override
    @Nullable
    public Bookshelf getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_BY_ID,
                                          new String[]{String.valueOf(id)})) {
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
                return new Bookshelf(rowData.getLong(DBKeys.KEY_PK_ID), rowData);
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
    public ArrayList<Bookshelf> getAll() {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchAllUserShelves()) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKeys.KEY_PK_ID), rowData));
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
            stmt.bindLong(4, bookshelf.getTopViewOffset());
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
        cv.put(DBKeys.KEY_BOOKSHELF_NAME, bookshelf.getName());
        cv.put(DBKeys.KEY_BOOKSHELF_BL_TOP_POS, bookshelf.getFirstVisibleItemPosition());
        cv.put(DBKeys.KEY_BOOKSHELF_BL_TOP_OFFSET, bookshelf.getTopViewOffset());

        cv.put(DBKeys.KEY_FK_STYLE, styleId);

        return 0 < mDb.update(TBL_BOOKSHELF.getName(), cv, DBKeys.KEY_PK_ID + "=?",
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
        cv.put(DBKeys.KEY_FK_BOOKSHELF, destId);

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // we don't hold 'position' for shelves... so just do a mass update
            rowsAffected = mDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                      DBKeys.KEY_FK_BOOKSHELF + "=?",
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
}
