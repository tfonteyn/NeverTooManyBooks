/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

final class UpgradeDatabase {

    private UpgradeDatabase() {
    }

    /**
     * Rename the given table, recreate it, load the data into the new table, drop the old table.
     *
     * <a href="https://www.sqlite.org/lang_altertable.html#making_other_kinds_of_table_schema_changes">
     * SQLite - making_other_kinds_of_table_schema_changes</a>
     * <p>
     * The 12 steps in summary:
     * <ol>
     *  <li>If foreign key constraints are enabled, disable them using PRAGMA foreign_keys=OFF.</li>
     *  <li>Create new table</li>
     *  <li>Copy data</li>
     *  <li>Drop old table</li>
     *  <li>Rename new into old</li>
     *  <li>If foreign keys constraints were originally enabled, re-enable them now.</li>
     * </ol>
     *
     * @param db              Database Access
     * @param source          the table
     * @param withConstraints Indicates if fields should have constraints applied
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final TableDefinition source,
                                       @SuppressWarnings("SameParameterValue")
                                       final boolean withConstraints,
                                       @Nullable final String... toRemove) {

        final String dstTableName = "recreating";
        final TableDefinition dstTable = new TableDefinition(source);
        dstTable.setName(dstTableName);
        dstTable.create(db, withConstraints)
                .createIndices(db);

        // This handles re-ordered fields etc.
        copyTableSafely(db, source.getName(), dstTableName, toRemove);

        db.execSQL("DROP TABLE " + source.getName());
        db.execSQL("ALTER TABLE " + dstTableName + " RENAME TO " + source.getName());
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column order. This method will copy all columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to {@code null}
     * if no default is defined.
     *
     * @param db          Database Access
     * @param source      from table
     * @param destination to table
     * @param toRemove    (optional) List of fields to be removed from the source table
     */
    private static void copyTableSafely(@NonNull final SynchronizedDb db,
                                        @SuppressWarnings("SameParameterValue")
                                        @NonNull final String source,
                                        @SuppressWarnings("SameParameterValue")
                                        @NonNull final String destination,
                                        @Nullable final String... toRemove) {
        // Get the source info
        final TableInfo sourceTable = new TableInfo(db, source);
        // Build the column list
        final StringJoiner columns = new StringJoiner(",");
        for (ColumnInfo ci : sourceTable.getColumns()) {
            boolean isNeeded = true;
            if (toRemove != null) {
                for (String s : toRemove) {
                    if (s.equalsIgnoreCase(ci.name)) {
                        isNeeded = false;
                        break;
                    }
                }
            }
            if (isNeeded) {
                columns.add(ci.name);
            }
        }

        final String colList = columns.toString();
        final String sql = "INSERT INTO " + destination + '(' + colList + ") SELECT "
                           + colList + " FROM " + source;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.executeInsert();
        }
    }

    /**
     * NOT USED RIGHT NOW. BEFORE USING SHOULD BE ENHANCED WITH PREPROCESS_TITLE IF NEEDED
     * <p>
     * Create and populate the 'order by' column.
     * This method is used/meant for use during upgrades.
     * <p>
     * Note this is a lazy approach using the users preferred Locale,
     * as compared to the DAO code where we take the book's language/Locale into account.
     * The overhead here would be huge.
     * If the user has any specific book issue, a simple update of the book will fix it.
     */
    private static void addOrderByColumn(@NonNull final Context context,
                                         @NonNull final SQLiteDatabase db,
                                         @NonNull final TableDefinition table,
                                         @NonNull final Domain source,
                                         @NonNull final Domain destination) {

        db.execSQL("ALTER TABLE " + table.getName()
                   + " ADD " + destination.getName() + " text NOT NULL default ''");

        final String updateSql =
                "UPDATE " + table.getName() + " SET " + destination.getName() + "=?"
                + " WHERE " + DBDefinitions.KEY_PK_ID + "=?";

        try (final SQLiteStatement update = db.compileStatement(updateSql);
             final Cursor cursor = db.rawQuery(
                     "SELECT " + DBDefinitions.KEY_PK_ID
                     + ',' + source.getName() + " FROM " + table.getName(),
                     null)) {

            final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final String in = cursor.getString(1);
                update.bindString(1, DAO.encodeOrderByColumn(in, userLocale));
                update.bindLong(2, id);
                update.executeUpdateDelete();
            }
        }
    }
}
