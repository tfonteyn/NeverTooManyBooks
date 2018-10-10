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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DEBUG CLASS to help com.eleybourn.bookcatalogue.debug cursor leakage.
 *
 * By using TrackedCursorFactory it is possible to use this class to analyze when and
 * where cursors are being allocated, and whether they are being de-allocated in a timely
 * fashion.
 *
 * Most code is removed by BuildConfig.DEBUG for production.
 *
 * @author Philip Warner
 */
public class TrackedCursor extends SynchronizedCursor implements Closeable {

    /** Used as a collection of known cursors */
    private static final Set<WeakReference<TrackedCursor>> mCursors = new HashSet<>();
    /** Global counter for unique cursor IDs */
    @NonNull
    private static Long mIdCounter = 0L;

    /** Debug counter */
    @NonNull
    private static Integer mInstanceCount = 0;
    /** ID of the current cursor */
    private Long mId;
    /** We record a stack track when a cursor is created. */
    private StackTraceElement[] mStackTrace;
    /** Weak reference to this object, used in cursor collection */
    @Nullable
    private WeakReference<TrackedCursor> mWeakRef;
    /** Already closed */
    private boolean mIsClosedFlg = false;

    public TrackedCursor(@NonNull final SQLiteCursorDriver driver,
                         @NonNull final String editTable,
                         @NonNull final SQLiteQuery query,
                         @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);

        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            synchronized (mInstanceCount) {
                mInstanceCount++;
                Logger.debug("Cursor instances: " + mInstanceCount);
            }

            // Record who called us. It's only from about the 7th element that matters.
            mStackTrace = Thread.currentThread().getStackTrace();

            // Get the next ID
            synchronized (mIdCounter) {
                mId = ++mIdCounter;
            }
            // Save this cursor in the collection
            synchronized (mCursors) {
                mWeakRef = new WeakReference<>(this);
                mCursors.add(mWeakRef);
            }
        }
    }

    /**
     * Get the total number of cursors that have not called close(). This is subtly
     * different from the list of open cursors because non-referenced cursors may
     * have been deleted and the finalizer not called.
     */
    @SuppressWarnings("unused")
    public static long getCursorCountApproximate() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            synchronized (mCursors) {
                return mCursors.size();
            }
        }
        return 0;
    }

    /**
     * DEBUG
     *
     * Get the total number of open cursors; verifies that existing weak refs are valid
     * and removes from collection if not.
     *
     * Note: This is not a *cheap* operation.
     */
    @SuppressWarnings({"unused", "UnusedAssignment"})
    public static long getCursorCount() {
        long count = 0;

        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            List<WeakReference<TrackedCursor>> list = new ArrayList<>();
            synchronized (mCursors) {
                for (WeakReference<TrackedCursor> r : mCursors) {
                    TrackedCursor c = r.get();
                    if (c != null) {
                        count++;
                    } else {
                        list.add(r);
                    }
                }
                for (WeakReference<TrackedCursor> r : list) {
                    mCursors.remove(r);
                }
            }
        }
        return count;
    }

    /**
     * DEBUG Dump all open cursors
     */
    public static void dumpCursors() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            List<TrackedCursor> cursors = getCursors();
            if (cursors == null) {
                Logger.debug("No cursors");
            } else {
                for (TrackedCursor c : cursors) {
                    Logger.debug("Cursor " + c.getCursorId());
                    for (StackTraceElement s : c.getStackTrace()) {
                        Logger.debug(s.getFileName() + "    Line " + s.getLineNumber() + " Method " + s.getMethodName());
                    }
                }
            }
        }
    }

    /**
     * DEBUG
     *
     * Get a collection of open cursors at the current time.
     */
    @SuppressWarnings("UnusedAssignment")
    @Nullable
    private static List<TrackedCursor> getCursors() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            List<TrackedCursor> list = new ArrayList<>();
            synchronized (mCursors) {
                for (WeakReference<TrackedCursor> r : mCursors) {
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
    public void close() {
        super.close();
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            if (!mIsClosedFlg) {
                synchronized (mInstanceCount) {
                    mInstanceCount--;
                    Logger.debug("Cursor instances: " + mInstanceCount);
                }
                if (mWeakRef != null)
                    synchronized (mCursors) {
                        mCursors.remove(mWeakRef);
                        mWeakRef.clear();
                        mWeakRef = null;
                    }
                mIsClosedFlg = true;
            }
        }
    }

    /**
     * Finalizer that does sanity check. Setting a break here can catch the exact moment that
     * a cursor is deleted before being closed.
     *
     * Note this is not guaranteed to be called by the JVM !
     */
    @Override
    public void finalize() {
        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            if (mWeakRef != null) {
                // This is a cursor that is being deleted before it is closed.
                // Setting a break here is sometimes useful.
                synchronized (mCursors) {
                    mCursors.remove(mWeakRef);
                    mWeakRef.clear();
                    mWeakRef = null;
                }
            }
        }
        super.finalize();
    }

    /**
     * Get the stack trace recorded when cursor created
     */
    @NonNull
    private StackTraceElement[] getStackTrace() {
        return mStackTrace;
    }

    /**
     * Get the ID of this cursor
     */
    private long getCursorId() {
        return mId;
    }
}
