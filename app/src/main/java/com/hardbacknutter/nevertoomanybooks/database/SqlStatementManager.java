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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Manages the construction and closure of persisted SQLiteStatement objects.
 * <p>
 * The purpose is not the actual caching of the statement for re-use in loops
 * (Android does that anyhow), but the handling of properly closing statements.
 * You do get extra caching across individual calls,
 * but not sure if that makes any impact.
 * <p>
 * Typical usage:
 * <pre>
 *     {@code
 *     SynchronizedStatement stmt = mStatementManager.get(STMT_1);
 *     if (stmt == null) {
 *         stmt = mStatementManager.add(STMT_1, "SELECT id FROM table WHERE col=?");
 *         long id;
 *         // Be cautious; other threads may use the cached stmt, and set parameters.
 *         synchronized (stmt) {
 *             stmt.bindLong(1, value);
 *             id = stmt.simpleQueryForLongOrZero();
 *         }
 *         ...
 *     }
 * </pre>
 */
public class SqlStatementManager
        implements AutoCloseable {

    @NonNull
    private final Map<String, SynchronizedStatement> mStatements =
            // not sure sync is needed. But this used to be a HashTable.
            Collections.synchronizedMap(new HashMap<>());

    /** The underlying database. */
    @Nullable
    private final SynchronizedDb mSyncedDb;

    SqlStatementManager() {
        mSyncedDb = null;
    }

    public SqlStatementManager(@Nullable final SynchronizedDb db) {
        mSyncedDb = db;
    }

    /**
     * Get a statement from the cache.
     *
     * @param name of the statement
     *
     * @return the statement, or {@code null} if it did not exist.
     */
    @Nullable
    public SynchronizedStatement get(@NonNull final String name) {
        return mStatements.get(name);
    }

    /**
     * Add a statement to the cache.
     * If already present, will close the old one and replace it with the new one.
     *
     * @param name of the statement
     * @param sql  of the statement
     *
     * @return the statement
     */
    @NonNull
    public SynchronizedStatement add(@NonNull final String name,
                                     @NonNull final String sql) {
        Objects.requireNonNull(mSyncedDb, "Database not set when SqlStatementManager created");
        return add(mSyncedDb, name, sql);
    }

    /**
     * Add a statement to the cache.
     * If already present, will close the old one and replace it with the new one.
     *
     * @param name of the statement
     * @param sql  of the statement
     *
     * @return the statement
     */
    @NonNull
    public SynchronizedStatement add(@NonNull final SynchronizedDb db,
                                     @NonNull final String name,
                                     @NonNull final String sql) {
        SynchronizedStatement old = mStatements.get(name);
        if (old != null) {
            old.close();
        }
        SynchronizedStatement stmt = db.compileStatement(sql);
        mStatements.put(name, stmt);
        return stmt;
    }

    public boolean isEmpty() {
        return mStatements.isEmpty();
    }

    /**
     * Close and remove the given statements.
     *
     * @param names statement names
     */
    public void close(@NonNull final String... names) {
        synchronized (mStatements) {
            for (String name : names) {
                try {
                    SynchronizedStatement stmt = mStatements.get(name);
                    if (stmt != null) {
                        stmt.close();
                        mStatements.remove(name);
                    }
                } catch (@NonNull final RuntimeException ignored) {
                }
            }
        }
    }

    /**
     * Close and remove <strong>all</strong> statements.
     * <p>
     * RuntimeException are caught and ignored.
     */
    @Override
    public void close() {
        synchronized (mStatements) {
            for (SynchronizedStatement stmt : mStatements.values()) {
                try {
                    stmt.close();
                } catch (@NonNull final RuntimeException ignored) {
                }
            }
            mStatements.clear();
        }
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mStatements.isEmpty()) {
            Logger.warn(this, "finalize", "closing statements.");
            close();
        }
        super.finalize();
    }

    /**
     * DEBUG help.
     *
     * @return list of all the names of the managed statements
     */
    @NonNull
    public List<String> getNames() {
        return new ArrayList<>(mStatements.keySet());
    }
}
