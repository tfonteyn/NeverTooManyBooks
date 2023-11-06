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
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class StyleDaoImpl
        extends BaseDaoImpl
        implements StyleDao {

    private static final String TAG = "StyleDaoImpl";

    /** {@link Style} all columns. */
    private static final String SELECT_STYLES =
            "SELECT * FROM " + DBDefinitions.TBL_BOOKLIST_STYLES.getName();

    /**
     * We order by the id, i.e. in the order the styles were created.
     * This is only done to get a reproducible and consistent order.
     */
    private static final String SELECT_STYLES_BY_TYPE =
            SELECT_STYLES
            + _WHERE_ + DBKey.STYLE_TYPE + "=?"
            + _ORDER_BY_ + DBKey.PK_ID;

    private static final String DELETE_BOOK_LIST_NODE_STATE_BY_STYLE =
            DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE.getName()
            + _WHERE_ + DBKey.FK_STYLE + "=?";

    private static final String INSERT_BUILTIN_STYLE =
            INSERT_INTO_ + DBDefinitions.TBL_BOOKLIST_STYLES.getName()
            + '(' + DBKey.PK_ID
            + ',' + DBKey.STYLE_UUID
            + ',' + DBKey.STYLE_TYPE
            + ',' + DBKey.STYLE_IS_PREFERRED
            + ',' + DBKey.STYLE_MENU_POSITION
            + ") VALUES(?,?,?,?,?)";

    /** Delete a {@link Style}. */
    private static final String DELETE_STYLE_BY_ID =
            DELETE_FROM_ + DBDefinitions.TBL_BOOKLIST_STYLES.getName()
            + _WHERE_ + DBKey.PK_ID + "=?";

    /** Get the id of a {@link Style} by UUID. */
    private static final String SELECT_STYLE_ID_BY_UUID =
            SELECT_ + DBKey.PK_ID + _FROM_ + DBDefinitions.TBL_BOOKLIST_STYLES.getName()
            + _WHERE_ + DBKey.STYLE_UUID + "=?";

    private static final String INSERT_STYLE;

    static {
        final StringBuilder tmp = new StringBuilder(
                INSERT_INTO_ + DBDefinitions.TBL_BOOKLIST_STYLES.getName()
                + '(' + DBKey.STYLE_UUID
                + ',' + DBKey.STYLE_TYPE
                + ',' + DBKey.STYLE_IS_PREFERRED
                + ',' + DBKey.STYLE_MENU_POSITION
                + ',' + DBKey.STYLE_NAME

                + ',' + DBKey.STYLE_LAYOUT
                + ',' + DBKey.STYLE_COVER_CLICK_ACTION
                + ',' + DBKey.STYLE_COVER_SCALE
                + ',' + DBKey.STYLE_TEXT_SCALE
                + ',' + DBKey.STYLE_ROW_USES_PREF_HEIGHT

                + ',' + DBKey.STYLE_LIST_HEADER
                + ',' + DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY
                + ',' + DBKey.STYLE_BOOK_LEVEL_FIELDS_ORDER_BY
                + ',' + DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME

                + ',' + DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME
                + ',' + DBKey.STYLE_PUBLISHER_SHOW_REORDERED
                + ',' + DBKey.STYLE_TITLE_SHOW_REORDERED

                + ',' + DBKey.STYLE_DETAILS_SHOW_FIELDS

                + ',' + DBKey.STYLE_EXP_LEVEL
                + ',' + DBKey.STYLE_GROUPS
                + ',' + DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE);

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            tmp.append(',').append(item.getDbKey());
        }

        tmp.append(") VALUES (");

        final int count = (int) (tmp.chars().filter(ch -> ch == ',').count() + 1);

        tmp.append(String.join(",", Collections.nCopies(count, "?"))).append(")");

        INSERT_STYLE = tmp.toString();
    }

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public StyleDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at <strong>installation</strong> time to add the builtin style ID's to the database.
     *
     * @param db Database Access
     */
    public static void onPostCreate(@NonNull final SQLiteDatabase db) {
        insertGlobalDefaults(db, new GlobalStyle());

        // insert the builtin styles so foreign key rules are possible.
        // Other than the id/uuid/type and the menu options, the settings are never
        // read/written from/to the database.
        try (SQLiteStatement stmt = db.compileStatement(INSERT_BUILTIN_STYLE)) {
            int menuPos = 1;
            for (final BuiltinStyle.Definition styleDef : BuiltinStyle.getAll()) {
                stmt.bindLong(1, styleDef.getId());
                stmt.bindString(2, styleDef.getUuid());
                stmt.bindLong(3, StyleType.Builtin.getId());
                // preferred: false
                stmt.bindLong(4, 0);
                // menu position, initially just in the order defined.
                stmt.bindLong(5, menuPos++);

                // after inserting the id '-1' debug logging will claim that the insert failed.
                if (BuildConfig.DEBUG /* always */) {
                    if (styleDef.getId() == -1) {
                        LoggerFactory.getLogger().d(TAG, "onPostCreate",
                                                    "Ignore debug message inserting -1 ^^^");
                    }
                }
                stmt.executeInsert();
            }
        }
    }

    /**
     * Create and insert the single global/defaults style.
     * Used during app installation (and upgrade).
     *
     * @param db    Database Access
     * @param style the defaults
     */
    public static void insertGlobalDefaults(@NonNull final SQLiteDatabase db,
                                            @NonNull final Style style) {
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(INSERT_STYLE))) {
            doInsert(style, null, stmt);
        }
    }

    @NonNull
    private static String getGroupIdsAsCsv(@NonNull final Style style) {
        return style.getGroupList()
                    .stream()
                    .map(group -> String.valueOf(group.getId()))
                    .collect(Collectors.joining(","));
    }

    private static long doInsert(@NonNull final Style style,
                                 @Nullable final String styleName,
                                 @NonNull final ExtSQLiteStatement stmt) {
        int c = 0;
        stmt.bindString(++c, style.getUuid());
        stmt.bindLong(++c, style.getType().getId());
        stmt.bindBoolean(++c, style.isPreferred());
        stmt.bindLong(++c, style.getMenuPosition());
        stmt.bindString(++c, styleName);

        stmt.bindLong(++c, style.getLayout().getId());
        stmt.bindLong(++c, style.getCoverClickAction().getId());
        stmt.bindLong(++c, style.getCoverScale());
        stmt.bindLong(++c, style.getTextScale());
        stmt.bindBoolean(++c, style.isGroupRowUsesPreferredHeight());

        stmt.bindLong(++c, style.getHeaderFieldVisibilityValue());
        stmt.bindLong(++c, style.getFieldVisibilityValue(FieldVisibility.Screen.List));
        stmt.bindString(++c, StyleCoder.getBookLevelFieldsOrderByAsJsonString(style));
        stmt.bindBoolean(++c, style.isSortAuthorByGivenName());

        stmt.bindBoolean(++c, style.isShowAuthorByGivenName());
        stmt.bindBoolean(++c, style.isShowReorderedPublisherName());
        stmt.bindBoolean(++c, style.isShowReorderedTitle());

        stmt.bindLong(++c, style.getFieldVisibilityValue(FieldVisibility.Screen.Detail));

        stmt.bindLong(++c, style.getExpansionLevel());
        stmt.bindString(++c, getGroupIdsAsCsv(style));
        stmt.bindLong(++c, style.getPrimaryAuthorType());
        for (final Style.UnderEach item : Style.UnderEach.values()) {
            stmt.bindBoolean(++c, style.isShowBooksUnderEachGroup(item.getGroupId()));
        }

        return stmt.executeInsert();
    }

    @Override
    public long getStyleIdByUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = db.compileStatement(SELECT_STYLE_ID_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public Map<String, Style> getUserStyles() {
        final Map<String, Style> map = new LinkedHashMap<>();

        try (Cursor cursor = db.rawQuery(SELECT_STYLES_BY_TYPE, new String[]{
                String.valueOf(StyleType.User.getId())})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final Style style = UserStyle.createFromDatabase(rowData);
                map.put(style.getUuid(), style);
            }
        }

        return map;
    }

    @Override
    @NonNull
    public Map<String, Style> getBuiltinStyles() {
        final Map<String, Style> map = new LinkedHashMap<>();

        try (Cursor cursor = db.rawQuery(SELECT_STYLES_BY_TYPE, new String[]{
                String.valueOf(StyleType.Builtin.getId())})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                BuiltinStyle.createFromDatabase(rowData).ifPresent(
                        style -> map.put(style.getUuid(), style));
            }
        }

        return map;
    }

    @Override
    @NonNull
    public Style getGlobalStyle() {
        try (Cursor cursor = db.rawQuery(SELECT_STYLES_BY_TYPE, new String[]{
                String.valueOf(StyleType.Global.getId())})) {
            final DataHolder rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return GlobalStyle.createFromDatabase(rowData);
            }
        }

        throw new IllegalStateException();
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Style style) {
        try (SynchronizedStatement stmt = db.compileStatement(INSERT_STYLE)) {
            final long iId = doInsert(style, style.getLabel(context), stmt);
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Style style) {
        final ContentValues cv = new ContentValues();
        // Note that the StyleType is NEVER updated.

        cv.put(DBKey.STYLE_IS_PREFERRED, style.isPreferred());
        cv.put(DBKey.STYLE_MENU_POSITION, style.getMenuPosition());

        if (style.getType() != StyleType.Builtin) {
            cv.put(DBKey.STYLE_NAME, style.getLabel(context));

            cv.put(DBKey.STYLE_EXP_LEVEL, style.getExpansionLevel());
            cv.put(DBKey.STYLE_GROUPS, getGroupIdsAsCsv(style));
            cv.put(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE, style.getPrimaryAuthorType());
            for (final Style.UnderEach item : Style.UnderEach.values()) {
                cv.put(item.getDbKey(), style.isShowBooksUnderEachGroup(item.getGroupId()));
            }

            cv.put(DBKey.STYLE_LAYOUT, style.getLayout().getId());
            cv.put(DBKey.STYLE_COVER_CLICK_ACTION, style.getCoverClickAction().getId());
            cv.put(DBKey.STYLE_COVER_SCALE, style.getCoverScale());
            cv.put(DBKey.STYLE_TEXT_SCALE, style.getTextScale());
            cv.put(DBKey.STYLE_ROW_USES_PREF_HEIGHT, style.isGroupRowUsesPreferredHeight());

            cv.put(DBKey.STYLE_LIST_HEADER, style.getHeaderFieldVisibilityValue());
            cv.put(DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY,
                   style.getFieldVisibilityValue(FieldVisibility.Screen.List));
            cv.put(DBKey.STYLE_BOOK_LEVEL_FIELDS_ORDER_BY,
                   StyleCoder.getBookLevelFieldsOrderByAsJsonString(style));
            cv.put(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                   style.isSortAuthorByGivenName());

            cv.put(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME, style.isShowAuthorByGivenName());
            cv.put(DBKey.STYLE_PUBLISHER_SHOW_REORDERED, style.isShowReorderedPublisherName());
            cv.put(DBKey.STYLE_TITLE_SHOW_REORDERED, style.isShowReorderedTitle());

            cv.put(DBKey.STYLE_DETAILS_SHOW_FIELDS,
                   style.getFieldVisibilityValue(FieldVisibility.Screen.Detail));
        }

        return 0 < db.update(DBDefinitions.TBL_BOOKLIST_STYLES.getName(), cv,
                             DBKey.PK_ID + "=?",
                             new String[]{String.valueOf(style.getId())});
    }

    @Override
    public boolean delete(@NonNull final Style style) {

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            purgeNodeStatesByStyle(style.getId());

            try (SynchronizedStatement stmt = db.compileStatement(DELETE_STYLE_BY_ID)) {
                stmt.bindLong(1, style.getId());
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
            style.setId(0);
        }
        return rowsAffected == 1;
    }

    @Override
    public void purgeNodeStatesByStyle(final long styleId) {
        try (SynchronizedStatement stmt = db
                .compileStatement(DELETE_BOOK_LIST_NODE_STATE_BY_STYLE)) {
            stmt.bindLong(1, styleId);
            stmt.executeUpdateDelete();
        }
    }
}
