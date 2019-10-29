/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;

public final class UpgradeDatabase {

    private UpgradeDatabase() {
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              Database Access
     * @param tableName       the table
     * @param createStatement sql to recreate the table
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    private static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                               @NonNull final String tableName,
                                               @NonNull final String createStatement,
                                               @NonNull final String... toRemove) {
        final String tempName = "recreate_tmp";
        db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tempName);
        db.execSQL(createStatement);
        // This handles re-ordered fields etc.
        copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              Database Access
     * @param tableToRecreate the table
     * @param withConstraints Indicates if fields should have constraints applied
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                       @NonNull final TableDefinition tableToRecreate,
                                       final boolean withConstraints,
                                       @NonNull final String... toRemove) {
        final String tableName = tableToRecreate.getName();
        final String tempName = "recreate_tmp";
        db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tempName);
        tableToRecreate.create(db, withConstraints);
        tableToRecreate.createIndices(db);
        // This handles re-ordered fields etc.
        copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column reordering. This method will copy all columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to {@code null}
     * if no default is defined.
     *
     * @param db          Database Access
     * @param source      from table
     * @param destination to table
     * @param toRemove    (optional) List of fields to be removed from the source table
     *                    (skipped in copy)
     */
    private static void copyTableSafely(@NonNull final SynchronizedDb db,
                                        @SuppressWarnings("SameParameterValue")
                                        @NonNull final String source,
                                        @NonNull final String destination,
                                        @NonNull final String... toRemove) {
        // Get the source info
        TableInfo sourceTable = new TableInfo(db, source);
        // Build the column list
        StringBuilder columns = new StringBuilder();
        boolean first = true;
        for (ColumnInfo ci : sourceTable.getColumns()) {
            boolean isNeeded = true;
            for (String s : toRemove) {
                if (s.equalsIgnoreCase(ci.name)) {
                    isNeeded = false;
                    break;
                }
            }
            if (isNeeded) {
                if (first) {
                    first = false;
                } else {
                    columns.append(',');
                }
                columns.append(ci.name);
            }
        }
        String colList = columns.toString();
        String sql = "INSERT INTO " + destination + '(' + colList + ") SELECT "
                     + colList + " FROM " + source;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.executeInsert();
        }
    }

    /**
     * NOT USED RIGHT NOW. BEFORE USING SHOULD BE ENHANCED WITH PREPROCESS_TITLE IF NEEDED
     *
     * Create and populate the 'order by' column.
     * This method is used/meant for use during upgrades.
     * <p>
     * Note this is a lazy approach using the users preferred Locale,
     * as compared to the DAO code where we take the book's language/Locale into account.
     * The overhead here would be huge.
     * If the user has any specific book issue, a simple update of the book will fix it.
     */
    private static void addOrderByColumn(@NonNull final SQLiteDatabase db,
                                         @NonNull final TableDefinition table,
                                         @NonNull final DomainDefinition source,
                                         @NonNull final DomainDefinition destination) {

        db.execSQL("ALTER TABLE " + table + " ADD " + destination + " text not null default ''");

        String updateSql = "UPDATE " + table + " SET " + destination + "=?"
                           + " WHERE " + DBDefinitions.DOM_PK_ID + "=?";

        try (SQLiteStatement update = db.compileStatement(updateSql);
             Cursor cursor = db.rawQuery("SELECT " + DBDefinitions.DOM_PK_ID
                                      + ',' + source + " FROM " + table,
                                      null)) {
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final String in = cursor.getString(1);
                update.bindString(1, DAO.encodeOrderByColumn(in, Locale.getDefault()));
                update.bindLong(2, id);
                update.executeUpdateDelete();
            }
        }
    }

    static void toDb2(@NonNull final SynchronizedDb syncedDb) {
        DBDefinitions.TBL_BOOKS.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOK_COLOR);
    }
}
