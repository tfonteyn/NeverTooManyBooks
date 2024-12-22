/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class IdentifierDaoImpl
        extends BaseDaoImpl {

    private static final String TAG = "IdentifierDaoImpl";

    private static final Map<String, String> PREDEFINED = Map.ofEntries(
            Map.entry("asin", "Amazon"),
            Map.entry("bedetheque", "Bedetheque"),
            Map.entry("dnb", "DNB.de"),
            Map.entry("douban", "Douban"),
            Map.entry("goodreads", "Goodreads"),
            Map.entry("google", "Google Books"),
            Map.entry("isfdb", "ISFDB Publication Record"),
            Map.entry("lastdodo", "LastDodo"),
            Map.entry("lccn", "Library of Congress control number (US)"),
            Map.entry("librarything", "LibraryThing"),
            Map.entry("mobi-asin", "Amazon (azw)"),
            Map.entry("oclc", "WorldCat"),
            Map.entry("openlibrary", "Open Library"),
            Map.entry("stripinfo", "StripInfo"),
            Map.entry("uri", "URI")
    );

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    IdentifierDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at <strong>installation</strong> time to add the predefined ID's to the database.
     *
     * @param db Database Access
     */
    public static void onPostCreate(@NonNull final SQLiteDatabase db) {
        try (SQLiteStatement stmt = db.compileStatement(Sql.INSERT_IDENT_DEFINITION)) {
            for (final Map.Entry<String, String> entry : PREDEFINED.entrySet()) {
                stmt.bindString(1, entry.getKey());
                stmt.bindString(2, entry.getValue());
                // url
                stmt.bindNull(3);

                stmt.executeInsert();
            }
        }
    }

    private static final class Sql {
        static final String INSERT_IDENT_DEFINITION =
                INSERT_INTO_ + DBDefinitions.TBL_IDENTIFIERS.getName()
                + '(' + DBKey.IDENT_NAME
                + ',' + DBKey.IDENT_DESC
                + ',' + DBKey.IDENT_URL
                + ") VALUES(?,?,?)";
    }
}
