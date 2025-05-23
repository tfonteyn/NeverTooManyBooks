/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.util.logger.LoggerFactory;

class Transformation {

    /** Log tag. */
    private static final String TAG = "Transformation";
    /**
     * By default, covers will always be downsized to maximum 600 x 1000 pixels.
     * Override with {@link #setScale(int, int)}
     */
    @Dimension
    private static final int MAX_IMAGE_WIDTH_PX = 600;
    @Dimension
    private static final int MAX_IMAGE_HEIGHT_PX = 1000;
    @Nullable
    private File srcFile;
    @Dimension
    private int maxWidth = MAX_IMAGE_WIDTH_PX;
    @Dimension
    private int maxHeight = MAX_IMAGE_HEIGHT_PX;
    private boolean scale;
    private boolean rotate;

    private int explicitRotation;
    private int surfaceRotation;

    @NonNull
    private static Optional<Bitmap> rotate(@NonNull final Bitmap bitmap,
                                           final int angle) {
        try {
            final Matrix matrix = new Matrix();
            matrix.setRotate(angle);
            final Bitmap rotatedBitmap =
                    Bitmap.createBitmap(bitmap, 0, 0,
                                        bitmap.getWidth(), bitmap.getHeight(),
                                        matrix, true);
            if (rotatedBitmap != bitmap) {
                // clean up the old one right now to save memory.
                bitmap.recycle();
                return Optional.of(rotatedBitmap);
            }
        } catch (@NonNull final OutOfMemoryError e) {
            // logging is likely to fail if we're out of memory, but let's try at least
            LoggerFactory.getLogger().e(TAG, e);
        }
        return Optional.empty();
    }

    /**
     * Set the source file.
     *
     * @param file to transform; The file will not be modified.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public Transformation setSource(@Nullable final File file) {
        this.srcFile = file;
        return this;
    }

    /**
     * Enable scaling to default dimensions.
     *
     * @param scale flag
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    Transformation setScale(final boolean scale) {
        this.scale = scale;
        return this;
    }

    /**
     * Enable scaling to the given dimensions.
     *
     * @param width  Maximum desired width of the image
     * @param height Maximum desired height of the image
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    Transformation setScale(@Dimension final int width,
                            @Dimension final int height) {
        maxWidth = width;
        maxHeight = height;
        scale = true;
        return this;
    }

    /**
     * Set an explicit angle to rotate the image.
     *
     * @param rotation to rotate; or {@code 0} for none.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    Transformation setRotation(final int rotation) {
        explicitRotation = rotation;
        rotate = true;
        return this;
    }

    /**
     * Set the device rotation.
     * Will be ignored if {@link #setRotation(int)} is set to a non-zero value.
     *
     * @param surfaceRotation as taken from the window manager
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    Transformation setSurfaceRotation(final int surfaceRotation) {
        switch (surfaceRotation) {
            case Surface.ROTATION_0:
                this.surfaceRotation = 90;
                break;

            case Surface.ROTATION_180:
                this.surfaceRotation = -90;
                break;

            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
            default:
                this.surfaceRotation = 0;
                break;
        }

        rotate = true;
        return this;
    }

    /**
     * Process the input file.
     *
     * @return the transformed bitmap
     *
     * @throws IllegalArgumentException if the source file is not set / does not exist
     */
    @WorkerThread
    @NonNull
    Optional<Bitmap> transform() {
        if (srcFile == null || !srcFile.exists()) {
            throw new IllegalArgumentException("No file");
        }

        final Bitmap bitmap;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (scale) {
                    bitmap = decodeAndScaleApi28(srcFile);
                } else {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(srcFile));
                }
            } else {
                if (scale) {
                    bitmap = decodeAndScaleApi26(srcFile.getAbsolutePath());
                } else {
                    bitmap = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
                }
            }
        } catch (final IOException e) {
            return Optional.empty();
        }

        if (bitmap != null) {
            if (rotate) {
                final int angle = determineRotationAngle(srcFile);
                if (angle != 0) {
                    final Optional<Bitmap> rotatedBitmap = rotate(bitmap, angle);
                    if (rotatedBitmap.isPresent()) {
                        return rotatedBitmap;
                    }
                }
            }
            return Optional.of(bitmap);
        }

        return Optional.empty();
    }

    private int determineRotationAngle(@NonNull final File file) {
        if (explicitRotation == 0) {
            // Try to adjust the rotation automatically:
            final int exifAngle = getExifAngle(file);
            final int angle = surfaceRotation - exifAngle;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger().d(TAG, "determineRotationAngle",
                                            "exif=" + exifAngle,
                                            "surfaceRotation=" + surfaceRotation,
                                            "angle=" + angle,
                                            "(angle % 360)=" + angle % 360);
            }
            return angle;

        } else {
            // just use the explicit value, ignore device and source file rotation
            return explicitRotation;
        }
    }

    /**
     * Get the rotation angle from the EXIF information.
     *
     * @param file to be decoded.
     *
     * @return angle; or {@code 0} on any failure
     */
    private int getExifAngle(@NonNull final File file) {
        final ExifInterface exif;
        try {
            exif = new ExifInterface(file);
        } catch (@NonNull final IOException ignore) {
            return 0;
        }

        final int rotation;
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                                     ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;

            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;

            case ExifInterface.ORIENTATION_NORMAL:
            default:
                rotation = 0;
                break;
        }

        return rotation;
    }

    /**
     * Decode a file path into a bitmap and scale it to fit the given bounds
     * while preserving the aspect ratio.
     * <p>
     * The image is certain to fill the box, with its exact dimensions
     * the smallest possible larger than the requested dimensions.
     * or i.o.w this is NOT an exact scaling!
     *
     * @param file to be decoded.
     *
     * @return the decoded bitmap
     *
     * @throws IOException on decoding failures
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @NonNull
    private Bitmap decodeAndScaleApi28(@NonNull final File file)
            throws IOException {
        final ImageDecoder.Source source = ImageDecoder.createSource(file);

        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            final int originalWidth = info.getSize().getWidth();
            final int originalHeight = info.getSize().getHeight();

            // Calculate the scale factor to just exceed the target dimensions
            final float scaleX = (float) maxWidth / originalWidth;
            final float scaleY = (float) maxHeight / originalHeight;
            // Scale up to exceed both dimensions
            final float scaleUp = Math.max(scaleX, scaleY);

            final int targetWidth = (int) Math.ceil(originalWidth * scaleUp);
            final int targetHeight = (int) Math.ceil(originalHeight * scaleUp);

            decoder.setTargetSize(targetWidth, targetHeight);
        });
    }

    @Nullable
    private Bitmap decodeAndScaleApi26(@NonNull final String pathName) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);

        // Abort if no size info, or if the image is too small to be used.
        final CoverStorage coverStorage = ServiceLocator.getInstance().getCoverStorage();
        if (!coverStorage.isAcceptableSize(options)) {
            return null;
        }

        // Calculate the inSampleSize
        options.inSampleSize = 1;
        if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width LARGER than the requested height and width.
            while (halfHeight / options.inSampleSize >= maxHeight
                   && halfWidth / options.inSampleSize >= maxWidth) {
                options.inSampleSize *= 2;
            }
        }

        // Decode bitmap for real
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(pathName, options);
    }
}
