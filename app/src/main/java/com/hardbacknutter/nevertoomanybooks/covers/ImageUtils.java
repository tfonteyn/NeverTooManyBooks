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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;


public final class ImageUtils {

    /**
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     * -> width = 0.6 * maxHeight.
     */
    public static final float HW_RATIO = 0.6f;
    /** By default, covers will always be downsized to maximum 600 x 1000 pixels. */
    static final int MAX_IMAGE_WIDTH_PX = 600;
    static final int MAX_IMAGE_HEIGHT_PX = 1000;

    /** Log tag. */
    private static final String TAG = "ImageUtils";
    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;
    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 2048;
    /** The prefix an embedded image url will have. */
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    private ImageUtils() {
    }

    /**
     * Set a placeholder drawable in the view.
     *
     * @param imageView    View to populate
     * @param drawable     drawable to use
     * @param background   (optional) drawable to use for the background; use {@code 0} for none
     * @param layoutWidth  layout width parameter
     * @param layoutHeight layout height parameter
     */
    @UiThread
    public static void setPlaceholder(@NonNull final ImageView imageView,
                                      @DrawableRes final int drawable,
                                      @DrawableRes final int background,
                                      final int layoutWidth,
                                      final int layoutHeight) {
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.width = layoutWidth;
        lp.height = layoutHeight;
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
     * @param context        Application context
     * @param url            Image file URL
     * @param prefix         for the filename
     * @param suffix         for the filename
     * @param cIdx           0..n image index
     * @param connectTimeout in milliseconds
     * @param readTimeout    in milliseconds
     * @param throttler      (optional) {@link Throttler} to use
     *
     * @return Downloaded fileSpec, or {@code null} on failure
     */
    @Nullable
    @WorkerThread
    public static String saveImage(@NonNull final Context context,
                                   @NonNull final String url,
                                   @NonNull final String prefix,
                                   @NonNull final String suffix,
                                   @IntRange(from = 0) final int cIdx,
                                   @IntRange(from = 0) final int connectTimeout,
                                   @IntRange(from = 0) final int readTimeout,
                                   @Nullable final Throttler throttler) {


        // We don't use {@code File.createTempFile(prefix, suffix);} because we
        // want a NAME (not a file). The actual file will be in a fixed directory,
        // and not in the system temp folder.
        final String tmpName;
        if (!prefix.isEmpty()) {
            tmpName = prefix + suffix + '_' + cIdx;
        } else {
            // just use something...
            tmpName = System.currentTimeMillis() + suffix + '_' + cIdx;
        }

        File file = AppDir.Cache.getFile(context, tmpName + ".jpg");
        try {
            if (url.startsWith(DATA_IMAGE_JPEG_BASE_64)) {
                try (OutputStream os = new FileOutputStream(file)) {
                    final byte[] image = Base64
                            .decode(url.substring(DATA_IMAGE_JPEG_BASE_64.length())
                                       .getBytes(StandardCharsets.UTF_8), 0);
                    os.write(image);
                }
            } else {
                try (TerminatorConnection con = new TerminatorConnection(
                        context, url, connectTimeout, readTimeout, throttler)) {
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
     * Check if caching is enabled.
     *
     * @param context Current context
     *
     * @return {@code true} if resized images are cached in a database.
     */
    @AnyThread
    public static boolean isImageCachingEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_image_cache_resized, false);
    }
}
