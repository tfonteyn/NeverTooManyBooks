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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;


public final class ImageUtils {

    /** By default, covers will always be downsized to maximum 600 x 1000 pixels. */
    static final int MAX_IMAGE_WIDTH_PX = 600;
    static final int MAX_IMAGE_HEIGHT_PX = 1000;

    /** Log tag. */
    private static final String TAG = "ImageUtils";
    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;
    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 2048;
    /** network: if at first we don't succeed... */
    private static final int NR_OF_TRIES = 2;
    /** The prefix an embedded image url will have. */
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    private ImageUtils() {
    }

    /**
     * Set a placeholder drawable in the view.
     * The view will be resized as a portrait image with 'maxHeight' as the height,
     * and the width set at 0.6 * maxHeight'.
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     *
     * @param imageView  View to populate
     * @param drawable   drawable to use
     * @param background (optional) drawable to use for the background; use {@code 0} for none
     * @param maxHeight  Maximum height of the ImageView
     */
    @UiThread
    static void setPlaceholder(@NonNull final ImageView imageView,
                               @DrawableRes final int drawable,
                               @SuppressWarnings("SameParameterValue")
                               @DrawableRes final int background,
                               final int maxHeight) {
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = maxHeight;
        lp.width = (int) (maxHeight * 0.6f);
        imageView.setLayoutParams(lp);

        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);

        if (background != 0) {
            imageView.setBackgroundResource(background);
        }
    }

    /**
     * Set a placeholder drawable in the view. Resize the view to wrap around the placeholder.
     *
     * @param imageView  View to populate
     * @param drawable   drawable to use
     * @param background (optional) drawable to use for the background; use {@code 0} for none
     */
    @UiThread
    public static void setPlaceholder(@NonNull final ImageView imageView,
                                      @DrawableRes final int drawable,
                                      @DrawableRes final int background) {
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        imageView.setLayoutParams(lp);

        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);

        if (background != 0) {
            imageView.setBackgroundResource(background);
        }
    }

    /**
     * Load the image bitmap into the destination view.
     * The image is scaled to fit the box exactly preserving the aspect ratio.
     *
     * @param imageView  View to populate
     * @param maxWidth   Maximum width of the ImageView
     * @param maxHeight  Maximum height of the ImageView
     * @param bitmap     The Bitmap of the image
     * @param background (optional) drawable to use for the background; use {@code 0} for none
     */
    @UiThread
    public static void setImageView(@NonNull final ImageView imageView,
                                    final int maxWidth,
                                    final int maxHeight,
                                    @NonNull final Bitmap bitmap,
                                    @DrawableRes final int background) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Log.d(TAG, "setImageView"
                       + "|maxWidth=" + maxWidth
                       + "|maxHeight=" + maxHeight
                       + "|bm.width=" + bitmap.getWidth()
                       + "|bm.height=" + bitmap.getHeight());
        }

        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (bitmap.getWidth() < bitmap.getHeight()) {
            // image is portrait; limit the height
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = maxHeight;
        } else {
            // image is landscape; limit the width
            lp.width = maxWidth;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        imageView.setLayoutParams(lp);

        // padding MUST be 0dp to allow scaling ratio to work properly
        imageView.setPadding(0, 0, 0, 0);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(bitmap);

        if (background != 0) {
            imageView.setBackgroundResource(background);
        }
    }

    /**
     * Load the image owned by the UUID/cIdx into the destination ImageView.
     * Handles checking & storing in the cache.
     * <p>
     * Images and placeholder will always be scaled to a fixed size.
     *
     * @param imageView View to populate
     * @param maxWidth  Maximum width of the ImageView
     * @param maxHeight Maximum height of the ImageView
     * @param uuid      UUID of the book
     * @param cIdx      0..n image index
     *
     * @return {@code true} if the image was displayed. {@code false} if a place holder was used.
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView imageView,
                                       final int maxWidth,
                                       final int maxHeight,
                                       @NonNull final String uuid,
                                       @IntRange(from = 0) final int cIdx) {

        final Context context = imageView.getContext();
        final boolean useCaching = imagesAreCached(context);

        // 1. If caching is used, and we don't have cache building happening, check it.
        if (useCaching && !CoversDAO.ImageCacheWriterTask.hasActiveTasks()) {
            final Bitmap bitmap = CoversDAO.getImage(context, uuid, cIdx, maxWidth, maxHeight);
            if (bitmap != null) {
                setImageView(imageView, maxWidth, maxHeight, bitmap, 0);
                return true;
            }
        }

        // 2. Cache did not have it, or we were not allowed to check.
        // Check if the file exists; if it does not, set the placeholder icon and exit.
        final File file = AppDir.getCoverFile(context, uuid, cIdx);
        if (!isFileGood(file)) {
            setPlaceholder(imageView, R.drawable.ic_image, 0, maxHeight);
            return false;
        }

        // Once we get here, we know the file is valid
        if (useCaching) {
            // 1. Gets the image from the file system and display it.
            // 2. Start a subsequent task to send it to the cache.
            // This 2nd task uses the serial executor.
            new ImageLoaderWithCacheWrite(imageView, file, maxWidth, maxHeight, null, uuid, cIdx)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;

        } else {
            // Cache not used: Get the image from the file system and display it.
            new ImageLoader(imageView, file, maxWidth, maxHeight, null)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;
        }
    }

    /**
     * Check if a file is an image with an acceptable size.
     *
     * <strong>If the file is not acceptable, then it will be deleted.</strong>
     *
     * @param srcFile to check
     *
     * @return {@code true} if file is acceptable.
     */
    @AnyThread
    public static boolean isFileGood(@Nullable final File srcFile) {
        // light weight test first
        if (srcFile == null || !srcFile.exists() || srcFile.length() < MIN_VALID_IMAGE_FILE_SIZE) {
            return false;
        }

        // Read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), opt);
        // minimal size required
        final boolean isGood = opt.outHeight >= MIN_VALID_IMAGE_SIDE
                               && opt.outWidth >= MIN_VALID_IMAGE_SIDE;

        // cleanup bad files.
        if (!isGood) {
            FileUtils.delete(srcFile);
        }
        return isGood;
    }

    /**
     * Decode the image from the given file and scale to fit the given bounds,
     * while preserving the aspect ratio.
     * The file is not altered.
     * <p>
     * The image is certain to fill the box, with its exact dimensions
     * the smallest possible larger than the requested dimensions.
     * or i.o.w this is NOT an exact scaling!
     *
     * @param srcFile   the file to read from
     * @param reqWidth  Maximum desired width of the image
     * @param reqHeight Maximum desired height of the image
     *
     * @return The bitmap, or {@code null} on any failure
     */
    @Nullable
    @WorkerThread
    static Bitmap decodeFile(@Nullable final File srcFile,
                             final int reqWidth,
                             final int reqHeight) {
        if (srcFile == null || !srcFile.exists()) {
            return null;
        }

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), options);

        // Abort if no size info, or to small to be any good.
        if (options.outHeight <= 0 || options.outWidth <= 0
            || (options.outHeight < MIN_VALID_IMAGE_SIDE
                && options.outWidth < MIN_VALID_IMAGE_SIDE)) {
            return null;
        }

        // Calculate the inSampleSize
        options.inSampleSize = 1;
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width LARGER than the requested height and width.
            while ((halfHeight / options.inSampleSize) >= reqHeight
                   && (halfWidth / options.inSampleSize) >= reqWidth) {
                options.inSampleSize *= 2;
            }
        }

        // Decode bitmap for real
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(srcFile.getAbsolutePath(), options);
    }

    /**
     * Save the given bitmap to the destination file using PNG format at 100% quality..
     *
     * @param bitmap  to save
     * @param dstFile to write to
     *
     * @return {@code true} for success
     */
    @WorkerThread
    static boolean saveBitmap(@NonNull final Bitmap bitmap,
                              @NonNull final File dstFile) {
        try {
            try (OutputStream os = new FileOutputStream(dstFile.getAbsoluteFile())) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            return true;

        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "", e);
            }
            return false;
        }
    }

    /**
     * Given a URL, get an image and save to a file. Called/run in a background task.
     *
     * @param context   Application context
     * @param url       Image file URL
     * @param name      for the file.
     * @param connectTimeout in milliseconds
     * @param throttler (optional) {@link Throttler} to use
     *
     * @return Downloaded fileSpec, or {@code null} on failure
     */
    @Nullable
    @WorkerThread
    public static String saveImage(@NonNull final Context context,
                                   @NonNull final String url,
                                   @NonNull final String name,
                                   final int connectTimeout,
                                   @Nullable final Throttler throttler) {

        File file = AppDir.Cache.getFile(context, name + ".jpg");
        try {
            if (url.startsWith(DATA_IMAGE_JPEG_BASE_64)) {
                try (OutputStream os = new FileOutputStream(file)) {
                    final byte[] image = Base64
                            .decode(url.substring(DATA_IMAGE_JPEG_BASE_64.length())
                                       .getBytes(StandardCharsets.UTF_8), 0);
                    os.write(image);
                }
            } else {
                try (TerminatorConnection con =
                             TerminatorConnection.open(context, url, connectTimeout,
                                                       NR_OF_TRIES, throttler)) {
                    file = FileUtils.copyInputStream(context, con.getInputStream(), file);
                }
            }
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "saveImage"
                           + "|e=" + e.getLocalizedMessage());
            }
            FileUtils.delete(file);
            return null;
        }

        // disabled... we assume a picture from a website is already a good size.
        // final Bitmap bitmap = scaleAndRotate(file, MAX_IMAGE_WIDTH_PX, MAX_IMAGE_HEIGHT_PX, 0);
        // if (bitmap == null || !saveBitmap(bitmap, file)) {
        //     return null;
        // }
        return file != null ? file.getAbsolutePath() : null;
    }

    /**
     * Pick the largest image from the given list, and delete all others.
     *
     * @param imageList a list of images
     *
     * @return name of cover found, or {@code null} for none.
     */
    @AnyThread
    @Nullable
    public static String getBestImage(@NonNull final ArrayList<String> imageList) {

        // biggest size based on height * width
        long bestImageSize = -1;
        // index of the file which is the biggest
        int bestFileIndex = -1;

        // Just read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            final String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    final long size = opt.outHeight * opt.outWidth;
                    if (size > bestImageSize) {
                        bestImageSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one.
        // Note there *may* be no best one, so all would be deleted. This is fine.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                FileUtils.delete(new File(imageList.get(i)));
            }
        }

        if (bestFileIndex >= 0) {
            return imageList.get(bestFileIndex);
        }

        return null;
    }

    /**
     * Check if caching is enabled.
     *
     * @param context Current context
     *
     * @return {@code true} if resized images are cached in a database.
     */
    @AnyThread
    public static boolean imagesAreCached(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_image_cache_resized, false);
    }
}
