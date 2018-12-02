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
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CoversDBAdapter;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;

/**
 * Background task to save a bitmap into the covers thumbnail database. Runs in background
 * because it involves compression and IO, and can be safely queued. Failures can be ignored
 * because it is just writing to a cache used solely for optimization.
 *
 * This class also has its own static SimpleTaskQueue.
 *
 * @author Philip Warner
 */
public class ThumbnailCacheWriterTask implements SimpleTaskQueue.SimpleTask {

    /**
     * Single-thread queue for writing data. There is no point in more than one thread since
     * the database will force serialization of the updates.
     */
    private static final SimpleTaskQueue mQueue = new SimpleTaskQueue("ThumbnailCacheWriterTask", 1);
    /** Indicates if Bitmap can be recycled when no longer needed */
    private final boolean mCanRecycle;

    /** Cache ID of this object */
    private String mCacheId;
    /** Bitmap to store */
    private Bitmap mBitmap;

    /**
     * Create a task that will compress the passed bitmap and write it to the database,
     * it will also be recycled if flag is set.
     *
     * @param cacheId    Cache ID to use
     * @param source     Raw bitmap to store
     * @param canRecycle Indicates bitmap should be recycled after use
     */
    private ThumbnailCacheWriterTask(final @NonNull String cacheId,
                                     final @NonNull Bitmap source,
                                     final boolean canRecycle) {
        mCacheId = cacheId;
        mBitmap = source;
        mCanRecycle = canRecycle;
    }

    /**
     * Queue the passed bitmap to be compressed and written to the database, will be recycled if
     * flag is set.
     *
     * @param cacheId    Cache ID to use
     * @param source     Raw bitmap to store
     * @param canRecycle Indicates bitmap should be recycled after use
     */
    public static void writeToCache(final @NonNull String cacheId,
                                    final @NonNull Bitmap source,
                                    final boolean canRecycle) {
        ThumbnailCacheWriterTask t = new ThumbnailCacheWriterTask(cacheId, source, canRecycle);
        mQueue.enqueue(t);
    }

    /**
     * Check if there is an active task in the queue.
     */
    public static boolean hasActiveTasks() {
        return mQueue.hasActiveTasks();
    }

    /**
     * Do the main work in the background thread.
     */
    @Override
    public void run(final @NonNull SimpleTaskContext taskContext) {
        if (mBitmap.isRecycled()) {
            // Was probably recycled by rapid scrolling of view
            mBitmap = null;
        } else {
            // do not close this db.
            CoversDBAdapter coversDBAdapter = taskContext.getCoversDb();
            coversDBAdapter.saveFile(mBitmap, mCacheId);

            if (mCanRecycle) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }
        mCacheId = null;
    }

    @Override
    public void onFinish(Exception e) {
    }
}
