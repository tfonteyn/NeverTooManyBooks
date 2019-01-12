/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEBUG CLASS to help com.eleybourn.bookcatalogue.debug cursor leakage.
 * <p>
 * By using TrackedCursorFactory it is possible to use this class to analyze when and
 * where cursors are being allocated, and whether they are being de-allocated in a timely
 * fashion.
 * <p>
 * Most code is removed by BuildConfig.DEBUG for production.
 *
 * @author Philip Warner
 */
public class TrackedCursor
        extends SynchronizedCursor
        implements Closeable {

    /** DEBUG instance counter, goes up and down. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    /** Used as a collection of known cursors. */
    private static final Set<WeakReference<TrackedCursor>> CURSORS = new HashSet<>();

    /** Static counter for unique cursor IDs, only ever goes up. */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** ID of the current cursor. */
    private int mId;

    /** We record a stack track when a cursor is created. */
    private StackTraceElement[] mStackTrace;

    /** Weak reference to this object, used in cursor collection. */
    @Nullable
    private WeakReference<TrackedCursor> mWeakRef;

    /** Already closed. */
    private boolean mIsClosedFlg;

    public TrackedCursor(@NonNull final SQLiteCursorDriver driver,
                         @NonNull final String editTable,
                         @NonNull final SQLiteQuery query,
                         @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);

        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            Logger.info(this, "instances created: "
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
     * DEBUG.
     * <p>
     * Get the total number of cursors that have not called close(). This is subtly
     * different from the list of open cursors because non-referenced cursors may
     * have been deleted and the finalizer not called.
     */
    @SuppressWarnings("unused")
    public static long getCursorCountApproximate() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            synchronized (CURSORS) {
                return CURSORS.size();
            }
        }
        return 0;
    }

    /**
     * DEBUG.
     * <p>
     * Get the total number of open cursors; verifies that existing weak refs are valid
     * and removes from collection if not.
     * <p>
     * Note: This is not a *cheap* operation.
     */
    @SuppressWarnings({"unused", "UnusedAssignment"})
    public static long getCursorCount() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            long count = 0;
            List<WeakReference<TrackedCursor>> list = new ArrayList<>();
            synchronized (CURSORS) {
                for (WeakReference<TrackedCursor> r : CURSORS) {
                    TrackedCursor c = r.get();
                    if (c != null) {
                        count++;
                    } else {
                        list.add(r);
                    }
                }
                for (WeakReference<TrackedCursor> r : list) {
                    CURSORS.remove(r);
                }
            }
            return count;
        }
        return 0;
    }

    /**
     * DEBUG Dump all open cursors.
     */
    public static void dumpCursors() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            @SuppressWarnings("UnusedAssignment")
            List<TrackedCursor> cursors = getCursors();
            if (cursors == null) {
                Logger.info(TrackedCursor.class, "No cursors");
            } else {
                for (TrackedCursor c : cursors) {
                    Logger.info(TrackedCursor.class, "Cursor " + c.getCursorId());
                    for (StackTraceElement s : c.getStackTrace()) {
                        Logger.info(TrackedCursor.class,
                                    s.getFileName() + "    Line " + s.getLineNumber()
                                            + " Method " + s.getMethodName());
                    }
                }
            }
        }
    }

    /**
     * DEBUG.
     * <p>
     * Get a collection of open cursors at the current time.
     */
    @SuppressWarnings("UnusedAssignment")
    @Nullable
    private static List<TrackedCursor> getCursors() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            List<TrackedCursor> list = new ArrayList<>();
            synchronized (CURSORS) {
                for (WeakReference<TrackedCursor> r : CURSORS) {
                    TrackedCursor c = r.get();
                    if (c != null) {
                        list.add(c);
                    }
                }
            }
            return list;
        }
        return null;
    }

    /**
     * Remove from collection on close.
     */
    @Override
    @CallSuper
    public void close() {
        super.close();
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            if (!mIsClosedFlg) {
                removeCursor();
                mIsClosedFlg = true;
            }
            Logger.info(this, "instances left: "
                    + DEBUG_INSTANCE_COUNTER.decrementAndGet());
        }
    }

    /**
     * Setting a break here can catch the exact moment that
     * a cursor is deleted before being closed.
     */
    @Override
    @CallSuper
    protected void finalize() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            // This is a cursor that is being deleted before it is closed.
            // Setting a break here is sometimes useful.
            removeCursor();
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
     * @return the ID of this cursor.
     */
    private long getCursorId() {
        return mId;
    }
}
