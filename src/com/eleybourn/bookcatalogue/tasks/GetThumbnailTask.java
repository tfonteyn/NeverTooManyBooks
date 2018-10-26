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

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Task to get a thumbnail from the sdcard or cover database. It will resize it as required and
 * apply the resulting Bitmap to the related view.
 *
 * This object also has it's own statically defined SimpleTaskQueue for getting thumbnails in
 * background.
 *
 * @author Philip Warner
 */
public class GetThumbnailTask implements SimpleTask {

    /**
     * Queue for background thumbnail retrieval; allow 2 threads. More is nice, but with
     * many books to process it introduces what looks like lag when scrolling: 5 tasks
     * building now-invisible views is pointless.
     *
     * Despite above 'allow 2 threads', original code had '1' set so I presume even 2 was to much.
     * Given the number of cores has gone up these days, let's see what we can do....
     */
    @NonNull
    private static final SimpleTaskQueue mQueue;
    static {
        int maxTasks = 1;
        int nr = Runtime.getRuntime().availableProcessors();
        if (nr > 4) {
            maxTasks = 3; // just a poke in the dark TODO: experiment more
        }
        if (DEBUG_SWITCHES.TASK_MANAGER && BuildConfig.DEBUG) {
            Logger.info(GetThumbnailTask.class,"GetThumbnailTask: #cpu     : " + nr);
            Logger.info(GetThumbnailTask.class,"GetThumbnailTask: #maxTasks: " + maxTasks);
        }
        mQueue = new SimpleTaskQueue("thumbnails", maxTasks);
    }

    /** Reference to the view we are using */
    @NonNull
    private final WeakReference<ImageView> mView;
    /** ID of book whose cover we are getting */
    @NonNull
    private final String mBookHash;
    /** Options indicating original caller had checked cache */
    private final boolean mCacheWasChecked;
    /** The width of the thumbnail retrieved (based on preferences) */
    private final int mWidth;
    /** The height of the thumbnail retrieved (based on preferences) */
    private final int mHeight;
    @NonNull
    private final Context mContext;
    /** Resulting bitmap object */
    @Nullable
    private Bitmap mBitmap = null;
    /** Options indicating image was found in the cache */
    private boolean mWasInCache = false;
    /** Indicated we want the queue manager to call the finished() method. */
    private boolean mWantFinished = true;

    /**
     * Constructor. Clean the view and save the details of what we want.
     */
    private GetThumbnailTask(@NonNull final Context context,
                             @NonNull final String hash,
                             @NonNull final ImageView v,
                             final int maxWidth,
                             final int maxHeight,
                             final boolean cacheWasChecked) {
        clearOldTaskFromView(v);
        mContext = context;
        mView = new WeakReference<>(v);
        mCacheWasChecked = cacheWasChecked;

        mBookHash = hash;
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
    public static void getThumbnail(@NonNull final Context context,
                                    @NonNull final String hash,
                                    @NonNull final ImageView view,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean cacheWasChecked) {
        GetThumbnailTask t = new GetThumbnailTask(context, hash, view, maxWidth, maxHeight, cacheWasChecked);
        mQueue.enqueue(t);
    }

    /**
     * Allow other tasks (or subclasses tasks) to be queued.
     *
     * @param t Task to put in queue
     */
    public static void enqueue(@NonNull final SimpleTask t) {
        mQueue.enqueue(t);
    }

    public static boolean hasActiveTasks() {
        return mQueue.hasActiveTasks();
    }

    /**
     * Utility routine to remove any record of a prior thumbnail task from a View object.
     *
     * Used internally and from Utils.fetchFileIntoImageView to ensure that nothing
     * overwrites the view.
     */
    public static void clearOldTaskFromView(@NonNull final ImageView v) {
        final GetThumbnailTask oldTask = ViewTagger.getTag(v, R.id.TAG_GET_THUMBNAIL_TASK);
        if (oldTask != null) {
            ViewTagger.setTag(v, R.id.TAG_GET_THUMBNAIL_TASK, null);
            mQueue.remove(oldTask);
        }
    }

    /**
     * Do the image manipulation.
     *
     * Code commented out for now: We wait at start to prevent a flood of images from hitting the UI thread.
     *
     * TODO: fetchBookCoverIntoImageView is an expensive operation. Make sure its still needed.
     */
    @Override
    public void run(@NonNull final SimpleTaskContext taskContext) {
			/*
			try {
				Thread.sleep(10); // Let the UI have a chance to do something if we are racking up images!
			} catch (InterruptedException error) {
			}
			*/

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


        if (!mCacheWasChecked) {
            File originalFile = StorageUtils.getCoverFile(mBookHash);
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(mContext)) {
                mBitmap = coversDbHelper.fetchCachedImageIntoImageView(originalFile,
                        null, mBookHash, mWidth, mHeight);
            }
            mWasInCache = (mBitmap != null);
        }

        if (mBitmap == null) {
            mBitmap = ImageUtils.fetchBookCoverIntoImageView(null, mBookHash,
                    mWidth, mHeight, true, false, false);
        }


        taskContext.setRequiresFinish(mWantFinished);
    }

    /**
     * Handle the results of the task.
     */
    @Override
    public void onFinish(Exception e) {
        if (!mWantFinished) {
            return;
        }

        // Get the view we are targeting and make sure it is valid
        ImageView view = mView.get();
        // Make sure the view is still associated with this task.
        // We don't want to overwrite the wrong image in a recycled view.
        final boolean viewIsValid = (view != null && this.equals(ViewTagger.getTag(view, R.id.TAG_GET_THUMBNAIL_TASK)));

        // Clear the view tag
        if (viewIsValid) {
            ViewTagger.setTag(view, R.id.TAG_GET_THUMBNAIL_TASK, null);
        }

        if (mBitmap != null) {
            if (!mWasInCache && BooklistPreferencesActivity.isThumbnailCacheEnabled()) {
                // Queue the image to be written to the cache. Do it in a separate queue to avoid
                // delays in displaying image and to avoid contention -- the cache queue only has
                // one thread. Tell the cache write it can be recycled if we don't have a valid view.
                ThumbnailCacheWriterTask.writeToCache(mContext, CoversDbHelper.getThumbnailCoverCacheId(mBookHash, mWidth, mHeight), mBitmap, !viewIsValid);
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
