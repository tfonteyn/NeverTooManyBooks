/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Manages the construction and closure of persisted SQLiteStatement objects.
 * <p>
 * The purpose is not the actual caching of the statement for re-use in loops
 * (Android does that anyhow), but the handling of properly closing statements.
 * You do get extra caching across individual calls, but not sure if that makes any impact.
 */
public class SqlStatementManager
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "SqlStatementManager";

    @NonNull
    private final Map<String, SynchronizedStatement> mStatements =
            Collections.synchronizedMap(new HashMap<>());

    /** The underlying database. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    private final String mInstanceName;

    /**
     * Constructor.
     *
     * @param db   the database
     * @param name instance name; used for logging
     */
    public SqlStatementManager(@NonNull final SynchronizedDb db,
                               final String name) {
        mSyncedDb = db;
        mInstanceName = name;
    }

    /**
     * Get a statement from the cache. Create it if needed.
     *
     * @param name        of the statement
     * @param sqlSupplier a Supplier to get the SQL for the statement if compiling is needed
     *
     * @return the statement.
     */
    @NonNull
    public SynchronizedStatement get(@NonNull final String name,
                                     @NonNull final Supplier<String> sqlSupplier) {
        SynchronizedStatement stmt = mStatements.get(name);
        if (stmt == null) {
            stmt = mSyncedDb.compileStatement(sqlSupplier.get());
            mStatements.put(name, stmt);
        }
        return stmt;
    }

    /**
     * Close and remove <strong>all</strong> statements.
     * <p>
     * RuntimeException are caught and ignored.
     */
    @Override
    public void close() {
        for (SynchronizedStatement stmt : mStatements.values()) {
            try {
                stmt.close();
            } catch (@NonNull final RuntimeException ignore) {
                // ignore
            }
        }
        mStatements.clear();
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
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "finalize|" + mInstanceName);
            }
            close();
        }
        super.finalize();
    }
}
