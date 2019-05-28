package com.eleybourn.bookcatalogue.database.dbsync;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Wrapper for statements that ensures locking is used.
 * <p>
 * Represents a statement that can be executed against a database.  The statement
 * cannot return multiple rows or columns, but single value (1 x 1) result sets
 * are supported.
 *
 * @author Philip Warner
 */
public class SynchronizedStatement
        implements Closeable {

    /** Synchronizer from database. */
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
     * @param db  the database
     * @param sql the sql for this statement
     */
    public SynchronizedStatement(@NonNull final SynchronizedDb db,
                                 @NonNull final String sql) {
        mSync = db.getSynchronizer();
        mStatement = db.getUnderlyingDatabase().compileStatement(sql);
        // this is not a debug flag, but used to get a shared versus exclusive lock
        mIsReadOnly = sql.trim().toUpperCase().startsWith("SELECT");

        if (BuildConfig.DEBUG /* always */) {
            mIsCount = sql.trim().toUpperCase().startsWith("SELECT COUNT(");
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
                Logger.debugWithStackTrace(this, "bindString", "binding NULL");
            }
            mStatement.bindNull(index);
        } else {
            mStatement.bindString(index, value);
        }
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
     *
     * Clears all existing bindings. Unset bindings are treated as NULL.
     */
    @SuppressWarnings("unused")
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
                Logger.debug(this, "simpleQueryForLong", mStatement);
                Logger.debug(this, "simpleQueryForLong", "result: " + result);
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
                Logger.debug(this, "simpleQueryForLongOrZero", mStatement);
                Logger.debug(this, "simpleQueryForLongOrZero", "result: " + result);
            }
            return result;
        } catch (SQLiteDoneException ignore) {
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
                Logger.debugWithStackTrace(this, "count", "count statement not a count?");
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
                Logger.debug(this, "simpleQueryForString", mStatement);
                Logger.debug(this, "simpleQueryForString", result);
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
        } catch (SQLiteDoneException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(this, "simpleQueryForStringOrNull", mStatement);
                Logger.debug(this, "simpleQueryForStringOrNull", "NULL");
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
                Logger.debug(this, "execute", mStatement);
            }
            mStatement.execute();
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e, mStatement.toString());
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
                Logger.debug(this, "executeUpdateDelete", mStatement);
                Logger.debug(this, "executeUpdateDelete", "rowsAffected=" + rowsAffected);
            }
            return rowsAffected;
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e, mStatement.toString());
            throw e;
        } finally {
            exclusiveLock.unlock();
        }
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute this SQL statement and return the ID of the row inserted due to this call.
     * The SQL statement should be an INSERT for this to be a useful call.
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long executeInsert() {
        Synchronizer.SyncLock exclusiveLock = mSync.getExclusiveLock();
        try {
            long id = mStatement.executeInsert();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_EXECUTE_INSERT) {
                Logger.debug(this, "executeInsert", mStatement);
                Logger.debug(this, "executeInsert", "id=" + id);
            }
            if (id == -1) {
                Logger.warnWithStackTrace(this, "Insert failed");
            }
            return id;
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e, mStatement.toString());
            throw e;
        } finally {
            exclusiveLock.unlock();
        }
    }

    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            Logger.warn(this, "finalize",
                        "Closing unclosed statement:\n" + mStatement);
            mStatement.close();
        }
        super.finalize();
    }

    @Override
    @NonNull
    public String toString() {
        return "SynchronizedStatement{"
                + mStatement.toString()
                + '}';
    }
}
