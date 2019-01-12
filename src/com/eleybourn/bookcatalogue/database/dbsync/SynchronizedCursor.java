package com.eleybourn.bookcatalogue.database.dbsync;

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

    @NonNull
    private final Synchronizer mSync;
    private int mCount = -1;

    protected SynchronizedCursor(@NonNull final SQLiteCursorDriver driver,
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
            Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
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
        Synchronizer.SyncLock sharedLock = mSync.getSharedLock();
        try {
            return super.requery();
        } finally {
            sharedLock.unlock();
        }
    }
}
