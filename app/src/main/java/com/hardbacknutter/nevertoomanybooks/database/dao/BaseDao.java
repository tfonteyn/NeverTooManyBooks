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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

/**
 * The use and definition of DAO in this project has a long history.
 * Migrating to 'best practices' has been an ongoing effort but is at best a far future goal.
 * The main issue is that all testing must be done with the emulator as we can't easily
 * inject mock doa's for now.
 */
public abstract class BaseDao {

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
    protected static final String _OR_ = " OR ";
    protected static final String _NOT_IN_ = " NOT IN ";

    /**
     * Update a single Book's KEY_UTC_LAST_UPDATED to 'now'
     */
    private static final String TOUCH =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
            + _WHERE_ + KEY_PK_ID + "=?";

    /** Log tag. */
    private static final String TAG = "BaseDao";

    /** See {@link #encodeDate(LocalDateTime)}. */
    private static final Pattern T = Pattern.compile("T");
    /** See {@link #encodeOrderByColumn}. */
    private static final Pattern NON_WORD_CHARACTER_PATTERN = Pattern.compile("\\W");
    /** See {@link #encodeString}. */
    private static final Pattern SINGLE_QUOTE_LITERAL = Pattern.compile("'", Pattern.LITERAL);

    /** Reference to the singleton. */
    protected final SynchronizedDb mSyncedDb;
    @NonNull
    protected final String mInstanceName;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    protected BaseDao(@NonNull final Context context,
                      @NonNull final String logTag) {
        mInstanceName = logTag;

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|Constructor");
        }

        mSyncedDb = DBHelper.getSyncDb(context);
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
    protected static String encodeDate(@NonNull final LocalDateTime dateTime) {
        // We should just create a formatter which uses a ' '...
        final String date = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return T.matcher(date).replaceFirst(" ");
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

    @NonNull
    public File getDatabaseFile() {
        return mSyncedDb.getDatabaseFile();
    }

    /**
     * Get the local database.
     * This should only be called in test classes, and from the {@link DBCleaner}.
     * <p>
     * Other code should use {@link DBHelper#getSyncDb(Context)} directly to get
     * a lighter weight object.
     *
     * @return Underlying database connection
     */
    @NonNull
    public SynchronizedDb getSyncDb() {
        return mSyncedDb;
    }

    /**
     * DEBUG only. Return the instance name of this DAO.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mInstanceName;
    }


    /**
     * Wrapper to {@link SynchronizedDb#inTransaction}.
     */
    public boolean inTransaction() {
        return mSyncedDb.inTransaction();
    }

    /**
     * Wrapper to {@link SynchronizedDb#beginTransaction(boolean)}.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public Synchronizer.SyncLock beginTransaction(final boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Wrapper to {@link SynchronizedDb#setTransactionSuccessful}.
     */
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    /**
     * Wrapper to {@link SynchronizedDb#endTransaction}.
     *
     * @param txLock Lock returned from {@link #beginTransaction(boolean)}
     */
    public void endTransaction(@Nullable final Synchronizer.SyncLock txLock) {
        mSyncedDb.endTransaction(txLock);
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

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(TOUCH)) {
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
            book.putString(KEY_UTC_LAST_UPDATED, encodeDate(LocalDateTime.now(ZoneOffset.UTC)));
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
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
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
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }
}
