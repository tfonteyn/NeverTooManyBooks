/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection;

// collapse all lines, restart app
// scroll to Pratchett on 1st line, expand, scroll to end.
////////////////////////////////////////////////////////////////////////////
// using tasks, no caching
// ImageUtils.taskChecks=175|ImageUtils.taskTicks=13580
// ImageUtils.taskChecks=175|ImageUtils.taskTicks=11879
////////////////////////////////////////////////////////////////////////////
// NO tasks, no cache
// ImageUtils.fileChecks=175|ImageUtils.fileChecks=2065
// ImageUtils.fileChecks=175|ImageUtils.fileTicks=2094
////////////////////////////////////////////////////////////////////////////
// from cache
// ImageUtils.cacheChecks=175|ImageUtils.cacheTicks=2305
////////////////////////////////////////////////////////////////////////////

public final class ImageUtils {

    /*
     * Scaling of thumbnails.
     * Must be kept in sync with res/values/strings-preferences.xml#pv_cover_scale_factor
     *
     * res/xml/preferences_book_style.xml contains the default set to 2
     */
    /** Thumbnail Scaling. */
    @SuppressWarnings("unused")
    public static final int SCALE_X_SMALL = 1;
    /** Thumbnail Scaling. */
    public static final int SCALE_SMALL = 2;
    /** Thumbnail Scaling. */
    public static final int SCALE_MEDIUM = 3;
    /** Thumbnail Scaling. */
    public static final int SCALE_LARGE = 5;
    /** Thumbnail Scaling. */
    public static final int SCALE_X_LARGE = 8;
    /** Thumbnail Scaling. */
    public static final int SCALE_2X_LARGE = 12;

    private static final int BUFFER_SIZE = 65536;

    public static AtomicLong cacheTicks = new AtomicLong();
    public static AtomicLong fileTicks = new AtomicLong();
    public static AtomicLong taskTicks = new AtomicLong();

    public static AtomicInteger cacheChecks = new AtomicInteger();
    public static AtomicInteger fileChecks = new AtomicInteger();
    public static AtomicInteger taskChecks = new AtomicInteger();

    private ImageUtils() {
    }

    /**
     * Get the maximum pixel size an image should be based on the desired scale factor.
     *
     * @param scale to apply
     *
     * @return amount in pixels
     */
    public static int getMaxImageSize(final int scale) {
        return scale * (int) App.getAppContext().getResources()
                                .getDimension(R.dimen.cover_base_size);
    }

    /**
     * Called in the UI thread. Will either
     * (1) use a cached cover or
     * (2) start a background task to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date versus
     * the cache date. If the cached image date is < the file, it is rebuilt.
     *
     * @param imageView  View to populate
     * @param uuid      UUID of book to retrieve.
     * @param maxWidth  Max width of resulting image
     * @param maxHeight Max height of resulting image
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView imageView,
                                    @NonNull final String uuid,
                                    final int maxWidth,
                                    final int maxHeight) {

        boolean cacheWasChecked = false;

        Context context = imageView.getContext();
        long tick;

        // 1. If we want to check the cache, AND we don't have cache building happening, check it.
        if (BooklistBuilder.imagesAreCached(context)
            && !GetImageTask.hasActiveTasks()
            && !CoversDAO.ImageCacheWriterTask.hasActiveTasks()) {

            tick = System.nanoTime();
            Bitmap bm = CoversDAO.getImage(uuid, maxWidth, maxHeight);
            cacheChecks.incrementAndGet();
            if (bm != null) {
                // Remove any tasks that may be getting the image because they may overwrite
                // anything we do.
                // Remember: the view may have been re-purposed and have a different associated
                // task which must be removed from the view and removed from the queue.
                GetImageTask.clearOldTaskFromView(imageView);
                boolean isSet = ImageUtils.setImageView(imageView, bm, maxWidth, maxHeight, true);
                cacheTicks.addAndGet(System.nanoTime() - tick);
                return isSet;
            }
            // make sure that the next step does not check again.
            cacheWasChecked = true;
        }

        // 2. The image is not in the cache but the original exists, queue a task if allowed.
        if (BooklistBuilder.imagesAreGeneratedInBackground(context)) {
            // use place holder to indicate an image is coming
            imageView.setImageResource(R.drawable.ic_image);
            // go get it
            new GetImageTask(uuid, imageView, maxWidth, maxHeight, cacheWasChecked)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            // we don't know for sure, let's be optimistic.
            return true;
        }

        tick = System.nanoTime();

        // 3. Check if the file exists; if it does not set 'ic_broken_image' icon and exit.
        File file = StorageUtils.getCoverFile(uuid);
        if (!file.exists()) {
            // no image
            imageView.setImageResource(R.drawable.ic_broken_image);
            return false;
        }

        // 4. go get it from the file system.
        // first display
        boolean isSet = setImageView(imageView, file, maxWidth, maxHeight, true);
        fileTicks.addAndGet(System.nanoTime() - tick);
        fileChecks.incrementAndGet();
        // now send to cache if needed/allowed.
        if (isSet && BooklistBuilder.imagesAreCached(imageView.getContext())) {
            saveToCache(imageView, uuid, maxWidth, maxHeight);
        }
        return isSet;
    }

    /**
     * Load the image file into the destination view.
     * Scaling is done by Android, enforced by the view itself and the dimensions passed in.
     *
     * @param destView  The ImageView to load with the file or an appropriate icon
     * @param file      The file of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView destView,
                                    @NonNull final File file,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean upscale) {

        // Get the file, if it exists. Otherwise set 'ic_broken_image' icon and exit.
        if (!file.exists()) {
            destView.setImageResource(R.drawable.ic_broken_image);
            return false;
        }

        Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
        return setImageView(destView, bm, maxWidth, maxHeight, upscale);
    }

    /**
     * Load the image bitmap into the destination view.
     * Scaling is done by Android, enforced by the view itself and the dimensions passed in.
     *
     * @param destView  The ImageView to load with the bitmap or an appropriate icon
     * @param bm        The Bitmap of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    private static boolean setImageView(@NonNull final ImageView destView,
                                     @Nullable final Bitmap bm,
                                     final int maxWidth,
                                     final int maxHeight,
                                     final boolean upscale) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.debug(ImageUtils.class, "setImageView",
                         "maxWidth=" + maxWidth,
                         "maxHeight=" + maxHeight,
                         "upscale=" + upscale,
                         bm != null ? "bm.width=" + bm.getWidth() : "no bm",
                         bm != null ? "bm.height=" + bm.getHeight() : "no bm");
        }

        destView.setMaxWidth(maxWidth);
        destView.setMaxHeight(maxHeight);

        if (bm == null) {
            // no bitmap
            destView.setImageResource(R.drawable.ic_broken_image);
            return false;
        } else {
            // upscale only when needed.
            if (bm.getHeight() < maxHeight && upscale) {
                Bitmap scaledBitmap;
//                scaledBitmap = Bitmap.createScaledBitmap(bm, maxWidth, maxHeight, true);
                scaledBitmap = createScaledBitmap(bm, maxWidth, maxHeight);
                if (!bm.equals(scaledBitmap)) {
                    // clean up the old one right now to save memory.
                    bm.recycle();
                    destView.setImageBitmap(scaledBitmap);
                    return true;
                }
            }
            // if not upscaling, let Android decide on any other scaling as needed.
            destView.setImageBitmap(bm);
            return true;
        }
    }

    @NonNull
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final File file,
                                            final int scale) {
        int maxSize = ImageUtils.getMaxImageSize(scale);
        return createScaledBitmap(BitmapFactory.decodeFile(file.getPath()), maxSize, maxSize);
    }

    /**
     * Custom version of {@link Bitmap#createScaledBitmap}.
     * <p>
     * The ratio correction was taken from the original BC code,
     * but the file-decode scaling logic removed.
     * <p>
     * Creates a new bitmap, scaled from an existing bitmap, when possible. If the
     * specified width and height are the same as the current width and height of
     * the source bitmap, the source bitmap is returned and no new bitmap is
     * created.
     *
     * @param source       The source bitmap.
     * @param dstWidth  The new bitmap's desired width.
     * @param dstHeight The new bitmap's desired height.
     *
     * @return The new scaled bitmap or the source bitmap if no scaling is required.
     *
     * @throws IllegalArgumentException if width is <= 0, or height is <= 0
     */
    @NonNull
    @AnyThread
    private static Bitmap createScaledBitmap(@NonNull final Bitmap source,
                                             final int dstWidth,
                                             final int dstHeight) {
        Matrix matrix = new Matrix();

        final int width = source.getWidth();
        final int height = source.getHeight();
        if (width != dstWidth || height != dstHeight) {
            final float sx = (float) dstWidth / width;
            final float sy = (float) dstHeight / height;
            // Next line from original method: using this still causes distortion,
            // matrix.setScale(sx, sy);
            // instead work out the ratio so that it fits exactly
            float ratio = (sx < sy) ? sx : sy;
            matrix.setScale(ratio, ratio);
        }
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true);
    }

    /**
     * Get the image from the file specification.
     * This is the original code BC code, using BitmapFactory.Options + File
     * TEST: which one is better/faster ? original or simplified ?
     * <p>
     * TODO: createScaledBitmap is an expensive operation. Make sure you really need it.
     *
     * @param fileSpec  the file specification (NOT the uuid!)
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param exact     if true, the image will be proportionally scaled to fit box.
     *
     * @return The bitmap, or {@code null} on failure
     */
    @Nullable
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final String fileSpec,
                                            final int maxWidth,
                                            final int maxHeight,
                                            final boolean exact) {

        // Read the file to get the bitmap size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume file bad.
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to SCALE the file to fit in required box
        final float widthRatio = (float) maxWidth / opt.outWidth;
        final float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out SCALE so that it fits exactly
        float ratio = (widthRatio < heightRatio) ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        final int idealSampleSize = (int) Math.ceil(1 / ratio);
        // Get the nearest *bigger* power of 2.
        final int samplePow2 =
                (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.debug(ImageUtils.class, "createScaledBitmap",
                         "filename = " + fileSpec,
                         "exact=" + exact,
                         "maxWidth=" + maxWidth,
                         "opt.outWidth=" + opt.outWidth,
                         "widthRatio=" + widthRatio,
                         "maxHeight=" + maxHeight,
                         "opt.outHeight=" + opt.outHeight,
                         "heightRatio=" + heightRatio,
                         "ratio=" + ratio,
                         "idealSampleSize =" + idealSampleSize,
                         "samplePow2=" + samplePow2);
        }

        final Bitmap bm;
        try {
            if (exact) {
                // Create one bigger than needed and SCALE it;
                // this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC().
                    // Otherwise, covers in cover browser will stay blank.
                    Logger.warn(ImageUtils.class, "createScaledBitmap",
                                "Unexpectedly failed to decode bitmap; memory exhausted?");
                    return null;
                }

                final Matrix matrix = new Matrix();
                // Fix ratio based on new sample size and SCALE it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (!bm.equals(tmpBm)) {
                    // clean up the old one right now to save memory.
                    tmpBm.recycle();
                }
            } else {
                // Use a SCALE that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(fileSpec, opt);
            }
        } catch (@NonNull final OutOfMemoryError e) {
            Logger.error(ImageUtils.class, e);
            return null;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.debug(ImageUtils.class, "createScaledBitmap",
                         "bm.width=" + bm.getWidth(),
                         "bm.height=" + bm.getHeight());
        }

        return bm;
    }

    /**
     * Given a URL, get an image and save to a file.
     * <p>
     * ENHANCE: unify the naming elements of the file.
     *
     * @param url    Image file URL
     * @param name   for the file.
     * @param suffix optional suffix
     *
     * @return Downloaded fileSpec, or {@code null} on failure
     */
    @Nullable
    @WorkerThread
    public static String saveImage(@NonNull final String url,
                                   @NonNull final String name,
                                   @Nullable final String suffix) {

        final File file = StorageUtils.getTempCoverFile(name, suffix);

        boolean success = false;
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            success = StorageUtils.saveInputStreamToFile(con.inputStream, file);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(ImageUtils.class, e);
            }
        }

        return success ? file.getAbsolutePath() : null;
    }

    private static void saveToCache(@NonNull final ImageView imageView,
                                    @NonNull final String uuid,
                                    final int maxWidth,
                                    final int maxHeight) {
        // during displaying, the bitmap could have been up or downscaled.
        // so we extract the currently displayed one to send to the cache.
        Drawable drawable = imageView.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            // Queue the image to be written to the cache.
            new CoversDAO.ImageCacheWriterTask(uuid, maxWidth, maxHeight, bitmap)
                    .execute();
        }
    }

    /**
     * Given a URL, get an image and return as a byte array.
     *
     * @param url Image file URL
     *
     * @return Downloaded {@code byte[]} or {@code null} upon failure
     */
    @Nullable
    @WorkerThread
    public static byte[] getBytes(@NonNull final String url) {
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // Save the output to a byte output stream
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                //noinspection ConstantConditions
                while ((len = con.inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            }
        } catch (@NonNull final IOException e) {
            Logger.error(ImageUtils.class, e);
        }
        return null;
    }

    /**
     * Given byte array that represents an image (jpg, png etc), return as a bitmap.
     *
     * @param bytes Raw byte data
     *
     * @return bitmap
     */
    @Nullable
    @AnyThread
    public static Bitmap getBitmap(@NonNull final byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                                                      new BitmapFactory.Options());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.debug(ImageUtils.class, "getBitmap",
                         "Array " + bytes.length + " bytes",
                         "bitmap " + bitmap.getHeight() + 'x' + bitmap.getWidth());
        }
        return bitmap;
    }

    /**
     * Read {@link UniqueId#BKEY_FILE_SPEC_ARRAY}.
     * If there are images, pick the largest one, rename it, and delete the others.
     * Finally, remove the key and set {@link UniqueId#BKEY_IMAGE} to true
     */
    @AnyThread
    public static void cleanupImages(@Nullable final Bundle /* in/out */ bookData) {
        if (bookData == null) {
            return;
        }

        ArrayList<String> imageList = bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
        if (imageList == null || imageList.isEmpty()) {
            return;
        }

        cleanupImages(imageList);

        // Finally, cleanup the data
        bookData.remove(UniqueId.BKEY_FILE_SPEC_ARRAY);
        // and indicate we got a file with the default name
        bookData.putBoolean(UniqueId.BKEY_IMAGE, true);
    }

    /**
     * If there are images, pick the largest one, rename it, and delete the others.
     *
     * @param imageList a list of images
     */
    @AnyThread
    private static void cleanupImages(@NonNull final ArrayList<String> imageList) {

        long bestFileSize = -1;
        int bestFileIndex = -1;

        // Just read the image files to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    long size = opt.outHeight * opt.outWidth;
                    if (size > bestFileSize) {
                        bestFileSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one. Note there *may* be no best one,
        // so all would be deleted. We do this first in case the list
        // contains a file with the same name as the target of our rename.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                StorageUtils.deleteFile(new File(imageList.get(i)));
            }
        }
        // Get the best file (if present) and rename it.
        if (bestFileIndex >= 0) {
            File source = new File(imageList.get(bestFileIndex));
            File destination = StorageUtils.getTempCoverFile();
            StorageUtils.renameFile(source, destination);
        }
    }

    /**
     * Task to get a thumbnail from the file system or covers database.
     * It will resize it as required and apply the resulting Bitmap to the related view.
     * <p>
     * We now use standard AsyncTask but run it on the parallel executor.
     */
    private static class GetImageTask
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

        private Exception mException;

        long mTick;

        /**
         * Constructor. Clean the view and save the details of what we want.
         * <p>
         * Create a task to convert, set and store the image for the passed book.
         * If cacheWasChecked = false, then the cache will be checked before any work is
         * done, and if found in the cache it will be used. This option is included to
         * reduce contention between background and foreground tasks: the foreground (UI)
         * thread checks the cache only if there are no background cache-related tasks
         * currently running.
         */
        @UiThread
        private GetImageTask(@NonNull final String uuid,
                             @NonNull final ImageView imageView,
                             final int width,
                             final int height,
                             final boolean cacheWasChecked) {

            mTick = System.nanoTime();

            clearOldTaskFromView(imageView);
            mView = new WeakReference<>(imageView);
            mCacheWasChecked = cacheWasChecked;

            mUuid = uuid;
            mWidth = width;
            mHeight = height;

            // Clear current image
            imageView.setImageBitmap(null);

            // Associate the view with this task
            imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, this);
        }

        static boolean hasActiveTasks() {
            return RUNNING_TASKS.get() != 0;
        }

        /**
         * Remove any record of a prior thumbnail task from a View object to ensure that nothing
         * overwrites the view.
         */
        static void clearOldTaskFromView(@NonNull final ImageView imageView) {
            GetImageTask oldTask = (GetImageTask) imageView.getTag(R.id.TAG_GET_THUMBNAIL_TASK);
            if (oldTask != null) {
                imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);
                oldTask.cancel(true);
            }
        }

        @Override
        @Nullable
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("GetImageTask");

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
                    mBitmap = CoversDAO.getImage(mUuid, mWidth, mHeight);
                    mWasInCache = mBitmap != null;
                }

                // Make sure the view is still ... bla bla as above
                if (isCancelled() || !this.equals(view.getTag(R.id.TAG_GET_THUMBNAIL_TASK))) {
                    return null;
                }

                // wasn't in cache, try file system.
                if (mBitmap == null) {
                    String fileSpec = StorageUtils.getCoverFile(mUuid).getPath();
                    mBitmap = BitmapFactory.decodeFile(fileSpec, null);
                }

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
                Logger.error(this, e);
                mException = e;
            }
            return null;
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Void result) {

            if (mException != null) {
                return;
            }

            // Get the view we are targeting and make sure it is valid
            ImageView imageView = mView.get();

            // Make sure the view is still ... bla bla as above
            boolean viewIsValid = imageView != null
                                  && this.equals(imageView.getTag(R.id.TAG_GET_THUMBNAIL_TASK));

            // we're done; clear the view tag
            if (viewIsValid) {
                imageView.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);
            }

            if (mBitmap != null) {
                if (viewIsValid) {
                    // first display
                    setImageView(imageView, mBitmap, mWidth, mHeight, true);
                    taskTicks.addAndGet(System.nanoTime() - mTick);
                    taskChecks.incrementAndGet();
                    // now send to cache if needed/allowed.
                    if (!mWasInCache && BooklistBuilder.imagesAreCached(imageView.getContext())) {
                        saveToCache(imageView, mUuid, mWidth, mHeight);
                    }
                }
            } else {
                if (viewIsValid) {
                    imageView.setImageResource(R.drawable.ic_broken_image);
                }
            }
        }

        @Override
        @UiThread
        protected void onCancelled(final Void result) {
            cleanup();
        }

        @AnyThread
        private void cleanup() {
            RUNNING_TASKS.decrementAndGet();
        }
    }
}
