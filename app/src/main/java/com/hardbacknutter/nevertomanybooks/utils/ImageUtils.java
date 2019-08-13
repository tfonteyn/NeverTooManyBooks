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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection;

// collapse all lines, restart app
// scroll to Pratchett (175 books) on 1st line, expand, scroll to end.
//////////////////////////////////////////////////////////////////////////// test 1
// no cache
// ImageUtils.fileChecks=175|ImageUtils.fileChecks=2065
// ImageUtils.fileChecks=175|ImageUtils.fileTicks=2094
//
// from cache
// ImageUtils.cacheChecks=175|ImageUtils.cacheTicks=2305
//
// ==> without rescaling code (leaving it to Android) ==> no point in using a cache.
////////////////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////////////// test 2
// writing to cache with REAL scaling
// ImageUtils.fileChecks=175|ImageUtils.fileTicks=2678

// using cache
// ImageUtils.cacheChecks=175|ImageUtils.cacheTicks=1585
//
// ==> so during the writing, its slower.... but afterwards, 50% faster compared to test 1
////////////////////////////////////////////////////////////////////////////

public final class ImageUtils {

    /**
     * The minimum size an image file must be to be considered valid.
     * 200: based on LibraryThing 1x1 pixel placeholder being 178 bytes in download
     * (43 after compression on disk).
     */
    public static final int MIN_IMAGE_FILE_SIZE = 200;

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

    // temp debug
    public static final AtomicLong cacheTicks = new AtomicLong();
    public static final AtomicLong fileTicks = new AtomicLong();
    public static final AtomicInteger cacheChecks = new AtomicInteger();
    public static final AtomicInteger fileChecks = new AtomicInteger();

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
     * Load the image file into the destination view.
     * Handles checking & storing in the cache.
     *
     * @param imageView View to populate
     * @param uuid      UUID of book
     * @param maxWidth  Max width of resulting image
     * @param maxHeight Max height of resulting image
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView imageView,
                                       @NonNull final String uuid,
                                       final int maxWidth,
                                       final int maxHeight) {

        // 1. If caching is used, and we don't have cache building happening, check it.
        if (imagesAreCached() && !CoversDAO.ImageCacheWriterTask.hasActiveTasks()) {

            long tick = System.nanoTime();
            cacheChecks.incrementAndGet();
            Bitmap bm = CoversDAO.getImage(uuid, maxWidth, maxHeight);
            if (bm != null) {
                boolean isSet = ImageUtils.setImageView(imageView, bm, maxWidth, maxHeight, true);
                cacheTicks.addAndGet(System.nanoTime() - tick);
                return isSet;
            }
        }

        // 2. Check if the file exists; if it does not set 'ic_broken_image' icon and exit.
        File file = StorageUtils.getCoverFile(uuid);
        if (!file.exists() || file.length() < MIN_IMAGE_FILE_SIZE) {
            imageView.setImageResource(R.drawable.ic_broken_image);
            return false;
        }

        // 3. If caching is used, go get the image from the file system and send it to the cache.
        if (imagesAreCached()) {
            long tick = System.nanoTime();
            fileChecks.incrementAndGet();
            imageView.setMaxWidth(maxWidth);
            imageView.setMaxHeight(maxHeight);
            Bitmap bm = forceScaleBitmap(file.getAbsolutePath(), maxWidth, maxHeight, true);
            if (bm != null) {
                // display
                imageView.setImageBitmap(bm);
                // and send to the cache
                new CoversDAO.ImageCacheWriterTask(uuid, maxWidth, maxHeight, bm)
                        .execute();
                fileTicks.addAndGet(System.nanoTime() - tick);
                return true;

            } else {
                imageView.setImageResource(R.drawable.ic_broken_image);
                return false;
            }
        }

        // 4. Just go get the image from the file system.
        return setImageView(imageView, file, maxWidth, maxHeight, true);
    }

    /**
     * Convenience method for {@link #setImageView(ImageView, Bitmap, int, int, boolean)}.
     *
     * @param imageView The ImageView to load with the file or an appropriate icon
     * @param file      The file of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    public static boolean setImageView(@NonNull final ImageView imageView,
                                       @NonNull final File file,
                                       final int maxWidth,
                                       final int maxHeight,
                                       final boolean upscale) {
        if (file.exists() && file.length() > MIN_IMAGE_FILE_SIZE) {
            @Nullable
            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            return setImageView(imageView, bm, maxWidth, maxHeight, upscale);
        }

        imageView.setImageResource(R.drawable.ic_broken_image);
        return false;
    }

    /**
     * Load the image bitmap into the destination view.
     * Scaling is done by Android, enforced by the view itself and the dimensions passed in.
     *
     * @param imageView The ImageView to load with the bitmap or an appropriate icon
     * @param source    The Bitmap of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    private static boolean setImageView(@NonNull final ImageView imageView,
                                        @Nullable final Bitmap source,
                                        final int maxWidth,
                                        final int maxHeight,
                                        final boolean upscale) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.debug(ImageUtils.class, "setImageView",
                         "maxWidth=" + maxWidth,
                         "maxHeight=" + maxHeight,
                         "upscale=" + upscale,
                         source != null ? "bm.width=" + source.getWidth() : "no bm",
                         source != null ? "bm.height=" + source.getHeight() : "no bm");
        }

        imageView.setMaxWidth(maxWidth);
        imageView.setMaxHeight(maxHeight);

        if (source != null) {
            // upscale only when needed.
            if (source.getHeight() < maxHeight && upscale) {
                Bitmap scaledBitmap;
                scaledBitmap = createScaledBitmap(source, maxWidth, maxHeight);
                if (!source.equals(scaledBitmap)) {
                    // clean up the old one right now to save memory.
                    source.recycle();
                    imageView.setImageBitmap(scaledBitmap);
                    return true;
                }
            }
            // if not upscaling, let Android decide on any other scaling as needed.
            imageView.setImageBitmap(source);
            return true;
        }

        imageView.setImageResource(R.drawable.ic_broken_image);
        return false;
    }

    /**
     * Convenience method for {@link #createScaledBitmap(Bitmap, int, int)}
     *
     * @param file  The file of the image
     * @param scale user preferred scale factor
     *
     * @return the bitmap, or {@code null} if the file failed to decode.
     */
    @Nullable
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final File file,
                                            final int scale) {
        @Nullable
        Bitmap bm = BitmapFactory.decodeFile(file.getPath());
        if (bm == null) {
            return null;
        }
        int maxSize = ImageUtils.getMaxImageSize(scale);
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
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != dstWidth || height != dstHeight) {
            float sx = (float) dstWidth / width;
            float sy = (float) dstHeight / height;
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
     * <p>
     * <b>Note:</b>: forceScaleBitmap is an expensive operation. Make sure you really need it.
     * This method is slower then {@link #createScaledBitmap} but produces a truly scaled
     * bitmap to fit withing the bounds passed in.
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
        float ratio = (widthRatio < heightRatio) ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        int idealSampleSize = (int) Math.ceil(1 / ratio);
        // Get the nearest *bigger* power of 2.
        int samplePow2 = (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

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

        int bytesRead = 0;
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            bytesRead = StorageUtils.saveInputStreamToFile(con.inputStream, file);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(ImageUtils.class, e);
            }
        }

        return bytesRead > 0 ? file.getAbsolutePath() : null;
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
     * @return {@code true} if resized images are cached in a database.
     */
    public static boolean imagesAreCached() {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                .getBoolean(Prefs.pk_images_cache_resized, false);
    }
}
