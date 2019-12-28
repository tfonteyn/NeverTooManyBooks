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
package com.hardbacknutter.nevertoomanybooks.database.dbsync;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Wrapper for statements that ensures locking is used.
 * <p>
 * Represents a statement that can be executed against a database.  The statement
 * cannot return multiple rows or columns, but single value (1 x 1) result sets
 * are supported.
 */
public class SynchronizedStatement
        implements Closeable {

    /** Log tag. */
    private static final String TAG = "SynchronizedStatement";

    /** Synchronizer from database. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Synchronizer mSync;
    /** Underlying statement. This class is final, so we cannot extend it. */
    private final SQLiteStatement mStatement;
    /** Indicates this is a 'read-only' statement. */
    private final boolean mIsReadOnly;
    /** DEBUG: Indicates this is a 'count' statement. */
    private boolean mIsCount;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor. Do not use directly!
     * <p>
     * Always use {@link SynchronizedDb#compileStatement(String)} to get a new instance.
     * (why? -> compileStatement uses locks)
     *
     * @param db  Database Access
     * @param sql the sql for this statement
     */
    public SynchronizedStatement(@NonNull final SynchronizedDb db,
                                 @NonNull final String sql) {
        mSync = db.getSynchronizer();
        mStatement = db.getUnderlyingDatabase().compileStatement(sql);
        // this is not a debug flag, but used to get a shared versus exclusive lock
        mIsReadOnly = sql.trim().toUpperCase(Locale.ENGLISH).startsWith("SELECT");

        if (BuildConfig.DEBUG /* always */) {
            mIsCount = sql.trim().toUpperCase(Locale.ENGLISH).startsWith("SELECT COUNT(");
        }
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a String value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, CAN be null, in which case {@link #bindNull} will be used.
     */
    public void bindString(final int index,
                           @Nullable final String value) {
        if (value == null) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "bindString|binding NULL", new Throwable());
            }
            mStatement.bindNull(index);
        } else {
            mStatement.bindString(index, value);
        }
    }

    /**
     * Wrapper for binding a boolean to a SQLiteStatement by morphing it to a long(1/0).
     * <p>
     * Bind a boolean value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind
     */
    public void bindBoolean(final int index,
                            final boolean value) {
        mStatement.bindLong(index, value ? 1 : 0);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a long value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind
     */
    public void bindLong(final int index,
                         final long value) {
        mStatement.bindLong(index, value);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a double value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind
     */
    @SuppressWarnings("unused")
    public void bindDouble(final int index,
                           final double value) {
        mStatement.bindDouble(index, value);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a byte array value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, CAN be null, in which case {@link #bindNull} will be used.
     */
    @SuppressWarnings("unused")
    void bindBlob(final int index,
                  @Nullable final byte[] value) {
        if (value == null) {
            mStatement.bindNull(index);
        } else {
            mStatement.bindBlob(index, value);
        }
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a NULL value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind null to
     */
    public void bindNull(final int index) {
        mStatement.bindNull(index);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Clears all existing bindings. Unset bindings are treated as NULL.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void clearBindings() {
        mStatement.clearBindings();
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;
        mStatement.close();
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     */
    public long simpleQueryForLong()
            throws SQLiteDoneException {
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            long result = mStatement.simpleQueryForLong();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR) {
                Log.d(TAG, "simpleQueryForLong|" + mStatement + "|result: " + result);
            }
            return result;
        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Syntax sugar. Converts an SQLiteDoneException into returning 0.
     * <p>
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     *
     * @return The result of the query, or 0 when no rows found
     */
    public long simpleQueryForLongOrZero() {
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            long result = mStatement.simpleQueryForLong();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR) {
                Log.d(TAG, "simpleQueryForLongOrZero|" + mStatement + "|result: " + result);
            }
            return result;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return 0;
        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Syntax sugar to identify SELECT COUNT(..) statements
     * <p>
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     * For example, SELECT COUNT(*) FROM table;
     *
     * @return The result of the query.
     */
    public long count() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR) {
            if (!mIsCount) {
                Log.d(TAG, "count|count statement not a count?", new Throwable());
            }
        }
        return simpleQueryForLongOrZero();
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     */
    @NonNull
    public String simpleQueryForString()
            throws SQLiteDoneException {
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            String result = mStatement.simpleQueryForString();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR) {
                Log.d(TAG, "simpleQueryForString|" + mStatement + '|' + result);
            }
            return result;

        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Syntax sugar. Converts an SQLiteDoneException into returning {@code null}.
     * <p>
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query, or {@code null} if not found.
     */
    @Nullable
    public String simpleQueryForStringOrNull() {
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            return mStatement.simpleQueryForString();
        } catch (@NonNull final SQLiteDoneException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "simpleQueryForStringOrNull|" + mStatement + "|NULL");
            }
            return null;
        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute this SQL statement, if it is not a SELECT / INSERT / DELETE / UPDATE, for example
     * CREATE / DROP table, view, trigger, index etc.
     */
    public void execute() {
        Synchronizer.SyncLock txLock;
        if (mIsReadOnly) {
            txLock = mSync.getSharedLock();
        } else {
            txLock = mSync.getExclusiveLock();
        }
        try {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_EXECUTE) {
                Log.d(TAG, "execute|" + mStatement);
            }
            mStatement.execute();
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(TAG, e, mStatement.toString());
            throw e;
        } finally {
            txLock.unlock();
        }
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute this SQL statement, if the number of rows affected by execution of this SQL
     * statement is of any importance to the caller - for example, UPDATE / DELETE SQL statements.
     *
     * @return the number of rows affected by this SQL statement execution.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int executeUpdateDelete() {
        Synchronizer.SyncLock exclusiveLock = mSync.getExclusiveLock();
        try {
            int rowsAffected = mStatement.executeUpdateDelete();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_EXECUTE_UPDATE_DELETE) {
                Log.d(TAG, "executeUpdateDelete|" + mStatement + "|rowsAffected=" + rowsAffected);
            }
            return rowsAffected;
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(TAG, e, mStatement.toString());
            throw e;
        } finally {
            exclusiveLock.unlock();
        }
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute this SQL statement and return the id of the row inserted due to this call.
     * The SQL statement should be an INSERT for this to be a useful call.
     *
     * @return the row id of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long executeInsert() {
        Synchronizer.SyncLock exclusiveLock = mSync.getExclusiveLock();
        try {
            long id = mStatement.executeInsert();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_EXECUTE_INSERT) {
                Log.d(TAG, "executeInsert|" + mStatement + "|id=" + id);

                if (id == -1) {
                    Logger.warnWithStackTrace(TAG, "Insert failed",
                                              "mStatement=" + mStatement);
                }
            }
            return id;
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(TAG, e, mStatement.toString());
            throw e;
        } finally {
            exclusiveLock.unlock();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SynchronizedStatement{"
               + "mIsCount=" + mIsCount
               + ", mIsReadOnly=" + mIsReadOnly
               + ", mCloseWasCalled=" + mCloseWasCalled
               + ", mStatement=" + mStatement.toString()
               + '}';
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            Logger.warn(TAG, "finalize",
                        "Closing unclosed statement:\n" + mStatement);
            if (mStatement != null) {
                mStatement.close();
            }
        }
        super.finalize();
    }
}
