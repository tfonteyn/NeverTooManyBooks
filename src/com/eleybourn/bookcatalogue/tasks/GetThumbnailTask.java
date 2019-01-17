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
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Task to get a thumbnail from the sdcard or cover database. It will resize it as required and
 * apply the resulting Bitmap to the related view.
 * <p>
 * This object also has it's own statically defined SimpleTaskQueue for getting thumbnails in
 * background.
 *
 * @author Philip Warner
 */
public class GetThumbnailTask
        implements SimpleTaskQueue.SimpleTask {

    /**
     * Queue for background thumbnail retrieval; allow 2 threads. More is nice, but with
     * many books to process it introduces what looks like lag when scrolling: 5 tasks
     * building now-invisible views is pointless.
     * <p>
     * Despite above 'allow 2 threads', original code had '1' set; so I presume even 2 was to much.
     * Given the number of cores has gone up these days, let's see what we can do....
     */
    @NonNull
    private static final SimpleTaskQueue TASK_QUEUE;

    static {
        int maxTasks = 1;
        int nr = Runtime.getRuntime().availableProcessors();
        if (nr > 4) {
            // just a poke in the dark TODO: experiment more
            maxTasks = 3;
        }
        if (DEBUG_SWITCHES.TASK_MANAGER && BuildConfig.DEBUG) {
            Logger.info(GetThumbnailTask.class, "GetThumbnailTask: #cpu     : " + nr);
            Logger.info(GetThumbnailTask.class, "GetThumbnailTask: #maxTasks: " + maxTasks);
        }
        TASK_QUEUE = new SimpleTaskQueue("GetThumbnailTask", maxTasks);
    }

    /** Reference to the view we are using. */
    @NonNull
    private final WeakReference<ImageView> mView;
    /** ID of book whose cover we are getting. */
    @NonNull
    private final String mUuid;
    /** Flag indicating original caller had checked cache. */
    private final boolean mCacheWasChecked;
    /** The width of the thumbnail retrieved (based on preferences). */
    private final int mWidth;
    /** The height of the thumbnail retrieved (based on preferences). */
    private final int mHeight;
    /** Resulting bitmap object. */
    @Nullable
    private Bitmap mBitmap;
    /** Flag indicating image was found in the cache. */
    private boolean mWasInCache;
    /** Indicated we want the queue manager to call the finished() method. */
    private boolean mWantFinished = true;

    /**
     * Constructor. Clean the view and save the details of what we want.
     */
    private GetThumbnailTask(@NonNull final String hash,
                             @NonNull final ImageView v,
                             final int maxWidth,
                             final int maxHeight,
                             final boolean cacheWasChecked) {
        clearOldTaskFromView(v);
        mView = new WeakReference<>(v);
        mCacheWasChecked = cacheWasChecked;

        mUuid = hash;
        mWidth = maxWidth;
        mHeight = maxHeight;

        // Clear current image
        v.setImageBitmap(null);

        // Associate the view with this task
        ViewTagger.setTag(v, R.id.TAG_GET_THUMBNAIL_TASK, this);
    }

    /**
     * Create a task to convert, set and store the thumbnail for the passed book.
     * If cacheWasChecked = false, then the cache will be checked before any work is
     * done, and if found in the cache it will be used. This option is included to
     * reduce contention between background and foreground tasks: the foreground (UI)
     * thread checks the cache only if there are no background cache-related tasks
     * currently running.
     */
    public static void getThumbnail(@NonNull final String uuid,
                                    @NonNull final ImageView view,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean cacheWasChecked) {
        GetThumbnailTask t = new GetThumbnailTask(uuid, view, maxWidth, maxHeight, cacheWasChecked);
        TASK_QUEUE.enqueue(t);
    }

    /**
     * Allow other tasks (or subclasses tasks) to be queued.
     *
     * @param task Task to put in queue
     */
    public static void enqueue(@NonNull final SimpleTaskQueue.SimpleTask task) {
        TASK_QUEUE.enqueue(task);
    }

    public static boolean hasActiveTasks() {
        return TASK_QUEUE.hasActiveTasks();
    }

    /**
     * Remove any record of a prior thumbnail task from a View object.
     * <p>
     * Used internally and from Utils.fetchFileIntoImageView to ensure that nothing
     * overwrites the view.
     */
    public static void clearOldTaskFromView(@NonNull final ImageView imageView) {
        final GetThumbnailTask oldTask = ViewTagger.getTag(imageView, R.id.TAG_GET_THUMBNAIL_TASK);
        if (oldTask != null) {
            ViewTagger.setTag(imageView, R.id.TAG_GET_THUMBNAIL_TASK, null);
            TASK_QUEUE.remove(oldTask);
        }
    }

    /**
     * Do the image manipulation.
     * <p>
     * Code commented out for now: We wait at start to prevent a flood of images
     * from hitting the UI thread.
     * <p>
     * TODO: fetchFileIntoImageView is an expensive operation. Make sure its still needed.
     */
    @Override
    public void run(@NonNull final SimpleTaskContext taskContext) {
//        try {
//            // Let the UI have a chance to do something if we are racking up images!
//            Thread.sleep(10);
//        } catch (InterruptedException error) {
//        }


        // Get the view we are targeting and make sure it is valid
        ImageView v = mView.get();
        if (v == null) {
            mView.clear();
            mWantFinished = false;
            return;
        }

        // Make sure the view is still associated with this task.
        // We don't want to overwrite the wrong image in a recycled view.
        if (!this.equals(ViewTagger.getTag(v, R.id.TAG_GET_THUMBNAIL_TASK))) {
            mWantFinished = false;
            return;
        }

        // try cache
        if (!mCacheWasChecked) {
            File originalFile = StorageUtils.getCoverFile(mUuid);
            CoversDBA coversDBAdapter = taskContext.getCoversDb();
            mBitmap = coversDBAdapter.fetchCachedImage(originalFile, mUuid, mWidth, mHeight);

            mWasInCache = (mBitmap != null);
        }

        // wasn't in cache, try file system
        if (mBitmap == null) {
            mBitmap = ImageUtils.fetchFileIntoImageView(null, mUuid,
                                                        mWidth, mHeight, true,
                                                        false, false);
        }

        taskContext.setRequiresFinish(mWantFinished);
    }

    /**
     * Handle the results of the task.
     */
    @Override
    public void onFinish(@Nullable final Exception e) {
        if (!mWantFinished) {
            return;
        }

        // Get the view we are targeting and make sure it is valid
        ImageView view = mView.get();
        // Make sure the view is still associated with this task.
        // We don't want to overwrite the wrong image in a recycled view.
        final boolean viewIsValid = (view != null
                && this.equals(ViewTagger.getTag(view, R.id.TAG_GET_THUMBNAIL_TASK)));

        // Clear the view tag
        if (viewIsValid) {
            ViewTagger.setTag(view, R.id.TAG_GET_THUMBNAIL_TASK, null);
        }

        if (mBitmap != null) {
            if (!mWasInCache && BooklistBuilder.thumbnailsAreCached()) {
                // Queue the image to be written to the cache. Do it in a separate queue to avoid
                // delays in displaying image and to avoid contention -- the cache queue only has
                // one thread.
                // Tell the cache write it can be recycled if we don't have a valid view.
                ThumbnailCacheWriterTask.writeToCache(
                        CoversDBA.getThumbnailCoverCacheId(mUuid, mWidth, mHeight),
                        mBitmap, !viewIsValid);
            }
            if (viewIsValid) {
                //LayoutParams lp = new LayoutParams(mBitmap.getWidth(), mBitmap.getHeight());
                //v.setLayoutParams(lp);
                view.setImageBitmap(mBitmap);
            } else {
                mBitmap.recycle();
                mBitmap = null;
            }
        } else {
            if (view != null) {
                view.setImageResource(R.drawable.ic_broken_image);
            }
        }

        mView.clear();
    }
}
