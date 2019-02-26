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

package com.eleybourn.bookcatalogue.tasks;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.database.CoversDBA;

/**
 * Background task to save a bitmap into the covers thumbnail database. Runs in background
 * because it involves compression and IO, and can be safely queued. Failures can be ignored
 * because it is just writing to a cache used solely for optimization.
 * <p>
 * Standard AsyncTask for writing data. There is no point in more than one thread since
 * the database will force serialization of the updates.
 *
 * @author Philip Warner
 */
public final class ImageCacheWriterTask
        extends AsyncTask<Void, Void, Void> {

    private static final AtomicInteger runningTasks = new AtomicInteger();

    /** Indicates if Bitmap can be recycled when no longer needed. */
    private final boolean mCanRecycle;
    /** Cache ID of this object. */
    private String mCacheId;
    /** Bitmap to store. */
    private Bitmap mBitmap;

    /**
     * Create a task that will compress the passed bitmap and write it to the database,
     * it will also be recycled if flag is set.
     *
     * @param source     Raw bitmap to store
     * @param canRecycle Indicates bitmap should be recycled after use
     */
    @UiThread
    private ImageCacheWriterTask(@NonNull final String uuid,
                                 final int maxWidth,
                                 final int maxHeight,
                                 @NonNull final Bitmap source,
                                 final boolean canRecycle) {
        mCacheId = CoversDBA.constructCacheId(uuid, maxWidth, maxHeight);
        mBitmap = source;
        mCanRecycle = canRecycle;
    }

    /**
     * Queue the passed bitmap to be compressed and written to the database,
     * will be recycled if flag is set.
     *
     * @param source     Raw bitmap to store
     * @param canRecycle Indicates bitmap should be recycled after use
     */
    @UiThread
    static void writeToCache(@NonNull final String uuid,
                             final int maxWidth,
                             final int maxHeight,
                             @NonNull final Bitmap source,
                             final boolean canRecycle) {

        new ImageCacheWriterTask(uuid, maxWidth, maxHeight, source, canRecycle).execute();
    }

    /**
     * @return <tt>true</tt> if there is an active task in the queue.
     */
    @UiThread
    public static boolean hasActiveTasks() {
        return runningTasks.get() != 0;
    }

    /**
     * Do the main work in the background thread.
     */
    @Override
    @WorkerThread
    protected Void doInBackground(final Void... params) {
        runningTasks.incrementAndGet();

        if (mBitmap.isRecycled()) {
            // Was probably recycled by rapid scrolling of view
            mBitmap = null;
        } else {
            try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                coversDBAdapter.saveFile(mBitmap, mCacheId);
            }

            if (mCanRecycle) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }
        mCacheId = null;

        runningTasks.decrementAndGet();
        return null;
    }
}
