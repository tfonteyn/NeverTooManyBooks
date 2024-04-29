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

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;

class BaseDaoImpl {

    /**
     * In addition to the SQLite default BINARY collator (others: NOCASE and RTRIM),
     * Android supplies two more.
     * LOCALIZED: using the system's current Locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current Locale.
     * <p>
     * We tried 'COLLATE UNICODE' but it is case sensitive.
     * We ended up with 'Ursula Le Guin' and 'Ursula le Guin'.
     * <p>
     * We now use COLLATE LOCALE and check to see if it is case sensitive.
     * Maybe in the future Android will add LOCALE_CI (or equivalent).
     */
    static final String _COLLATION = " COLLATE LOCALIZED";

    static final String DELETE_FROM_ = "DELETE FROM ";
    static final String INSERT_INTO_ = "INSERT INTO ";

    static final String SELECT_COUNT_FROM_ = "SELECT COUNT(*) FROM ";
    static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    static final String SELECT_EXISTS_ = "SELECT EXISTS ";
    static final String SELECT_ = "SELECT ";
    static final String _AS_ = " AS ";
    static final String _FROM_ = " FROM ";
    static final String _WHERE_ = " WHERE ";
    static final String _ORDER_BY_ = " ORDER BY ";
    static final String _DESC = " DESC";
    static final String _GROUP_BY_ = " GROUP BY ";
    static final String UPDATE_ = "UPDATE ";
    static final String _SET_ = " SET ";

    static final String _AND_ = " AND ";
    static final String _OR_ = " OR ";
    static final String _IN_ = " IN ";
    static final String _NOT_IN_ = " NOT IN ";

    static final String CASE_WHEN_ = "CASE WHEN ";
    static final String _THEN_ = " THEN ";
    static final String _ELSE_ = " ELSE ";
    static final String _END = " END";

    /** Reference to the <strong>singleton</strong> which makes it safe to store/share here. */
    @NonNull
    final SynchronizedDb db;

    /**
     * Constructor.
     *
     * @param db     Underlying database
     * @param logTag of this DAO for logging.
     */
    BaseDaoImpl(@NonNull final SynchronizedDb db,
                @NonNull final String logTag) {
        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(logTag, "Constructor");
        }

        this.db = db;
    }

    /**
     * Execute the given SQL, and fetches column 0 as an {@code ArrayList<String>}.
     *
     * @param sql to execute
     *
     * @return List of values
     */
    @NonNull
    List<String> getColumnAsStringArrayList(@NonNull final String sql) {
        final List<String> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
        }
        return list;
    }

    /**
     * Execute the given SQL, and fetches column 0 as an {@code ArrayList<Long>}.
     *
     * @param sql SQL to execute
     *
     * @return List of values
     */
    @NonNull
    List<Long> getColumnAsLongArrayList(@NonNull final String sql) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }
}
