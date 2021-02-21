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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_PREFERRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_MENU_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public final class StyleDao
        extends BaseDao {

    private static final String TAG = "StyleDao";

    /** {@link ListStyle} all columns. */
    private static final String SELECT_STYLES =
            "SELECT * FROM " + TBL_BOOKLIST_STYLES.getName();

    private static final String DELETE_BOOK_LIST_NODE_STATE_BY_STYLE =
            DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName() + _WHERE_ + KEY_FK_STYLE + "=?";

    private static final String INSERT_STYLE =
            INSERT_INTO_ + TBL_BOOKLIST_STYLES.getName()
            + '(' + KEY_STYLE_UUID
            + ',' + KEY_STYLE_IS_BUILTIN
            + ',' + KEY_STYLE_IS_PREFERRED
            + ',' + KEY_STYLE_MENU_POSITION
            + ") VALUES (?,?,?,?)";

    /** Delete a {@link ListStyle}. */
    private static final String DELETE_STYLE_BY_ID =
            DELETE_FROM_ + TBL_BOOKLIST_STYLES.getName() + _WHERE_ + KEY_PK_ID + "=?";

    /** Get the id of a {@link ListStyle} by UUID. */
    private static final String SELECT_STYLE_ID_BY_UUID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKLIST_STYLES.getName()
            + _WHERE_ + KEY_STYLE_UUID + "=?";

    /** Singleton. */
    private static StyleDao sInstance;

    private StyleDao(@NonNull final Context context,
                     @NonNull final String logTag) {
        super(context, logTag);
    }

    public static StyleDao getInstance() {
        if (sInstance == null) {
            sInstance = new StyleDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get the id of a {@link ListStyle} with matching UUID.
     *
     * @param uuid UUID of the style to find
     *
     * @return id
     */
    public long getStyleIdByUuid(@NonNull final String uuid) {

        try (SynchronizedStatement stmt = mDb.compileStatement(SELECT_STYLE_ID_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get a cursor over the Styles table.
     *
     * @param userDefined {@code true} to get the User defined styles,
     *                    {@code false} to get the builtin styles
     *
     * @return cursor
     */
    @NonNull
    public Cursor fetchStyles(final boolean userDefined) {

        // We order by the id, i.e. in the order the styles were created.
        // This is only done to get a reproducible and consistent order.
        final String sql =
                SELECT_STYLES + _WHERE_ + KEY_STYLE_IS_BUILTIN + "=?" + _ORDER_BY_ + KEY_PK_ID;

        return mDb.rawQuery(sql, new String[]{String.valueOf(userDefined ? 0 : 1)});
    }

    /**
     * Create a new {@link ListStyle}.
     *
     * @param style to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final ListStyle style) {
        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT_STYLE)) {
            stmt.bindString(1, style.getUuid());
            stmt.bindBoolean(2, style instanceof BuiltinStyle);
            stmt.bindBoolean(3, style.isPreferred());
            stmt.bindLong(4, style.getMenuPosition());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update a {@link ListStyle}.
     *
     * @param style to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final ListStyle style) {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_STYLE_IS_PREFERRED, style.isPreferred());
        cv.put(KEY_STYLE_MENU_POSITION, style.getMenuPosition());

        return 0 < mDb.update(TBL_BOOKLIST_STYLES.getName(), cv, KEY_PK_ID + "=?",
                              new String[]{String.valueOf(style.getId())});
    }

    /**
     * Delete a {@link ListStyle}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param style to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final ListStyle style) {

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            purgeNodeStatesByStyle(style.getId());

            try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_STYLE_BY_ID)) {
                stmt.bindLong(1, style.getId());
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
            style.setId(0);
        }
        return rowsAffected == 1;
    }

    /**
     * Purge Booklist node state data for the given Style.<br>
     * Called when a style is deleted or manually from the Styles management context menu.
     *
     * @param styleId to purge
     */
    public void purgeNodeStatesByStyle(final long styleId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(DELETE_BOOK_LIST_NODE_STATE_BY_STYLE)) {
            stmt.bindLong(1, styleId);
            stmt.executeUpdateDelete();
        }
    }
}
