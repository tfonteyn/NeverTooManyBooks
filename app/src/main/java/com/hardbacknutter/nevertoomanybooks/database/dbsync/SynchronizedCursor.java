/*
 * @Copyright 2020 HardBackNutter
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
    private final Synchronizer mSync;
    /** cached count for the query. */
    private int mCount = -1;

    /**
     * Constructor.
     *
     * @param driver    SQLiteCursorDriver
     * @param editTable the name of the table used for this query
     * @param query     the {@link SQLiteQuery} object associated with this cursor object.
     * @param sync      the database {@link Synchronizer}
     */
    public SynchronizedCursor(@NonNull final SQLiteCursorDriver driver,
                              @NonNull final String editTable,
                              @NonNull final SQLiteQuery query,
                              @NonNull final Synchronizer sync) {
        super(driver, editTable, query);
        mSync = sync;
    }

    /**
     * Wrapper that uses a lock before calling underlying method.
     */
    @Override
    public int getCount() {
        // Cache the count (it's what SQLiteCursor does), and we avoid locking
        if (mCount == -1) {
            final Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
            try {
                mCount = super.getCount();
            } finally {
                sharedLock.unlock();
            }
        }
        return mCount;
    }

    /**
     * Wrapper that uses a lock before calling underlying method.
     */
    @Override
    @CallSuper
    public boolean requery() {
        final Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            return super.requery();
        } finally {
            sharedLock.unlock();
        }
    }
}
