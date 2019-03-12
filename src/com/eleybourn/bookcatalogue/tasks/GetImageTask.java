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
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Task to get a thumbnail from the file system or covers database.
 * It will resize it as required and apply the resulting Bitmap to the related view.
 * <p>
 * We now use standard AsyncTask but run it on the parallel executor.
 *
 * @author Philip Warner
 */
public class GetImageTask
        extends AsyncTask<Void, Void, Void> {

    private static final AtomicInteger RUNNING_TASKS = new AtomicInteger();

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
    /** */
    private Exception mException;

    /**
     * Constructor. Clean the view and save the details of what we want.
     */
    @UiThread
    private GetImageTask(@NonNull final String uuid,
                         @NonNull final ImageView imageView,
                         final int maxWidth,
                         final int maxHeight,
                         final boolean cacheWasChecked) {
        clearOldTaskFromView(imageView);
        mView = new WeakReference<>(imageView);
        mCacheWasChecked = cacheWasChecked;

        mUuid = uuid;
        mWidth = maxWidth;
        mHeight = maxHeight;

        // Clear current image
        imageView.setImageBitmap(null);

        // Associate the view with this task
        imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, this);
    }

    /**
     * Create a task to convert, set and store the image for the passed book.
     * If cacheWasChecked = false, then the cache will be checked before any work is
     * done, and if found in the cache it will be used. This option is included to
     * reduce contention between background and foreground tasks: the foreground (UI)
     * thread checks the cache only if there are no background cache-related tasks
     * currently running.
     */
    @UiThread
    public static void newInstanceAndStart(@NonNull final String uuid,
                                           @NonNull final ImageView imageView,
                                           final int maxWidth,
                                           final int maxHeight,
                                           final boolean cacheWasChecked) {
        new GetImageTask(uuid, imageView, maxWidth, maxHeight, cacheWasChecked)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static boolean hasActiveTasks() {
        return RUNNING_TASKS.get() != 0;
    }

    /**
     * Remove any record of a prior thumbnail task from a View object.
     * <p>
     * Used internally and from Utils.getImageAndPutIntoView to ensure that nothing
     * overwrites the view.
     */
    public static void clearOldTaskFromView(@NonNull final ImageView imageView) {
        final GetImageTask oldTask = (GetImageTask) imageView.getTag(R.id.TAG_GET_THUMBNAIL_TASK);
        if (oldTask != null) {
            imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);
            oldTask.cancel(true);
        }
    }


    @Override
    @UiThread
    protected void onCancelled(final Void result) {
        cleanup();
    }

    /**
     * Do the image manipulation.
     * <p>
     * TODO: getImageAndPutIntoView is an expensive operation. Make sure its still needed.
     */
    @Override
    @WorkerThread
    protected Void doInBackground(final Void... params) {
        RUNNING_TASKS.incrementAndGet();

        try {
            // Get the view we are targeting and make sure it is valid
            ImageView view = mView.get();
            if (view == null) {
                mView.clear();
                return null;
            }

            // Make sure the view is still associated with this task.
            // We don't want to overwrite the wrong image in a recycled view.
            if (isCancelled() || !this.equals(view.getTag(R.id.TAG_GET_THUMBNAIL_TASK))) {
                return null;
            }

            // try cache
            if (!mCacheWasChecked) {
                File originalFile = StorageUtils.getCoverFile(mUuid);
                try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                    mBitmap = coversDBAdapter.getImage(originalFile, mUuid, mWidth, mHeight);
                }
                mWasInCache = (mBitmap != null);
            }

            if (isCancelled() || !this.equals(view.getTag(R.id.TAG_GET_THUMBNAIL_TASK))) {
                return null;
            }

            // wasn't in cache, try file system.
            if (mBitmap == null) {
                mBitmap = ImageUtils.getImage(mUuid, mWidth, mHeight, true,
                                              false, false);
            }

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception e) {
            mException = e;
            Logger.error(e);
        }
        return null;
    }

    @AnyThread
    private void cleanup() {
        RUNNING_TASKS.decrementAndGet();
    }

    /**
     * Handle the results of the task.
     */
    @Override
    @UiThread
    protected void onPostExecute(final Void result) {

        if (mException != null) {
            return;
        }

        // Get the view we are targeting and make sure it is valid
        ImageView imageView = mView.get();
        // Make sure the view is still associated with this task.
        // We don't want to overwrite the wrong image in a recycled view.
        final boolean viewIsValid = (imageView != null
                && this.equals(imageView.getTag(R.id.TAG_GET_THUMBNAIL_TASK)));

        // Clear the view tag
        if (viewIsValid) {
            imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);
        }

        if (mBitmap != null) {
            if (!mWasInCache && BooklistBuilder.imagesAreCached()) {
                // Queue the image to be written to the cache. Do it in a separate queue to avoid
                // delays in displaying image and to avoid contention -- the cache queue only has
                // one thread.
                // Tell the cache write it can be recycled if we don't have a valid view.
                ImageCacheWriterTask.writeToCache(mUuid, mWidth, mHeight, mBitmap, !viewIsValid);
            }
            if (viewIsValid) {
                //LayoutParams lp = new LayoutParams(mBitmap.getWidth(), mBitmap.getHeight());
                //view.setLayoutParams(lp);
                imageView.setImageBitmap(mBitmap);
            } else {
                mBitmap.recycle();
                mBitmap = null;
            }
        } else {
            if (imageView != null) {
                imageView.setImageResource(R.drawable.ic_image);
            }
        }

        mView.clear();
    }
}
