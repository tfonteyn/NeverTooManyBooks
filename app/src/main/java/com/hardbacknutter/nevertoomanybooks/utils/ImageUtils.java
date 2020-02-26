/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

// collapse all lines, restart app
// scroll to Pratchett (175 books) on 1st line, expand, scroll to end.
//////////////////////////////////////////////////////////////////////////// test 1
// no cache
// cacheWrites=175|cacheWrites=2065
// cacheWrites=175|cacheWriteTicks=2094
//
// from cache
// cacheReads=175|cacheReadTicks=2305
//
// ==> without rescaling code (leaving it to Android) ==> no point in using a cache.
////////////////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////////////// test 2
// writing to cache with REAL scaling
// cacheWrites=175|cacheWriteTicks=2678

// using cache
// cacheReads=175|cacheReadTicks=1585
//
// ==> so during the writing, its slower.... but afterwards, 50% faster compared to test 1
////////////////////////////////////////////////////////////////////////////

public final class ImageUtils {

    /**
     * The minimum size an image file must be to be considered valid.
     * 300: based on LibraryThing 1x1 pixel placeholder being 178 bytes in download
     * (43 bytes after compression on disk).
     */
    public static final int MIN_IMAGE_FILE_SIZE = 300;
    /** Thumbnail Scaling. */
    public static final int SCALE_NOT_DISPLAYED = 0;

    /*
     * Scaling of thumbnails.
     * Must be kept in sync with res/values/strings-preferences.xml#pv_cover_scale_factor
     *
     * res/xml/preferences_styles.xml contains the default set to SCALE_MEDIUM
     */
    /** Thumbnail Scaling. */
    public static final int SCALE_X_SMALL = 1;
    /** Thumbnail Scaling. */
    public static final int SCALE_SMALL = 2;
    /** Thumbnail Scaling. */
    public static final int SCALE_MEDIUM = 3;
    /** Thumbnail Scaling. */
    public static final int SCALE_LARGE = 4;
    /** Thumbnail Scaling. */
    public static final int SCALE_X_LARGE = 5;
    /** Thumbnail Scaling. */
    public static final int SCALE_2X_LARGE = 6;

    /** scaling factor for each SCALE_* option. */
    private static final int[] SCALE_FACTOR = {0, 1, 2, 3, 5, 8, 12};
    /** Log tag. */
    private static final String TAG = "ImageUtils";
    private static final int BUFFER_SIZE = 32768;
    /** network: if at first we don't succeed... */
    private static final int NR_OF_TRIES = 2;
    /** network: milliseconds to wait between retries. */
    private static final int RETRY_AFTER_MS = 500;
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    private ImageUtils() {
    }

    /**
     * Get the maximum pixel size an image should be based on the desired scale factor.
     *
     * @param context Current context
     * @param scale   to apply
     *
     * @return amount in pixels
     */
    public static int getMaxImageSize(@NonNull final Context context,
                                      @Scale final int scale) {
        return SCALE_FACTOR[scale]
               * (int) context.getResources().getDimension(R.dimen.cover_base_size);
    }

    /**
     * Check if caching is enabled.
     *
     * @param context Current context
     *
     * @return {@code true} if resized images are cached in a database.
     */
    public static boolean imagesAreCached(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_image_cache_resized, false);
    }

    /**
     * Set a placeholder drawable in the view.
     *
     * @param imageView  The ImageView to load with the placeholder
     * @param drawable   drawable to use
     * @param background drawable to use for a placeholder background (0 for none)
     * @param maxHeight  Maximum height of the image
     */
    @UiThread
    public static void setPlaceholder(@NonNull final ImageView imageView,
                                      @DrawableRes final int drawable,
                                      @DrawableRes final int background,
                                      final int maxHeight) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = maxHeight;
        lp.width = (int) (maxHeight * 0.75f);
        imageView.setLayoutParams(lp);

        imageView.setImageResource(drawable);
        if (background != 0) {
            imageView.setBackgroundResource(background);
        }
    }

    /**
     * Load the image bitmap into the destination view.
     *
     * @param imageView      The ImageView to load with the bitmap or an appropriate icon
     * @param source         The Bitmap of the image
     * @param maxWidth       Maximum width of the image
     * @param maxHeight      Maximum height of the image
     * @param allowUpscaling use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    public static void setImageView(@NonNull final ImageView imageView,
                                    @NonNull final Bitmap source,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean allowUpscaling) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Log.d(TAG, "setImageView"
                       + "|maxWidth=" + maxWidth
                       + "|maxHeight=" + maxHeight
                       + "|allowUpscaling=" + allowUpscaling
                       + "|bm.width=" + source.getWidth()
                       + "|bm.height=" + source.getHeight());
        }

        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        imageView.setLayoutParams(lp);

        imageView.setMaxWidth(maxWidth);
        imageView.setMaxHeight(maxHeight);

        Bitmap bitmap = source;
        // upscale only when required and allowed; otherwise let Android decide.
        if (source.getHeight() < maxHeight && allowUpscaling) {
            bitmap = createScaledBitmap(source, maxWidth, maxHeight);
        }

        imageView.setImageBitmap(bitmap);
    }

    /**
     * Load the image owned by the UUID/cIdx into the destination ImageView.
     * Handles checking & storing in the cache.
     * <p>
     * Images and placeholder will always be scaled to a fixed size.
     *
     * @param imageView View to populate
     * @param uuid      UUID of the book
     * @param cIdx      0..n image index
     * @param maxWidth  Maximum width of resulting image
     * @param maxHeight Maximum height of resulting image
     *
     * @return {@code true} if the image was displayed. {@code false} if a place holder was used.
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView imageView,
                                       @NonNull final String uuid,
                                       final int cIdx,
                                       final int maxWidth,
                                       final int maxHeight) {

        Context context = imageView.getContext();

        // 1. If caching is used, and we don't have cache building happening, check it.
        if (imagesAreCached(context) && !CoversDAO.ImageCacheWriterTask.hasActiveTasks()) {
            Bitmap bitmap = CoversDAO.getImage(context, uuid, cIdx, maxWidth, maxHeight);
            if (bitmap != null) {
                setImageView(imageView, bitmap, maxWidth, maxHeight, true);
                return true;
            }
        }

        // 2. Cache did not have it, or we were not allowed to check.
        // Check if the file exists; if it does not, set the placeholder icon and exit.
        File file = StorageUtils.getCoverFileForUuid(context, uuid, cIdx);
        if (file.length() < MIN_IMAGE_FILE_SIZE) {
            setPlaceholder(imageView, R.drawable.ic_image, 0, maxHeight);
            return false;
        }

        // Once we get here, we know the file is valid

        // 3. If caching is used, go get the image from the file system AND send it to the cache.
        if (imagesAreCached(context)) {
            new CacheLoader(imageView, uuid, cIdx, file, maxWidth, maxHeight)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;

        } else {
            // Cache not used, we know the file is valid, go get it.
            new ImageLoader(imageView, file, maxWidth, maxHeight, true)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;
        }
    }

    /**
     * Convenience method for {@link #createScaledBitmap(Bitmap, int, int)}.
     *
     * @param context Current context
     * @param file    The file of the image
     * @param scale   user preferred scale factor
     *
     * @return the bitmap, or {@code null} if the file failed to decode.
     */
    @Nullable
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final Context context,
                                            @NonNull final File file,
                                            @Scale final int scale) {
        @Nullable
        Bitmap bm = BitmapFactory.decodeFile(file.getPath());
        if (bm == null) {
            return null;
        }
        int maxSize = getMaxImageSize(context, scale);
        return createScaledBitmap(bm, maxSize, maxSize);
    }

    /**
     * Custom version of {@link Bitmap#createScaledBitmap}.
     * <p>
     * The ratio correction was taken from the original BC code, see {@link #forceScaleBitmap},
     * but the file-decode scaling logic removed.
     * <p>
     * Creates a new bitmap, scaled from an existing bitmap, when possible. If the
     * specified width and height are the same as the current width and height of
     * the source bitmap, the source bitmap is returned and no new bitmap is
     * created.
     *
     * @param source    The source bitmap.
     * @param dstWidth  The new bitmap's desired width.
     * @param dstHeight The new bitmap's desired height.
     *
     * @return The new scaled bitmap or the source bitmap if no scaling was done.
     *
     * @throws IllegalArgumentException if width is <= 0, or height is <= 0
     */
    @NonNull
    @AnyThread
    private static Bitmap createScaledBitmap(@NonNull final Bitmap source,
                                             final int dstWidth,
                                             final int dstHeight) {
        Matrix matrix = new Matrix();
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != dstWidth || height != dstHeight) {
            float sx = (float) dstWidth / width;
            float sy = (float) dstHeight / height;
            // Next line from original method: using this still causes distortion,
            // matrix.setScale(sx, sy);
            // instead work out the ratio so that it fits exactly
            float ratio = Math.min(sx, sy);
            matrix.setScale(ratio, ratio);
        }
        Bitmap scaledBitmap = Bitmap.createBitmap(source, 0, 0, width, height, matrix, true);
        if (!source.equals(scaledBitmap)) {
            // clean up the old one right now to save memory.
            source.recycle();
        }
        return scaledBitmap;
    }

    /**
     * Get the image from the file specification.
     * <p>
     * <strong>Note:</strong> forceScaleBitmap is an expensive operation.
     * Make sure you really need it.
     * This method is slower than {@link #createScaledBitmap} but produces a truly scaled
     * bitmap to fit withing the bounds passed in.
     *
     * @param fileSpec  the file specification (NOT the uuid!)
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param exact     if true, the image will be proportionally scaled to fit box.
     *
     * @return The bitmap, or {@code null} on failure
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    @AnyThread
    public static Bitmap forceScaleBitmap(@NonNull final String fileSpec,
                                          final int maxWidth,
                                          final int maxHeight,
                                          final boolean exact) {

        // Read the file to get the bitmap size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume the file is bad.
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to SCALE the file to fit in required box
        float widthRatio = (float) maxWidth / opt.outWidth;
        float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out SCALE so that it fits exactly
        float ratio = Math.min(widthRatio, heightRatio);

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        int idealSampleSize = (int) Math.ceil(1 / ratio);
        // Get the nearest *bigger* power of 2.
        int samplePow2 = (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Log.d(TAG, "createScaledBitmap"
                       + "|filename = " + fileSpec
                       + "|exact=" + exact
                       + "|maxWidth=" + maxWidth
                       + "|opt.outWidth=" + opt.outWidth
                       + "|widthRatio=" + widthRatio
                       + "|maxHeight=" + maxHeight
                       + "|opt.outHeight=" + opt.outHeight
                       + "|heightRatio=" + heightRatio
                       + "|ratio=" + ratio
                       + "|idealSampleSize =" + idealSampleSize
                       + "|samplePow2=" + samplePow2);
        }

        @Nullable
        Bitmap bm;
        try {
            if (exact) {
                // Create one bigger than needed and SCALE it;
                // this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                @Nullable
                Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    return null;
                }

                Matrix matrix = new Matrix();
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
            Logger.error(TAG, e);
            return null;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Log.d(TAG, "createScaledBitmap"
                       + "|bm.width=" + bm.getWidth()
                       + "|bm.height=" + bm.getHeight());
        }

        return bm;
    }

    /**
     * Given a URL, get an image and save to a file. Called/run in a background task.
     *
     * @param context Application context
     * @param url     Image file URL
     * @param name    for the file.
     *
     * @return Downloaded fileSpec, or {@code null} on failure
     */
    @Nullable
    @WorkerThread
    public static String saveImage(@NonNull final Context context,
                                   @NonNull final String url,
                                   @NonNull final String name) {

        File file = StorageUtils.getTempCoverFile(context, name);

        if (url.startsWith(DATA_IMAGE_JPEG_BASE_64)) {
            byte[] image = Base64.decode(url.substring(DATA_IMAGE_JPEG_BASE_64.length())
                                            .getBytes(StandardCharsets.UTF_8), 0);
            try (OutputStream os = new FileOutputStream(file)) {
                os.write(image);
                return file.getAbsolutePath();

            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "saveImage"
                               + "|base64"
                               + "|e=" + e.getLocalizedMessage());
                }
            }
        } else {
            // If the site drops connection, we retry once.
            int retry = NR_OF_TRIES;
            while (retry > 0) {
                try (TerminatorConnection con = TerminatorConnection.open(context, url)) {
                    file = StorageUtils.saveInputStreamToFile(context, con.getInputStream(), file);
                    return file != null ? file.getAbsolutePath() : null;

                } catch (@NonNull final IOException e) {
                    retry--;

                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "saveImage"
                                   + "|url=\"" + url + '\"'
                                   + "|will retry=" + (retry > 0)
                                   + "|e=" + e.getLocalizedMessage());
                    }
                    try {
                        Thread.sleep(RETRY_AFTER_MS);
                    } catch (@NonNull final InterruptedException ignore) {
                    }
                }
            }
        }
        return null;
    }

    /**
     * Given a URL, get an image and return as a byte array. Called/run in a background task.
     *
     * @param context Application context
     * @param url     Image file URL
     *
     * @return Downloaded {@code byte[]} or {@code null} upon failure
     */
    @Nullable
    @WorkerThread
    public static byte[] getBytes(@NonNull final Context context,
                                  @NonNull final String url) {

        // If the site drops connection, we retry once.
        int retry = NR_OF_TRIES;

        while (retry > 0) {
            try (TerminatorConnection con = TerminatorConnection.open(context, url);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // Save the output to a byte output stream
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                InputStream is = con.getInputStream();
                if (is == null) {
                    return null;
                }
                while ((len = is.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            } catch (@NonNull final IOException e) {
                retry--;

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "getBytes"
                               + "|e=" + e.getLocalizedMessage()
                               + "|will retry=" + (retry > 0)
                               + "|url=\"" + url + '\"');
                }
                try {
                    Thread.sleep(RETRY_AFTER_MS);
                } catch (@NonNull final InterruptedException ignore) {
                }
            }
        }
        return null;
    }

    /**
     * If there are images, pick the largest one, and delete the others.
     *
     * @param imageList a list of images
     *
     * @return name of cover found, or {@code null} for none.
     */
    @AnyThread
    @Nullable
    public static String cleanupImages(@NonNull final ArrayList<String> imageList) {

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
        // so all would be deleted.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                StorageUtils.deleteFile(new File(imageList.get(i)));
            }
        }

        if (bestFileIndex >= 0) {
            return imageList.get(bestFileIndex);
        }

        return null;
    }

    /**
     * Rotate the image. Reads from the passed file, and writes the result back to it.
     *
     * @param context Current context
     * @param file    to read/write
     * @param angle   rotate by the specified amount
     *
     * @return {@code true} on success
     */
    public static boolean rotate(@NonNull final Context context,
                                 @NonNull final File file,
                                 final long angle) {
        if (!file.exists()) {
            return false;
        }

        // sanity check
        if (angle == 0) {
            return true;
        }

        // We load the file and first scale it up.
        // Keep in mind this means it could be up- or downscaled from the original !
        int maxSize = ImageUtils.getMaxImageSize(context, SCALE_2X_LARGE);

        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                Bitmap bm = forceScaleBitmap(file.getPath(), maxSize, maxSize, true);
                if (bm == null) {
                    return false;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0,
                                                           bm.getWidth(), bm.getHeight(),
                                                           matrix, true);
                if (rotatedBitmap != bm) {
                    // clean up the old one right now to save memory.
                    bm.recycle();
                    // Write back to the file
                    try (OutputStream os = new FileOutputStream(file.getAbsoluteFile())) {
                        rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    }
                    return true;
                }

                return false;

            } catch (@NonNull final OutOfMemoryError e) {
                attempts--;
                if (attempts > 1) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "", e);
                }
                return false;
            }
        }
    }

    /**
     * Rotate the image. If successful, the input bitmap will be recycled.
     * Otherwise we simply return the input bitmap as-is.
     *
     * @param bm    to rotate
     * @param angle rotate by the specified amount
     *
     * @return the rotated bitmap.
     */
    static Bitmap rotate(@NonNull final Bitmap bm,
                         final int angle) {
        // sanity check
        if (angle == 0) {
            return bm;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0,
                                                   bm.getWidth(), bm.getHeight(),
                                                   matrix, true);
        if (rotatedBitmap != bm) {
            bm.recycle();
            return rotatedBitmap;
        } else {
            return bm;
        }

    }

    @IntDef({SCALE_NOT_DISPLAYED, SCALE_X_SMALL, SCALE_SMALL, SCALE_MEDIUM,
             SCALE_LARGE, SCALE_X_LARGE, SCALE_2X_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scale {

    }


    /**
     * Load a Bitmap from a file, and populate the view.
     */
    public static class ImageLoader
            extends AsyncTask<Void, Void, Bitmap> {

        @NonNull
        private final WeakReference<ImageView> mImageView;
        @NonNull
        private final File mFile;
        private final int mMaxWidth;
        private final int mMaxHeight;
        private final boolean mAllowUpscaling;

        /**
         * Constructor.
         *
         * @param imageView      to populate
         * @param file           to load, must be valid
         * @param maxWidth       Maximum desired width of the image
         * @param maxHeight      Maximum desired height of the image
         * @param allowUpscaling use the maximum h/w also as the minimum; thereby forcing upscaling.
         */
        public ImageLoader(@NonNull final ImageView imageView,
                           @NonNull final File file,
                           final int maxWidth,
                           final int maxHeight,
                           final boolean allowUpscaling) {
            // see onPostExecute
            imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);

            mImageView = new WeakReference<>(imageView);
            mFile = file;
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
            mAllowUpscaling = allowUpscaling;
        }

        @Override
        @Nullable
        protected Bitmap doInBackground(final Void... voids) {
            Bitmap bitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath());
            // upscale when required and allowed
            if (bitmap != null && bitmap.getHeight() < mMaxHeight && mAllowUpscaling) {
                bitmap = createScaledBitmap(bitmap, mMaxWidth, mMaxHeight);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(@Nullable final Bitmap bitmap) {
            ImageView imageView = mImageView.get();

            if (imageView != null
                // are we still associated with this view ? (remember: views are recycled)
                && this.equals(imageView.getTag(R.id.TAG_THUMBNAIL_TASK))) {
                imageView.setTag(R.id.TAG_THUMBNAIL_TASK, null);
                if (bitmap != null) {
                    // upscaling, if applicable, was done in the background task.
                    setImageView(imageView, bitmap, mMaxWidth, mMaxHeight, false);
                } else {
                    setPlaceholder(imageView, R.drawable.ic_broken_image, 0, mMaxHeight);
                }
            }
        }
    }

    /**
     * Load a Bitmap from a file, and populate the view.
     */
    private static class CacheLoader
            extends AsyncTask<Void, Void, Bitmap> {

        @NonNull
        private final WeakReference<ImageView> mImageView;
        private final String mUuid;
        private final int mCIdx;
        @NonNull
        private final File mFile;
        private final int mMaxWidth;
        private final int mMaxHeight;

        /**
         * @param imageView to populate
         * @param uuid      UUID of the book
         * @param cIdx      0..n image index
         * @param file      to load, must be valid
         * @param maxWidth  Maximum desired width of the image
         * @param maxHeight Maximum desired height of the image
         */
        CacheLoader(@NonNull final ImageView imageView,
                    final String uuid,
                    final int cIdx,
                    @NonNull final File file,
                    final int maxWidth,
                    final int maxHeight) {
            // see onPostExecute
            imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);

            mImageView = new WeakReference<>(imageView);
            mUuid = uuid;
            mCIdx = cIdx;
            mFile = file;
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
        }

        @Override
        @Nullable
        protected Bitmap doInBackground(final Void... voids) {
            return forceScaleBitmap(mFile.getAbsolutePath(), mMaxWidth, mMaxHeight, true);
        }

        @Override
        protected void onPostExecute(@Nullable final Bitmap bitmap) {
            ImageView imageView = mImageView.get();

            if (imageView != null
                // are we still associated with this view ? (remember: views are recycled)
                && this.equals(imageView.getTag(R.id.TAG_THUMBNAIL_TASK))) {
                imageView.setTag(R.id.TAG_THUMBNAIL_TASK, null);
                if (bitmap != null) {
                    // display it, upscaling, if applicable, was done in the background task.
                    setImageView(imageView, bitmap, mMaxWidth, mMaxHeight, false);
                    // and start another task to send it to the cache
                    new CoversDAO.ImageCacheWriterTask(mUuid, mCIdx, mMaxWidth, mMaxHeight, bitmap)
                            .execute();
                } else {
                    setPlaceholder(imageView, R.drawable.ic_broken_image, 0, mMaxHeight);
                }
            }
        }
    }
}
