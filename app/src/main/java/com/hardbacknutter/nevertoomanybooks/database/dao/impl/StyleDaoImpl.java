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

import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public class StyleDaoImpl
        extends BaseDaoImpl
        implements StyleDao {

    private static final String TAG = "StyleDaoImpl";

    /** {@link ListStyle} all columns. */
    private static final String SELECT_STYLES =
            "SELECT * FROM " + TBL_BOOKLIST_STYLES.getName();

    /**
     * We order by the id, i.e. in the order the styles were created.
     * This is only done to get a reproducible and consistent order.
     */
    private static final String SELECT_STYLES_BY_TYPE =
            SELECT_STYLES
            + _WHERE_ + DBKey.BOOL_STYLE_IS_BUILTIN + "=?"
            + _ORDER_BY_ + DBKey.PK_ID;

    private static final String DELETE_BOOK_LIST_NODE_STATE_BY_STYLE =
            DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
            + _WHERE_ + DBKey.FK_STYLE + "=?";

    private static final String INSERT_STYLE =
            INSERT_INTO_ + TBL_BOOKLIST_STYLES.getName()
            + '(' + DBKey.KEY_STYLE_UUID
            + ',' + DBKey.BOOL_STYLE_IS_BUILTIN
            + ',' + DBKey.BOOL_STYLE_IS_PREFERRED
            + ',' + DBKey.KEY_STYLE_MENU_POSITION
            + ") VALUES (?,?,?,?)";

    /** Delete a {@link ListStyle}. */
    private static final String DELETE_STYLE_BY_ID =
            DELETE_FROM_ + TBL_BOOKLIST_STYLES.getName() + _WHERE_ + DBKey.PK_ID + "=?";

    /** Get the id of a {@link ListStyle} by UUID. */
    private static final String SELECT_STYLE_ID_BY_UUID =
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_BOOKLIST_STYLES.getName()
            + _WHERE_ + DBKey.KEY_STYLE_UUID + "=?";

    /**
     * Constructor.
     */
    public StyleDaoImpl() {
        super(TAG);
    }

    @Override
    public long getStyleIdByUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = mDb.compileStatement(SELECT_STYLE_ID_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public Map<String, UserStyle> getUserStyles(@NonNull final Context context) {
        final Map<String, UserStyle> map = new LinkedHashMap<>();

        try (Cursor cursor = mDb.rawQuery(SELECT_STYLES_BY_TYPE,
                                          new String[]{String.valueOf(0)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final UserStyle style = UserStyle.createFromDatabase(context, rowData);
                map.put(style.getUuid(), style);
            }
        }

        return map;
    }

    @Override
    @NonNull
    public Map<String, BuiltinStyle> getBuiltinStyles(@NonNull final Context context) {
        final Map<String, BuiltinStyle> map = new LinkedHashMap<>();

        try (Cursor cursor = mDb.rawQuery(SELECT_STYLES_BY_TYPE,
                                          new String[]{String.valueOf(1)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final BuiltinStyle style = BuiltinStyle.createFromDatabase(context, rowData);
                map.put(style.getUuid(), style);
            }
        }

        return map;
    }

    @Override
    public long insert(@NonNull final ListStyle style) {
        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT_STYLE)) {
            stmt.bindString(1, style.getUuid());
            stmt.bindBoolean(2, !style.isUserDefined());
            stmt.bindBoolean(3, style.isPreferred());
            stmt.bindLong(4, style.getMenuPosition());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final ListStyle style) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.BOOL_STYLE_IS_PREFERRED, style.isPreferred());
        cv.put(DBKey.KEY_STYLE_MENU_POSITION, style.getMenuPosition());

        return 0 < mDb.update(TBL_BOOKLIST_STYLES.getName(), cv, DBKey.PK_ID + "=?",
                              new String[]{String.valueOf(style.getId())});
    }

    @Override
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

    @Override
    public void purgeNodeStatesByStyle(final long styleId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(DELETE_BOOK_LIST_NODE_STATE_BY_STYLE)) {
            stmt.bindLong(1, styleId);
            stmt.executeUpdateDelete();
        }
    }
}
