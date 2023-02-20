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

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

/**
 * Cursor wrapper that tries to apply locks as necessary. Unfortunately, most cursor
 * movement methods are final and, if they involve any database locking, could theoretically
 * still result in 'database is locked' exceptions. So far in testing, none have occurred.
 */
public class SynchronizedCursor
        extends SQLiteCursor {

    /** the database {@link Synchronizer}. */
    @NonNull
    private final Synchronizer synchronizer;
    /** cached count for the query. */
    private int count = -1;

    /**
     * Constructor.
     *
     * @param driver       SQLiteCursorDriver
     * @param editTable    the name of the table used for this query
     * @param query        the {@link SQLiteQuery} object associated with this cursor object.
     * @param synchronizer the database {@link Synchronizer}
     */
    public SynchronizedCursor(@NonNull final SQLiteCursorDriver driver,
                              @NonNull final String editTable,
                              @NonNull final SQLiteQuery query,
                              @NonNull final Synchronizer synchronizer) {
        super(driver, editTable, query);
        this.synchronizer = synchronizer;
    }

    /**
     * Wrapper that uses a lock before calling underlying method.
     */
    @Override
    public int getCount() {
        // Cache the count (it's what SQLiteCursor does), and we avoid locking
        if (count == -1) {
            final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
            try {
                count = super.getCount();
            } finally {
                sharedLock.unlock();
            }
        }
        return count;
    }

    /**
     * Wrapper that uses a lock before calling underlying method.
     */
    @Override
    @CallSuper
    public boolean requery() {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return super.requery();
        } finally {
            sharedLock.unlock();
        }
    }
}
