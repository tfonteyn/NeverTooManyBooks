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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_OFFSET;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_POS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public final class BookshelfDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "BookshelfDao";

    private static final String INSERT =
            INSERT_INTO_ + TBL_BOOKSHELF.getName()
            + '(' + KEY_BOOKSHELF_NAME
            + ',' + KEY_FK_STYLE
            + ',' + KEY_BOOKSHELF_BL_TOP_POS
            + ',' + KEY_BOOKSHELF_BL_TOP_OFFSET
            + ") VALUES (?,?,?,?)";

    private static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
            DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE + _WHERE_ + KEY_FK_BOOKSHELF + "=?";

    /** Delete a {@link Bookshelf}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_BOOKSHELF.getName() + _WHERE_ + KEY_PK_ID + "=?";


    /**
     * Get the id of a {@link Bookshelf} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     */
    private static final String FIND_ID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
            + _WHERE_ + KEY_BOOKSHELF_NAME + "=?" + _COLLATION;

    /** All {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL =
            SELECT_ + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
            + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
            + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
            + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
            + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_STYLE_UUID)
            + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

    /** User defined {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL_USER_SHELVES =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + ">0"
            + _ORDER_BY_ + KEY_BOOKSHELF_NAME + _COLLATION;

    /** Get a {@link Bookshelf} by the Bookshelf id; linked with the styles table. */
    private static final String SELECT_BY_ID =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + "=?";

    /** Get a {@link Bookshelf} by its name; linked with the styles table. */
    private static final String FIND =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + "=?" + _COLLATION;


    /** Singleton. */
    private static BookshelfDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private BookshelfDao(@NonNull final Context context,
                         @NonNull final String logTag) {
        super(context, logTag);
    }

    public static BookshelfDao getInstance() {
        if (sInstance == null) {
            sInstance = new BookshelfDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get the {@link Bookshelf} based on the given id.
     *
     * <strong>Don't call directly; instead use a static method in {@link Bookshelf}</strong>.
     *
     * @param id of Bookshelf to find
     *
     * @return the {@link Bookshelf}, or {@code null} if not found
     */
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

    /**
     * Find a {@link Bookshelf} with the given name.
     *
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf findByName(@NonNull final String name) {

        try (Cursor cursor = mDb.rawQuery(FIND, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(KEY_PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Bookshelf} by using the appropriate fields of the passed {@link Bookshelf}.
     * The incoming object is not modified.
     *
     * @param bookshelf to find the id of
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long find(@NonNull final Bookshelf bookshelf) {

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Convenience method, fetch all shelves, and return them as a List.
     *
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return a list of all bookshelves in the database.
     */
    @NonNull
    public ArrayList<Bookshelf> getAll() {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchAllUserShelves()) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get all Bookshelves; mainly for the purpose of backups.
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return Cursor over all Bookshelves
     */
    @NonNull
    public Cursor fetchAllUserShelves() {
        return mDb.rawQuery(SELECT_ALL_USER_SHELVES, null);
    }

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     *
     * @param bookshelf to update
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Bookshelf bookshelf) {
        final long id = find(bookshelf);
        bookshelf.setId(id);
        return id;
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param context   Current context
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf /* in/out */ bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, bookshelf.getName());
            stmt.bindLong(2, styleId);
            stmt.bindLong(3, bookshelf.getTopItemPosition());
            stmt.bindLong(4, bookshelf.getTopViewOffset());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                bookshelf.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update a bookshelf.
     *
     * @param context   Current context
     * @param bookshelf to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        final ContentValues cv = new ContentValues();
        cv.put(KEY_BOOKSHELF_NAME, bookshelf.getName());
        cv.put(KEY_BOOKSHELF_BL_TOP_POS, bookshelf.getTopItemPosition());
        cv.put(KEY_BOOKSHELF_BL_TOP_OFFSET, bookshelf.getTopViewOffset());

        cv.put(KEY_FK_STYLE, styleId);

        return 0 < mDb.update(TBL_BOOKSHELF.getName(), cv, KEY_PK_ID + "=?",
                              new String[]{String.valueOf(bookshelf.getId())});
    }

    /**
     * Delete the passed {@link Bookshelf}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} if a row was deleted
     */
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

    /**
     * Moves all books from the 'source' {@link Bookshelf}, to the 'destId' {@link Bookshelf}.
     * The (now unused) 'source' {@link Bookshelf} is deleted.
     *
     * @param source from where to move
     * @param destId to move to
     *
     * @return the amount of books moved.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int merge(@NonNull final Bookshelf source,
                     final long destId) {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_BOOKSHELF, destId);

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // we don't hold 'position' for shelves... so just do a mass update
            rowsAffected = mDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                      KEY_FK_BOOKSHELF + "=?",
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

    /**
     * Purge book list node state data for the given {@link Bookshelf}.
     * <p>
     * Called when a {@link Bookshelf} is deleted or manually from the
     * {@link Bookshelf} management context menu.
     *
     * @param bookshelfId to purge
     */
    public void purgeNodeStates(final long bookshelfId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(BOOK_LIST_NODE_STATE_BY_BOOKSHELF)) {
            stmt.bindLong(1, bookshelfId);
            stmt.executeUpdateDelete();
        }
    }
}
