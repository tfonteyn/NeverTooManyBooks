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
package com.hardbacknutter.nevertoomanybooks.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * DEBUG CLASS to help cursor leakage.
 * <p>
 * By using TrackedCursorFactory it is possible to use this class to analyze when and
 * where cursors are being allocated, and whether they are being de-allocated in a timely
 * fashion.
 * <p>
 * Most code is removed by BuildConfig.DEBUG for production.
 */
public class TrackedCursor
        extends SynchronizedCursor
        implements Closeable {

    /** Log tag. */
    private static final String TAG = "TrackedCursor";

    /** DEBUG instance counter, goes up and down. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    /** Used as a collection of known cursors. */
    private static final Collection<WeakReference<TrackedCursor>> CURSORS = new HashSet<>();

    /** Static counter for unique cursor ID's, only ever goes up. */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** id of the current cursor. */
    private int mId;

    /** We record a stack track when a cursor is created. */
    private StackTraceElement[] mStackTrace;

    /** Weak reference to this object, used in cursor collection. */
    @Nullable
    private WeakReference<TrackedCursor> mWeakRef;

    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    public TrackedCursor(@NonNull final SQLiteCursorDriver driver,
                         @NonNull final String editTable,
                         @NonNull final SQLiteQuery query,
                         @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            Log.d(TAG, "TrackedCursor|instances created: "
                       + DEBUG_INSTANCE_COUNTER.incrementAndGet());
            // Record who called us. It's only from about the 7th element that matters.
            mStackTrace = Thread.currentThread().getStackTrace();

            // Get the next ID
            mId = ID_COUNTER.incrementAndGet();
            // Save this cursor in the collection
            synchronized (CURSORS) {
                mWeakRef = new WeakReference<>(this);
                CURSORS.add(mWeakRef);
            }
        }
    }

    /**
     * DEBUG Dump all open cursors.
     */
    public static void dumpCursors() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            Collection<TrackedCursor> list = new ArrayList<>();
            synchronized (CURSORS) {
                for (WeakReference<TrackedCursor> r : CURSORS) {
                    TrackedCursor c1 = r.get();
                    if (c1 != null) {
                        list.add(c1);
                    }
                }
            }

            for (TrackedCursor c : list) {
                Log.d(TAG, "dumpCursors|Cursor=" + c.getCursorId());
                for (StackTraceElement s : c.getStackTrace()) {
                    Log.d(TAG, "dumpCursors"
                               + "|file=" + s.getFileName()
                               + "|Line=" + s.getLineNumber()
                               + "|Method=" + s.getMethodName());
                }
            }
        }
    }

    /**
     * Remove from collection on close.
     */
    @Override
    @CallSuper
    public void close() {
        super.close();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            if (!mCloseWasCalled) {
                removeCursor();
                mCloseWasCalled = true;
            }
            Log.d(TAG, "close|cursors left: " + DEBUG_INSTANCE_COUNTER.decrementAndGet());
        }
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     * <p>
     * Setting a break here can catch the exact moment that
     * a cursor is deleted before being closed.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize() {
        if (!mCloseWasCalled) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
                Logger.warn(TAG, "finalize|calling close()");
                removeCursor();
            }
        }
        super.finalize();
    }

    private void removeCursor() {
        if (mWeakRef != null) {
            synchronized (CURSORS) {
                CURSORS.remove(mWeakRef);
                mWeakRef.clear();
                mWeakRef = null;
            }

        }
    }

    /**
     * @return the stack trace recorded when the cursor was created so we can see who created it.
     */
    @NonNull
    private StackTraceElement[] getStackTrace() {
        return mStackTrace;
    }

    /**
     * @return the id of this cursor.
     */
    private long getCursorId() {
        return mId;
    }
}
