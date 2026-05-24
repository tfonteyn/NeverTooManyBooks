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

package com.hardbacknutter.nevertoomanybooks.core.debug;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("unused")
public final class DbDebugUtils {

    private static final String TAG = "DbDebugUtils";

    private DbDebugUtils() {
    }

    private static void debugDumpInfo(@NonNull final SQLiteDatabase db) {
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
     * @param db        Database Access
     * @param tableName to dump
     * @param limit     LIMIT limit
     * @param orderBy   ORDER BY orderBy
     * @param tag       log tag to use
     * @param header    a header which will be logged first
     */
    public static void dumpTable(@NonNull final SQLiteDatabase db,
                                 @NonNull final String tableName,
                                 final int limit,
                                 @NonNull final String orderBy,
                                 @NonNull final String tag,
                                 @NonNull final String header) {
        final Logger logger = LoggerFactory.getLogger();
        logger.d(tag, "dumpTable", tableName + ": " + header);

        final String sql =
                "SELECT * FROM " + tableName
                + " ORDER BY " + orderBy + " LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            final String columnHeading = Arrays
                    .stream(cursor.getColumnNames())
                    .map(column -> String.format("%-12s  ", column))
                    .collect(Collectors.joining("", "\n", ""));
            logger.d(tag, columnHeading);

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
