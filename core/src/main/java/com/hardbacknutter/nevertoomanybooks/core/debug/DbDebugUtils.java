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

package com.hardbacknutter.nevertoomanybooks.core.debug;

import android.database.Cursor;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

@SuppressWarnings("unused")
public final class DbDebugUtils {

    private static final String TAG = "DbDebugUtils";

    private DbDebugUtils() {
    }

    private static void debugDumpInfo(@NonNull final SynchronizedDb db) {
        final Logger logger = LoggerFactory.getLogger();
        final String[] sql = {"SELECT sqlite_version() AS sqlite_version",
                "PRAGMA encoding",
                "PRAGMA collation_list",
                "PRAGMA foreign_keys",
                "PRAGMA recursive_triggers",
        };
        for (final String s : sql) {
            try (Cursor cursor = db.rawQuery(s, null)) {
                if (cursor.moveToNext()) {
                    logger.d(TAG, "debugDumpInfo", s + " = " + cursor.getString(0));
                }
            }
        }
    }

    /**
     * Dump the content of the given table to the debug output.
     *
     * @param tableDefinition to dump
     * @param limit           LIMIT limit
     * @param orderBy         ORDER BY orderBy
     * @param tag             log tag to use
     * @param header          a header which will be logged first
     */
    public static void dumpTable(@NonNull final SynchronizedDb db,
                                 @NonNull final TableDefinition tableDefinition,
                                 final int limit,
                                 @NonNull final String orderBy,
                                 @NonNull final String tag,
                                 @NonNull final String header) {
        final Logger logger = LoggerFactory.getLogger();
        logger.d(tag, "dumpTable", tableDefinition.getName() + ": " + header);

        final String sql =
                "SELECT * FROM " + tableDefinition.getName()
                + " ORDER BY " + orderBy + " LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            final StringBuilder columnHeading = new StringBuilder("\n");
            final String[] columnNames = cursor.getColumnNames();
            for (final String column : columnNames) {
                columnHeading.append(String.format("%-12s  ", column));
            }
            logger.d(tag, columnHeading.toString());

            while (cursor.moveToNext()) {
                final StringBuilder line = new StringBuilder();
                for (int c = 0; c < cursor.getColumnCount(); c++) {
                    line.append(String.format("%-12s  ", cursor.getString(c)));
                }
                logger.d(tag, line.toString());
            }
        }
    }
}
