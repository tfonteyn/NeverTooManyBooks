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

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

public abstract class BaseDaoImpl {

    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM),
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
    protected static final String _COLLATION = " COLLATE LOCALIZED";

    protected static final String DELETE_FROM_ = "DELETE FROM ";
    protected static final String INSERT_INTO_ = "INSERT INTO ";

    protected static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    protected static final String SELECT_ = "SELECT ";
    protected static final String _AS_ = " AS ";
    protected static final String _FROM_ = " FROM ";
    protected static final String _WHERE_ = " WHERE ";
    protected static final String _ORDER_BY_ = " ORDER BY ";

    protected static final String UPDATE_ = "UPDATE ";
    protected static final String _SET_ = " SET ";

    protected static final String _AND_ = " AND ";
    static final String _OR_ = " OR ";
    static final String _NOT_IN_ = " NOT IN ";

    /** See {@link #encodeDate(LocalDateTime)}. */
    private static final Pattern T = Pattern.compile("T");

    /**
     * Update a single Book's KEY_UTC_LAST_UPDATED to 'now'
     */
    private static final String TOUCH =
            UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
            + _SET_ + DBKeys.KEY_UTC_LAST_UPDATED + "=current_timestamp"
            + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /** Log tag. */
    private static final String TAG = "BaseDaoImpl";
    /** See {@link #encodeOrderByColumn}. */
    private static final Pattern NON_WORD_CHARACTER_PATTERN = Pattern.compile("\\W");
    /** See {@link #encodeString}. */
    private static final Pattern SINGLE_QUOTE_LITERAL = Pattern.compile("'", Pattern.LITERAL);

    /** Reference to the singleton. */
    @NonNull
    protected final SynchronizedDb mDb;

    /** Used for logging/tracking. */
    @NonNull
    protected final String mInstanceName;

    /**
     * Constructor.
     *
     * @param logTag of this DAO for logging.
     */
    protected BaseDaoImpl(@NonNull final String logTag) {
        mInstanceName = logTag;

        if (BuildConfig.DEBUG /* always */) {
            Logger.d(TAG, "Constructor", logTag);
        }

        mDb = ServiceLocator.getDb();
    }

    /**
     * Escape single quotation marks by doubling them (standard SQL escape).
     *
     * @param value to encode
     *
     * @return escaped value.
     */
    @NonNull
    public static String encodeString(@NonNull final CharSequence value) {
        return SINGLE_QUOTE_LITERAL.matcher(value).replaceAll("''");
    }

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book: strip spaces etc, make lowercase,...
     *
     * @param value  to encode
     * @param locale to use for case manipulation
     *
     * @return the encoded value
     */
    public static String encodeOrderByColumn(@NonNull final CharSequence value,
                                             @NonNull final Locale locale) {
        final String s = ParseUtils.toAscii(value);
        // remove all non-word characters. i.e. all characters not in [a-zA-Z_0-9]
        return NON_WORD_CHARACTER_PATTERN.matcher(s).replaceAll("").toLowerCase(locale);
    }

    /**
     * By exception in the base DAO to allow all dao instances to
     * update the 'last updated' of the given book.
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #touchBook(Book)} instead.
     *
     * @param bookId to update
     *
     * @return {@code true} on success
     */
    boolean touchBook(@IntRange(from = 1) final long bookId) {

        try (SynchronizedStatement stmt = mDb.compileStatement(TOUCH)) {
            stmt.bindLong(1, bookId);
            return 0 < stmt.executeUpdateDelete();
        }
    }

    /**
     * By exception in the base DAO to allow all dao instances to
     * update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with the current date-time.
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean touchBook(@NonNull final Book book) {

        if (touchBook(book.getId())) {
            book.putString(DBKeys.KEY_UTC_LAST_UPDATED,
                           encodeDate(LocalDateTime.now(ZoneOffset.UTC)));
            return true;

        } else {
            return false;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList.
     * Skips {@code null} and {@code ""} entries.
     *
     * @param cursor cursor
     *
     * @return List of values (case sensitive)
     */
    @NonNull
    protected ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        final ArrayList<String> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            final String name = cursor.getString(0);
            if (name != null && !name.isEmpty()) {
                list.add(name);
            }
        }
        return list;
    }

    /**
     * Fills an array with the specified (String) column from the passed SQL.
     *
     * @param sql        SQL to execute
     * @param columnName Column to fetch
     *
     * @return List of *all* values
     *
     * @see #getFirstColumnAsList
     */
    @NonNull
    ArrayList<String> getColumnAsList(@NonNull final String sql,
                                      @NonNull final String columnName) {
        final ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(sql, null)) {
            final int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(column));
            }
            return list;
        }
    }

    /**
     * Fills an array with the first column (index==0, type==long) from the passed SQL.
     *
     * @param sql SQL to execute
     *
     * @return List of *all* values
     */
    @NonNull
    protected ArrayList<Long> getIdList(@NonNull final String sql) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }

    /**
     * Encode a LocalDateTime. Used to transform Java-ISO to SQL-ISO datetime format.
     * <p>
     * Main/only function for now: replace the 'T' character with a ' '
     * so it matches the "current_timestamp" function in SQLite.
     * We should just create a formatter which uses a ' '
     *
     * @param dateTime to encode
     *
     * @return sqlite date time as a string
     */
    @NonNull
    protected String encodeDate(@NonNull final LocalDateTime dateTime) {
        // We should just create a formatter which uses a ' '...
        final String date = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return T.matcher(date).replaceFirst(" ");
    }
}
