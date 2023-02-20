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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.hardbacknutter.nevertoomanybooks.core.Logger;

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
    private final Synchronizer synchronizer;
    /** Underlying statement. This class is final, so we cannot extend it. */
    private final SQLiteStatement statement;
    /** Indicates this is a 'read-only' statement. */
    private final boolean readOnly;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final Logger logger;

    /**
     * Constructor. Do not use directly!
     * <p>
     * Always use {@link SynchronizedDb#compileStatement(String)} to get a new instance.
     * (why? -> compileStatement uses locks)
     *
     * @param synchronizer to use
     * @param statement    to execute
     * @param readOnly     flag; is the statement a read-only operation
     * @param logger       (optional) logger; passed in when in de debug mode
     */
    public SynchronizedStatement(@NonNull final Synchronizer synchronizer,
                                 @NonNull final SQLiteStatement statement,
                                 final boolean readOnly,
                                 @Nullable final Logger logger) {
        this.synchronizer = synchronizer;
        this.logger = logger;
        this.statement = statement;
        this.readOnly = readOnly;
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
            statement.bindNull(index);
        } else {
            statement.bindString(index, value);
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
        statement.bindLong(index, value ? 1 : 0);
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
        statement.bindLong(index, value);
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
        statement.bindDouble(index, value);
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
    public void bindBlob(final int index,
                         @Nullable final byte[] value) {
        if (value == null) {
            statement.bindNull(index);
        } else {
            statement.bindBlob(index, value);
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
        statement.bindNull(index);
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     * <p>
     * Clears all existing bindings. Unset bindings are treated as NULL.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void clearBindings() {
        statement.clearBindings();
    }

    /**
     * Wrapper for underlying method on SQLiteStatement.
     */
    @Override
    public void close() {
        statement.close();
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     * @see #simpleQueryForLongOrZero()
     */
    public long simpleQueryForLong()
            throws SQLiteDoneException {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            final long result = statement.simpleQueryForLong();
            if (logger != null) {
                logger.d(TAG, "simpleQueryForLong", statement + "|result=" + result);
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
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            final long result = statement.simpleQueryForLong();
            if (logger != null) {
                logger.d(TAG, "simpleQueryForLongOrZero", statement + "|result=" + result);
            }
            return result;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return 0;
        } finally {
            sharedLock.unlock();
        }
    }

    /**
     * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     * @see #simpleQueryForStringOrNull()
     */
    @SuppressWarnings("unused")
    @NonNull
    public String simpleQueryForString()
            throws SQLiteDoneException {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            final String result = statement.simpleQueryForString();

            if (logger != null) {
                logger.d(TAG, "simpleQueryForString", statement + "|result=" + result);
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
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return statement.simpleQueryForString();

        } catch (@NonNull final SQLiteDoneException e) {
            if (logger != null) {
                logger.d(TAG, "simpleQueryForStringOrNull", statement + "|NULL");
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
        final Synchronizer.SyncLock txLock;
        if (readOnly) {
            txLock = synchronizer.getSharedLock();
        } else {
            txLock = synchronizer.getExclusiveLock();
        }
        try {
            if (logger != null) {
                logger.d(TAG, "execute", statement);
            }
            statement.execute();
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
    public int executeUpdateDelete() {
        final Synchronizer.SyncLock exclusiveLock = synchronizer.getExclusiveLock();
        try {
            final int rowsAffected = statement.executeUpdateDelete();
            if (logger != null) {
                logger.d(TAG, "executeUpdateDelete", statement + "|rowsAffected=" + rowsAffected);
            }
            return rowsAffected;
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
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long executeInsert() {
        final Synchronizer.SyncLock exclusiveLock = synchronizer.getExclusiveLock();
        try {
            final long id = statement.executeInsert();

            if (logger != null) {
                logger.d(TAG, "executeInsert", statement + "|id=" + id);
                if (id == -1) {
                    logger.e(TAG, new Throwable(), "Insert failed|" + statement);
                }
            }
            return id;
        } finally {
            exclusiveLock.unlock();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SynchronizedStatement{"
               + ", readOnly=" + readOnly
               + ", statement=" + statement.toString()
               + '}';
    }
}
