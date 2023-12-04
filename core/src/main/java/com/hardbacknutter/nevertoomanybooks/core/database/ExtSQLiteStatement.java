/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;

/**
 * A simple wrapper for an {@link SQLiteStatement} which is a final class, so we cannot extend it.
 * <p>
 * Provides convenience methods and debug output.
 */
@SuppressWarnings({"unused", "WeakerAccess", "MissingJavadoc"})
public class ExtSQLiteStatement
        implements Closeable {

    /** Log tag. */
    private static final String TAG = "ExtSQLiteStatement";

    @NonNull
    private final SQLiteStatement statement;

    /**
     * Constructor.
     *
     * @param statement to wrap
     */
    public ExtSQLiteStatement(@NonNull final SQLiteStatement statement) {
        this.statement = statement;
    }

    /**
     * {@code null}-aware wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a String value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, can be {@code null},
     *              in which case {@link #bindNull} will be used.
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
     * {@code null}-aware wrapper for underlying method on SQLiteStatement.
     * <p>
     * Bind a byte array value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, can be {@code null},
     *              in which case {@link #bindNull} will be used.
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
    public void bindDouble(final int index,
                           final double value) {
        statement.bindDouble(index, value);
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

    public void clearBindings() {
        statement.clearBindings();
    }

    @Override
    public void close() {
        statement.close();
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     * @see #simpleQueryForLongOrZero()
     */
    public long simpleQueryForLong()
            throws SQLiteDoneException {
        final long result = statement.simpleQueryForLong();
        if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger()
                         .d(TAG, "simpleQueryForLong", statement + "|long=" + result);
        }
        return result;
    }

    /**
     * Syntax sugar. Converts an {@link SQLiteDoneException} into returning 0.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     *
     * @return The result of the query, or {@code 0} if no rows found
     */
    public long simpleQueryForLongOrZero() {
        try {
            return simpleQueryForLong();
        } catch (@NonNull final SQLiteDoneException ignore) {
            return 0;
        }
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query.
     *
     * @throws SQLiteDoneException if the query returns zero rows
     * @see #simpleQueryForStringOrNull()
     */
    @NonNull
    public String simpleQueryForString()
            throws SQLiteDoneException {
        final String result = statement.simpleQueryForString();
        if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger()
                         .d(TAG, "simpleQueryForString", statement + "|string=" + result);
        }
        return result;
    }

    /**
     * Syntax sugar. Converts an {@link SQLiteDoneException} into returning {@code null}.
     * <p>
     * Execute a statement that returns a 1 by 1 table with a text value.
     *
     * @return The result of the query, or {@code null} if no rows found
     */
    @Nullable
    public String simpleQueryForStringOrNull() {
        try {
            return simpleQueryForString();
        } catch (@NonNull final SQLiteDoneException e) {
            if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
                LoggerFactory.getLogger()
                             .d(TAG, "simpleQueryForStringOrNull", super.toString() + "|NULL");
            }
            return null;
        }
    }

    /**
     * Execute this SQL statement, if it is not a SELECT / INSERT / DELETE / UPDATE, for example
     * CREATE / DROP table, view, trigger, index etc.
     */
    public void execute() {
        if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger()
                         .d(TAG, "execute", statement);
        }
        //noinspection CheckStyle
        try {
            statement.execute();
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e, statement);
            throw e;
        }
    }

    /**
     * Execute this SQL statement, if the number of rows affected by execution of this SQL
     * statement is of any importance to the caller - for example, UPDATE / DELETE SQL statements.
     *
     * @return the number of rows affected by this SQL statement execution.
     */
    public int executeUpdateDelete() {
        final int rowsAffected;
        //noinspection CheckStyle
        try {
            rowsAffected = statement.executeUpdateDelete();
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e, statement);
            throw e;
        }

        if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger()
                         .d(TAG, "executeUpdateDelete",
                            statement + "|rowsAffected=" + rowsAffected);
        }
        return rowsAffected;
    }

    /**
     * Execute this SQL statement and return the id of the row inserted due to this call.
     * The SQL statement should be an INSERT for this to be a useful call.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long executeInsert() {
        final long id;
        //noinspection CheckStyle
        try {
            id = statement.executeInsert();
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e, statement);
            throw e;
        }

        if (BuildConfig.DEBUG && LoggerFactory.DEBUG_EXEC_SQL) {
            final Logger logger = LoggerFactory.getLogger();
            logger.d(TAG, "executeInsert", statement + "|id=" + id);
            if (id == -1) {
                logger.e(TAG, new Throwable(), "Insert failed|" + statement);
            }
        }
        return id;
    }

    @NonNull
    @Override
    public String toString() {
        return "ExtSQLiteStatement{"
               + "statement=" + statement
               + '}';
    }
}
