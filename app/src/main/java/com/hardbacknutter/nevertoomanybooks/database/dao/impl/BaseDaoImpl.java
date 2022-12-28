/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

abstract class BaseDaoImpl {

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
    static final String SELECT_ = "SELECT ";
    static final String _AS_ = " AS ";
    static final String _FROM_ = " FROM ";
    static final String _WHERE_ = " WHERE ";
    static final String _ORDER_BY_ = " ORDER BY ";
    static final String _GROUP_BY_ = " GROUP BY ";
    static final String UPDATE_ = "UPDATE ";
    static final String _SET_ = " SET ";

    static final String _AND_ = " AND ";
    static final String _OR_ = " OR ";
    static final String _NOT_IN_ = " NOT IN ";

    /**
     * Update a single Book's DATE_LAST_UPDATED__UTC to 'now'.
     */
    private static final String TOUCH =
            UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
            + _SET_ + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
            + _WHERE_ + DBKey.PK_ID + "=?";

    /** Log tag. */
    private static final String TAG = "BaseDaoImpl";

    /** Reference to the <strong>singleton</strong> which makes it safe to store/share here. */
    @NonNull
    final SynchronizedDb db;

    /**
     * Constructor.
     *
     * @param logTag of this DAO for logging.
     */
    BaseDaoImpl(@NonNull final String logTag) {
        if (BuildConfig.DEBUG /* always */) {
            Logger.d(TAG, "Constructor", logTag);
        }

        db = ServiceLocator.getInstance().getDb();
    }

    /**
     * Create the VALUES clause with 'count' number of '?'.
     *
     * @param count number of '?'
     *
     * @return a full " VALUES(?,...)" clause
     */
    @NonNull
    public static String values(final int count) {
        final StringJoiner sj = new StringJoiner(",", " VALUES (", ")");
        for (int i = 0; i < count; i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    /**
     * By exception in the base DAO to allow all dao instances to
     * update the 'last updated' of the given book.
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #touch(Book)} instead.
     *
     * @param bookId to update
     *
     * @return {@code true} on success
     */
    boolean touchBook(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = db.compileStatement(TOUCH)) {
            stmt.bindLong(1, bookId);
            return 0 < stmt.executeUpdateDelete();
        }
    }

    /**
     * By exception in the base DAO to allow all dao instances to
     * update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' then what we store).
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean touch(@NonNull final Book book) {
        if (touchBook(book.getId())) {
            book.putString(DBKey.DATE_LAST_UPDATED__UTC,
                           SqlEncode.date(LocalDateTime.now(ZoneOffset.UTC)));
            return true;

        } else {
            return false;
        }
    }

    /**
     * Execute the given SQL, and fetches column 0 as an {@code ArrayList<String>}.
     *
     * @param sql to execute
     *
     * @return List of values
     */
    @NonNull
    ArrayList<String> getColumnAsStringArrayList(@NonNull final String sql) {
        final ArrayList<String> list = new ArrayList<>();
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
    ArrayList<Long> getColumnAsLongArrayList(@NonNull final String sql) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }
}
