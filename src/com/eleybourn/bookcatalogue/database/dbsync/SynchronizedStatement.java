package com.eleybourn.bookcatalogue.database.dbsync;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.Closeable;

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
    /** Underlying statement. */
    private final SQLiteStatement mStatement;
    /** Indicates this is a 'read-only' statement. */
    private final boolean mIsReadOnly;
    /** Indicates this is a 'count' statement. */
    private final boolean mIsCount;
    /** Indicates close() has been called. */
    private boolean mIsClosed;

    /**
     * Constructor.
     *
     * @param db the database
     * @param sql the sql for this statement
     */
    public SynchronizedStatement(@NonNull final SynchronizedDb db,
                                 @NonNull final String sql) {
        mSync = db.getSynchronizer();
        mIsReadOnly = sql.trim().toUpperCase().startsWith("SELECT");
        mIsCount = sql.trim().toUpperCase().startsWith("SELECT COUNT(");
        mStatement = db.getUnderlyingDatabase().compileStatement(sql);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     */
    @SuppressWarnings("unused")
    public void bindDouble(final int index,
                           final double value) {
        mStatement.bindDouble(index, value);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     */
    public void bindLong(final int index,
                         final long value) {
        mStatement.bindLong(index, value);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     */
    public void bindString(final int index,
                           @Nullable final String value) {
        if (value == null) {
            if (/* always print debug */ BuildConfig.DEBUG) {
                Logger.debug("binding NULL");
            }
            mStatement.bindNull(index);
        } else {
            mStatement.bindString(index, value);
        }
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
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
     */
    public void bindNull(final int index) {
        mStatement.bindNull(index);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
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
        mIsClosed = true;
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
            if (DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR && BuildConfig.DEBUG) {
                Logger.info(this, "simpleQueryForLong", mStatement.toString());
                Logger.info(this, "simpleQueryForLong","result: " + result);
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
            if (DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR && BuildConfig.DEBUG) {
                Logger.info(this, "simpleQueryForLongOrZero", mStatement.toString());
                Logger.info(this, "simpleQueryForLongOrZero","result: " + result);
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
        if (DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR && BuildConfig.DEBUG) {
            if (!mIsCount) {
                Logger.debug("count statement not a count?");
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
            if (DEBUG_SWITCHES.DB_SYNC_SIMPLE_QUERY_FOR && BuildConfig.DEBUG) {
                Logger.info(this, "simpleQueryForString",mStatement.toString());
                Logger.info(this, "simpleQueryForString",result);
            }
            return result;

        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Syntax sugar. Converts an SQLiteDoneException into returning null.
     * <p>
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query, or null if not found.
     */
    @Nullable
    public String simpleQueryForStringOrNull() {
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            return mStatement.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            if (/* always print debug */ BuildConfig.DEBUG) {
                Logger.info(this, "simpleQueryForStringOrNull",mStatement.toString());
                Logger.info(this, "simpleQueryForStringOrNull","NULL");
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
            if (DEBUG_SWITCHES.DB_SYNC_EXECUTE && BuildConfig.DEBUG) {
                Logger.info(this, "execute", mStatement.toString());
            }
            mStatement.execute();
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(e, mStatement.toString());
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
            if (DEBUG_SWITCHES.DB_SYNC_ROWS_AFFECTED && BuildConfig.DEBUG) {
                Logger.info(this, "executeUpdateDelete", mStatement.toString());
                Logger.info(this, "executeUpdateDelete","rowsAffected=" + rowsAffected);
            }
            return rowsAffected;
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(e, mStatement.toString());
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

            if (DEBUG_SWITCHES.DB_SYNC_EXECUTE_INSERT && BuildConfig.DEBUG) {
                Logger.info(this, "executeInsert", mStatement.toString());
                Logger.info(this, "executeInsert","id=" + id);
            }
            if (id == -1) {
                Logger.error("Insert failed");
            }
            return id;
        } catch (SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(e, mStatement.toString());
            throw e;
        } finally {
            exclusiveLock.unlock();
        }
    }

    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mIsClosed && BuildConfig.DEBUG) {
            Logger.info(this, "Finalizing non-closed statement (potential error/small)\n"
                    + mStatement.toString());
        }
        mStatement.close();
        super.finalize();
    }

    @Override
    @NonNull
    public String toString() {
        return "SynchronizedStatement{" + mStatement.toString() + '}';
    }
}
