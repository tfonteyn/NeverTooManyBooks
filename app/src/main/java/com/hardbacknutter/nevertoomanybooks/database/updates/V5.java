/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;

class V5 {

    private static final String ALTER_TABLE_ = "ALTER TABLE ";
    private static final String DROP_TABLE_ = "DROP TABLE ";
    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String SELECT_ = "SELECT ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _FROM_ = " FROM ";
    private static final String _RENAME_TO_ = " RENAME TO ";
    private static final String _SET_ = " SET ";

    @NonNull
    private final SQLiteDatabase db;
    private final int oldVersion;

    /**
     * Constructor.
     *
     * @param db         Underlying database
     * @param oldVersion The old database version.
     */
    V5(@NonNull final SQLiteDatabase db,
       final int oldVersion) {
        this.db = db;
        this.oldVersion = oldVersion;
    }

    /**
     * Perform all updates.
     * <p>
     * v5.0.0: 26
     * v5.1.0: 27
     * v5.2.0: 29
     * v5.2.2: 30
     * v5.3.0: 31
     * v5.5.0: 32
     * v5.5.1: 33
     * v5.5.4: 34
     */
    void update() {
        if (oldVersion < 26) {
            db26();
        }
        if (oldVersion < 28) {
            db28();
        }
        if (oldVersion < 29) {
            db29();
        }
        if (oldVersion < 31) {
            db31();
        }
        if (oldVersion < 32) {
            db32();
        }
        if (oldVersion < 34) {
            // CANNOT ROLLBACK
            db34();
        }
    }

    private void db26() {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_BOOK_LIST_FIELD_ORDER_BY,
                DBDefinitions.DOM_STYLE_COVER_CLICK_ACTION,
                DBDefinitions.DOM_STYLE_LAYOUT);
    }

    private void db28() {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_TITLE_SHOW_REORDERED);

        // migrateReorderPref
        final int value = ServiceLocator.getInstance().getSharedPreferences()
                                        .getBoolean("show.title.reordered", false)
                          ? 1 : 0;

        // We apply the setting to ALL styles as it was the default for all.
        // (including the built-in which is pointless but easier)
        try (SQLiteStatement stmt = db.compileStatement(
                UPDATE_ + TBL_BOOKLIST_STYLES.getName()
                + _SET_ + DBKey.STYLE.TITLE_SHOW_REORDERED + "=?")) {
            stmt.bindLong(1, value);
            stmt.executeUpdateDelete();
        }
    }

    private void db29() {
        TBL_STRIPINFO_COLLECTION.alterTableAddColumns(
                db, DBDefinitions.DOM_STRIP_INFO_DIGITAL);
    }

    private void db31() {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db, DBDefinitions.DOM_STYLE_COVER_LONG_CLICK_ACTION);
    }

    private void db32() {
        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_BOOK_READ_PROGRESS);
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db, DBDefinitions.DOM_STYLE_READ_STATUS_WITH_PROGRESS);
    }

    private void db34() {
        // recreate tables due to some columns having their COLLATION changed
        Upgrade.runWithoutConstraints(db, () -> {
            // DBDefinitions.DOM_STYLE_NAME
            db34RecreateTable(TBL_BOOKLIST_STYLES);
            // DBDefinitions.DOM_BOOKSHELF_NAME
            db34RecreateTable(TBL_BOOKSHELF);
            // DBDefinitions.DOM_AUTHOR_FAMILY_NAME_OB, DBDefinitions.DOM_AUTHOR_GIVEN_NAMES_OB
            db34RecreateTable(TBL_AUTHORS);
            // DBDefinitions.DOM_SERIES_TITLE_OB
            db34RecreateTable(TBL_SERIES);
            // DBDefinitions.DOM_PUBLISHER_NAME_OB
            db34RecreateTable(TBL_PUBLISHERS);
            // DBDefinitions.DOM_TITLE_OB
            db34RecreateTable(TBL_BOOKS);
        });
    }

    /**
     * AS USED FOR THE UPGRADE FROM V33 TO V34 ONLY.
     * This creates/expects all columns to be identical except for the sqlite datatype.
     *
     * @param td table
     */
    private void db34RecreateTable(@NonNull final TableDefinition td) {
        final String dstTableName = "copyOf" + td.getName();
        db.execSQL(td.getCreateStatement(dstTableName, true));

        final List<String> srcColumns = td.getTableInfo(db)
                                          .getColumns()
                                          .stream()
                                          .map(ColumnInfo::getName)
                                          .collect(Collectors.toList());

        final List<String> dstColumns = new ArrayList<>(srcColumns);

        db.execSQL(
                INSERT_INTO_ + dstTableName + " (" + String.join(",", dstColumns) + ") "
                + SELECT_ + String.join(",", srcColumns) + _FROM_ + td.getName());

        db.execSQL(DROP_TABLE_ + td.getName());
        db.execSQL(ALTER_TABLE_ + dstTableName + _RENAME_TO_ + td.getName());
    }
}
