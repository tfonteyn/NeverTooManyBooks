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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.Discouraged;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.DEBUG_FLAGS;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Database wrapper class that performs thread synchronization on all operations.
 * <p>
 * <strong>IMPORTANT:</strong> all {@link RuntimeException}s coming from the wrapped
 * database are allowed to be thrown!
 * If the class logic deems it needed, a {@link TransactionException} can be thrown as well.
 *
 * <p>
 * After getting a question "why?": See {@link Synchronizer} for details.
 * <p>
 * About the SQLite version:
 * <a href="https://developer.android.com/reference/android/database/sqlite/package-summary">
 * package-summary</a>
 * <p>
 * Android 15,  API 35    3.44
 * Android 14,  API 34    3.39
 * Android 13,  API 33    3.32
 * Android 12,  API 31    3.32
 * Android 11,  API 30    3.28  required for: UPSERT support; UPDATE OR REPLACE
 * Android 9,   API 28    3.22
 * Android 8.1, API 27    3.19
 * Android 8.0, API 26*   3.18
 * <p>
 * But some device manufacturers include different versions of SQLite on their devices.
 */
public class SynchronizedDb
        implements AutoCloseable {

    private static final String TABLE_EXISTS_SQL_STANDARD =
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
    private static final String TABLE_EXISTS_SQL_TEMP =
            "SELECT COUNT(*) FROM sqlite_temp_master WHERE type='table' AND name=?";
    private static final int DEFAULT_STMT_CACHE_SIZE = 25;
    /** Log tag. */
    private static final String TAG = "SynchronizedDb";
    private static final String ERROR_TX_LOCK_WAS_NULL = "Lock passed in was NULL";
    /** Trying to start a transaction (lock) which is already started. */
    private static final String ERROR_TX_ALREADY_STARTED = "TX already started";
    /** endTransaction without beginTransaction. */
    private static final String ERROR_TX_NEVER_STARTED = "No TX started";
    private static final String ERROR_TX_INSIDE_SHARED = "Inside shared TX";
    /** endTransaction called with an unexpected/wrong lock. */
    private static final String ERROR_TX_WRONG_LOCK = "Wrong lock";
    private static final String DROP_TABLE_IF_EXISTS_ = "DROP TABLE IF EXISTS ";
    @NonNull
    private final SQLiteOpenHelper sqLiteOpenHelper;
    private final boolean collationCaseSensitive;
    private final int preparedStmtCacheSize;

    /** Underlying (and open for writing) database. */
    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    /** Sync object to use. */
    @NonNull
    private final Synchronizer synchronizer;

    /** Factory object to create a {@link TypedCursor} cursor. */
    private final SQLiteDatabase.CursorFactory typedCursorFactory =
            (db, d, et, q) -> new TypedCursor(d, et, q, getSynchronizer());


    /**
     * Currently held transaction lock, if any.
     * <p>
     * Set in {@link #beginTransaction(boolean)}
     * and released in {@link #endTransaction(Synchronizer.SyncLock)}
     */
    @Nullable
    private Synchronizer.SyncLock currentTxLock;

    /**
     * Constructor.
     *
     * @param synchronizer           Synchronizer to use
     * @param sqLiteOpenHelper       SQLiteOpenHelper to open the underlying database
     * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
     *
     * @throws SQLiteException if the database cannot be opened
     */
    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
                          @NonNull final SQLiteOpenHelper sqLiteOpenHelper,
                          final boolean collationCaseSensitive) {
        this(synchronizer, sqLiteOpenHelper, collationCaseSensitive, -1);
    }

    /**
     * Constructor.
     * <p>
     * The Javadoc for setMaxSqlCacheSize says the default is 10,
     * but if you check the source code (verified API 30):
     * android/database/sqlite/SQLiteDatabaseConfiguration.java: public int maxSqlCacheSize;
     * the default is in fact 25 as set in the constructor of that class.
     *
     * @param synchronizer           Synchronizer to use
     * @param sqLiteOpenHelper       SQLiteOpenHelper to open the underlying database
     * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
     * @param preparedStmtCacheSize  the number or prepared statements to cache.
     *
     * @throws SQLiteException if the database cannot be opened
     */
    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
                          @NonNull final SQLiteOpenHelper sqLiteOpenHelper,
                          final boolean collationCaseSensitive,
                          @IntRange(to = SQLiteDatabase.MAX_SQL_CACHE_SIZE) final int preparedStmtCacheSize) {
        this.synchronizer = synchronizer;
        this.sqLiteOpenHelper = sqLiteOpenHelper;
        this.collationCaseSensitive = collationCaseSensitive;
        this.preparedStmtCacheSize = preparedStmtCacheSize;

        // Trigger onCreate/onUpdate/... for the database
        final Synchronizer.SyncLock syncLock = this.synchronizer.getExclusiveLock();
        try {
            sqLiteDatabase = getWritableDatabase();
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * Check if the collation this database uses is case-sensitive.
     *
     * @return {@code true} if case-sensitive (i.e. up to "you" to add lower/upper calls)
     */
    public boolean isCollationCaseSensitive() {
        return collationCaseSensitive;
    }

    /**
     * Open the database for writing. {@see SqLiteOpenHelper#getWritableDatabase()}
     *
     * @return database
     *
     * @throws SQLiteException if the database cannot be opened for writing
     */
    @NonNull
    private SQLiteDatabase getWritableDatabase() {
        final SQLiteDatabase db = sqLiteOpenHelper.getWritableDatabase();
        // only set when bigger than the default
        if (preparedStmtCacheSize > DEFAULT_STMT_CACHE_SIZE) {
            db.setMaxSqlCacheSize(preparedStmtCacheSize);
        }
        return db;
    }

    /**
     * Wrapper.
     *
     * @return {@code true} if the current thread is in a transaction.
     */
    public boolean inTransaction() {
        return sqLiteDatabase.inTransaction();
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     *
     * @throws TransactionException when there is already an active transaction
     */
    @NonNull
    public Synchronizer.SyncLock beginTransaction(final boolean isUpdate)
            throws TransactionException {
        final Synchronizer.SyncLock txLock;
        if (isUpdate) {
            txLock = synchronizer.getExclusiveLock();
        } else {
            txLock = synchronizer.getSharedLock();
        }

        if (currentTxLock == null) {
            // We have the lock, but if the real beginTransaction() throws an exception,
            // we need to release the lock.
            //noinspection CheckStyle
            try {
                sqLiteDatabase.beginTransaction();
            } catch (@NonNull final RuntimeException e) {
                txLock.unlock();
                throw e;
            }
        } else {
            // If we have a lock, and there is currently a TX active...die
            // Note: because we get a lock, two 'isUpdate' transactions will
            // block, this is only likely to happen with two TXs on the current thread
            // or two non-update TXs on different thread.
            throw new TransactionException(ERROR_TX_ALREADY_STARTED);
        }

        currentTxLock = txLock;
        return txLock;
    }

    /**
     * Wrapper for underlying database method.
     */
    public void setTransactionSuccessful() {
        // We could pass in the lock and do the same checks as we do in #endTransaction
        sqLiteDatabase.setTransactionSuccessful();
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>MUST</strong> be called from a 'finally' block.
     *
     * @param txLock Lock returned from {@link #beginTransaction(boolean)}.
     *
     * @throws TransactionException on any failure
     */
    public void endTransaction(@Nullable final Synchronizer.SyncLock txLock)
            throws TransactionException {
        if (txLock == null) {
            throw new TransactionException(ERROR_TX_LOCK_WAS_NULL);
        }
        if (currentTxLock == null) {
            throw new TransactionException(ERROR_TX_NEVER_STARTED);
        }
        if (!currentTxLock.equals(txLock)) {
            throw new TransactionException(ERROR_TX_WRONG_LOCK);
        }

        try {
            sqLiteDatabase.endTransaction();
        } finally {
            // Always clear the current one before unlocking so another thread does not
            // see the old lock when it gets the lock
            currentTxLock = null;
            txLock.unlock();
        }
    }

    @Override
    public void close() {
        sqLiteDatabase.close();
    }

    /**
     * Locking-aware wrapper for the underlying {@link SQLiteDatabase#insertOrThrow} method.
     * <p>
     * <strong>Note:</strong> SQLite maintains a Statement cache in its
     * <strong>native code</strong> based on SQL string matching.
     * However, to avoid the Android code overhead,
     * loops should use {@link #compileStatement} instead.
     * <p>
     * Dev. notes:
     * <ul>
     *     <li>See {@link ExtSQLiteStatement} for notes on exceptions and return code.</li>
     *     <li>DO NOT USE {@link SQLiteDatabase#insert(String, String, ContentValues)} as it will
     *         swallow all exceptions and ALWAYS return {@code -1} so we would have
     *         no way of knowing WHY it failed.</li>
     * </ul>
     *
     * @param table  the table to insert the row into
     * @param values this map contains the initial column values for the
     *               row. The keys should be the column names and the values the
     *               column values
     *
     * @return the row id of the newly inserted row;
     *         or {@code -1} if no inserts were done (but NOT due to an error)
     *
     * @throws SQLException         on unexpected failures
     * @throws TransactionException when currently inside a shared lock
     *
     * @see SQLiteDatabase#insertWithOnConflict(String, String, ContentValues, int)
     */
    @Discouraged(message = "Use compileStatement instead")
    @IntRange(from = -1)
    public long insert(@NonNull final String table,
                       @NonNull final ContentValues values)
            throws TransactionException, SQLException {

        Synchronizer.SyncLock txLock = null;
        if (currentTxLock != null) {
            if (currentTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = synchronizer.getExclusiveLock();
        }

        try {
            return sqLiteDatabase.insertOrThrow(table, null, values);

        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>Note:</strong> SQLite maintains a Statement cache in its
     * <strong>native code</strong> based on SQL string matching.
     * However, to avoid the Android code overhead,
     * loops should use {@link #compileStatement} instead.
     *
     * @param table       the table to delete from
     * @param values      a map from column names to new column values.
     *                    {@code null} is a valid value that will be translated to NULL.
     * @param whereClause the optional WHERE clause to apply when deleting.
     *                    Passing null will delete all rows.
     * @param whereArgs   the arguments to bind for the WHERE
     *
     * @return the number of rows affected
     *
     * @throws SQLException         on any failure
     * @throws TransactionException when currently inside a shared lock
     */
    @Discouraged(message = "Use compileStatement instead")
    @IntRange(from = 0)
    public int update(@NonNull final String table,
                      @NonNull final ContentValues values,
                      @NonNull final String whereClause,
                      @Nullable final String[] whereArgs)
            throws SQLException, TransactionException {

        Synchronizer.SyncLock txLock = null;
        if (currentTxLock != null) {
            if (currentTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = synchronizer.getExclusiveLock();
        }

        // reminder: update does not throw exceptions for the actual update.
        // but it can throw other exceptions.
        try {
            return sqLiteDatabase.update(table, values, whereClause, whereArgs);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * Compiles an SQL statement into a reusable pre-compiled statement object.
     *
     * @param sql The raw SQL statement
     *
     * @return A pre-compiled <strong>NEW</strong> {@link SQLiteStatement} object
     *
     * @throws TransactionException when currently inside a shared lock
     */
    @NonNull
    public SynchronizedStatement compileStatement(@NonNull final String sql)
            throws TransactionException {
        Synchronizer.SyncLock txLock = null;
        if (currentTxLock != null) {
            if (currentTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = synchronizer.getExclusiveLock();
        }

        try {
            final SQLiteStatement statement = sqLiteDatabase.compileStatement(sql);
            // readOnly is used to get a shared versus exclusive lock.
            // The toUpperCase call was VERY slow (profiler test)
            // As there are only "select" and "savepoint" and  we don't use the latter:
            // test on 's' only, and assume trim() is not needed.
            //   readOnly = sql.strip().toUpperCase(Locale.ENGLISH).startsWith("SELECT");
            final boolean readOnly = sql.charAt(0) == 'S' || sql.charAt(0) == 's';

            return new SynchronizedStatement(synchronizer, statement, readOnly);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param sql The raw SQL statement
     *
     * @throws SQLException         on failures
     * @throws TransactionException when currently inside a shared lock
     */
    public void execSQL(@NonNull final String sql)
            throws SQLException, TransactionException {
        if (BuildConfig.DEBUG && DEBUG_FLAGS.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger().d(TAG, "execSQL", sql);
        }

        Synchronizer.SyncLock txLock = null;
        if (currentTxLock != null) {
            if (currentTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = synchronizer.getExclusiveLock();
        }

        try {
            sqLiteDatabase.execSQL(sql);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param sql           The raw SQL statement
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     *
     * @return the cursor
     */
    @NonNull
    public Cursor rawQuery(@NonNull final String sql,
                           @Nullable final String[] selectionArgs) {
        Synchronizer.SyncLock txLock = null;
        if (currentTxLock == null) {
            txLock = synchronizer.getSharedLock();
        }

        try {
            // URGENT: lint says this cursor is not always closed.
            // All usage is with "try (Cursor....)" EXCEPT:
            // {@link FtsDaoImp#querySearchSuggestions} as used by {@link SearchSuggestionProvider}
            // ==> where it seems not possible to close it ourselves.
            // and:
            // {@link Booklist#getOffsetCursor} for the paging.
            // ==> leak MUST be there
            //
            return sqLiteDatabase.rawQuery(sql, selectionArgs, null);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Wrapper for underlying database method.
     * It is recommended that custom cursors subclass SynchronizedCursor.
     * <p>
     * Runs the provided SQL and returns a cursor over the result set.
     *
     * @param sql           the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param editTable     the name of the first table, which is editable
     *
     * @return A {@link TypedCursor} object, which is positioned before the first entry.
     *         Note that {@link Cursor}s are not synchronized,
     *         see the documentation for more details.
     */
    @NonNull
    public TypedCursor rawQueryWithTypedCursor(@NonNull final String sql,
                                               @Nullable final String[] selectionArgs,
                                               @Nullable final String editTable) {
        Synchronizer.SyncLock txLock = null;
        if (currentTxLock == null) {
            txLock = synchronizer.getSharedLock();
        }
        try {
            // So what is this 'editTable' ? Google has almost no info on it, and
            // all code-paths lead to android.database.sqlite.SQLiteCursor
            // which in fact does NOT use the parameter at all (and it's private).
            // The only place where it could be used, is when using a cursory factory,
            // which in our case means the typedCursorFactory -> TypedCursor,
            // and we don't use it.... so passing in null is no issue.
            // If the SQLiteCursor ever starts using... we might crash... that'll be the day...
            //noinspection DataFlowIssue
            return (TypedCursor) sqLiteDatabase
                    .rawQueryWithFactory(typedCursorFactory, sql, selectionArgs, editTable);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Get a description/info structure for the given table.
     *
     * @param tableDefinition to get
     *
     * @return info object; its details might be empty if the table does not exist.
     *
     * @see TableDefinition#getTableInfo(SQLiteDatabase)
     */
    @NonNull
    public TableInfo getTableInfo(@NonNull final TableDefinition tableDefinition) {
        Synchronizer.SyncLock txLock = null;
        if (currentTxLock == null) {
            txLock = synchronizer.getSharedLock();
        }
        try {
            return tableDefinition.getTableInfo(sqLiteDatabase);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Check if the given table exists.
     * <p>
     * Both the table name and the {@link TableDefinition.TableType} are used for this check.
     *
     * @param tableDefinition to check
     *
     * @return flag
     *
     * @see #tableExists(String)
     */
    public boolean tableExists(@NonNull final TableDefinition tableDefinition) {
        final String sql;
        if (tableDefinition.getType() == TableDefinition.TableType.Standard) {
            sql = TABLE_EXISTS_SQL_STANDARD;
        } else {
            sql = TABLE_EXISTS_SQL_TEMP;
        }

        try (SynchronizedStatement stmt = compileStatement(sql)) {
            stmt.bindString(1, tableDefinition.getName());
            return stmt.simpleQueryForLongOrZero() > 0;
        }
    }

    /**
     * Check if the given table exists.
     * <p>
     * The name is checked in both {@link TableDefinition.TableType#Temporary}
     * and {@link TableDefinition.TableType#Standard} table-spaces.
     *
     * @param name to check
     *
     * @return flag
     *
     * @see #tableExists(TableDefinition)
     */
    public boolean tableExists(@NonNull final String name) {
        for (final String sql : List.of(TABLE_EXISTS_SQL_TEMP,
                                        TABLE_EXISTS_SQL_STANDARD)) {
            try (SynchronizedStatement stmt = compileStatement(sql)) {
                stmt.bindString(1, name);
                if (stmt.simpleQueryForLongOrZero() > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Locking-aware recreating {@link TableDefinition.TableType#Temporary} tables.
     * <p>
     * If the table has no references to it, this method can also
     * be used on {@link TableDefinition.TableType#Standard}.
     * <p>
     * Drop this table (if it exists) and (re)create it including its indexes.
     *
     * @param table                 to recreate
     * @param withDomainConstraints Indicates if fields should have constraints applied
     *
     * @throws TransactionException if paranoia was justified
     */
    public void recreate(@NonNull final TableDefinition table,
                         final boolean withDomainConstraints)
            throws TransactionException {

        // We're being paranoid here... we should always be called in a transaction,
        // which means we should not bother with LOCK_EXCLUSIVE.
        // But having the logic in place because: 1) future-proof + 2) developer boo-boo,
        if (!sqLiteDatabase.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        Synchronizer.SyncLock txLock = null;
        if (currentTxLock != null) {
            if (currentTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = synchronizer.getExclusiveLock();
        }

        try {
            // Drop the table in case there is an orphaned instance with the same name.
            sqLiteDatabase.execSQL(DROP_TABLE_IF_EXISTS_ + table.getName());
            table.create(sqLiteDatabase, withDomainConstraints);
            table.createIndices(sqLiteDatabase, collationCaseSensitive);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }


    /**
     * Run '<a href="https://www.sqlite.org/pragma.html#pragma_optimize">optimize</a>'
     * on the whole database.
     */
    public void optimize() {
        execSQL("PRAGMA optimize");
    }

    /**
     * Run '<a href="https://www.sqlite.org/lang_analyze.html">analyse</a>' on the whole database.
     */
    @WorkerThread
    public void analyze() {
        execSQL("analyze");
    }

    /**
     * Run '<a href="https://www.sqlite.org/lang_analyze.html">analyse</a>' on the given table.
     *
     * @param table to analyse.
     */
    public void analyze(@NonNull final TableDefinition table) {
        execSQL("analyze " + table);
    }

    /**
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return the underlying SQLiteDatabase object.
     */
    @NonNull
    public SQLiteDatabase getSQLiteDatabase() {
        return sqLiteDatabase;
    }

    /**
     * Gets the path to the database file.
     *
     * @return The path to the database file.
     */
    @NonNull
    public String getPath() {
        return sqLiteDatabase.getPath();
    }

    /**
     * For use by the cursor factory only.
     *
     * @return the underlying Synchronizer object.
     */
    @NonNull
    private Synchronizer getSynchronizer() {
        return synchronizer;
    }
}
