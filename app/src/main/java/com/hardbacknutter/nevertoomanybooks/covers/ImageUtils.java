/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class ImageUtils {

    /** By default, covers will always be downsized to maximum 600 x 1000 pixels. */
    static final int MAX_IMAGE_WIDTH_PX = 600;
    static final int MAX_IMAGE_HEIGHT_PX = 1000;

    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;
    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 2048;

    private ImageUtils() {
    }

    /**
     * Check if a file is an image with an acceptable size.
     * <p>
     * This is a slow check, use only when import/saving.
     * When displaying do a simple {@code srcFile.exists()} instead.
     * <p>
     * <strong>If the file is not acceptable, then it will be deleted.</strong>
     *
     * @param srcFile to check
     *
     * @return {@code true} if file is acceptable.
     */
    @AnyThread
    public static boolean isAcceptableSize(@Nullable final File srcFile) {
        if (srcFile == null) {
            return false;
        }

        if (srcFile.length() < MIN_VALID_IMAGE_FILE_SIZE) {
            FileUtils.delete(srcFile);
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
            || options.outHeight < MIN_VALID_IMAGE_SIDE
               && options.outWidth < MIN_VALID_IMAGE_SIDE) {
            return null;
        }

        // Calculate the inSampleSize
        options.inSampleSize = 1;
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width LARGER than the requested height and width.
            while (halfHeight / options.inSampleSize >= reqHeight
                   && halfWidth / options.inSampleSize >= reqWidth) {
                options.inSampleSize *= 2;
            }
        }

        // Decode bitmap for real
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(srcFile.getAbsolutePath(), options);
    }

    /**
     * Write out to the destination file.
     *
     * @param bitmap   to write
     * @param destFile file to write to
     *
     * @return (potentially) compressed bitmap; or {@code null} on any non-fatal error.
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @Nullable
    static Bitmap writeFile(@NonNull final Bitmap bitmap,
                            @NonNull final File destFile)
            throws IOException {

        try (OutputStream os = new FileOutputStream(destFile.getAbsoluteFile())) {
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                return bitmap;
            }
        }

        return null;
    }

    /**
     * Check if caching is enabled.
     *
     * @return {@code true} if resized images are cached in a database.
     */
    @AnyThread
    public static boolean isImageCachingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(ServiceLocator.getAppContext())
                                .getBoolean(Prefs.pk_image_cache_resized, false);
    }

    public static void setImageCachingEnabled(final boolean enable) {
        PreferenceManager.getDefaultSharedPreferences(ServiceLocator.getAppContext())
                         .edit()
                         .putBoolean(Prefs.pk_image_cache_resized, enable)
                         .apply();
    }

    /**
     * Given a InputStream with an image, write it to a file in the {@link CoverDir} directory.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param is       InputStream to read
     * @param destFile File to write to
     *
     * @return File written to (the one passed in)
     *
     * @throws StorageException      The {@link CoverDir} directory is not available
     * @throws FileNotFoundException if the input stream was {@code null}
     * @throws IOException           on generic/other IO failures
     */
    @NonNull
    public static File copy(@Nullable final InputStream is,
                            @NonNull final File destFile)
            throws StorageException,
                   FileNotFoundException,
                   IOException {
        if (is == null) {
            throw new FileNotFoundException("InputStream was NULL");
        }

        final File tmpFile = new File(CoverDir.getTemp(ServiceLocator.getAppContext()),
                                      System.nanoTime() + ".jpg");
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            FileUtils.copy(is, os);
            // rename to real output file
            FileUtils.rename(tmpFile, destFile);
            return destFile;
        } finally {
            FileUtils.delete(tmpFile);
        }
    }
}
