/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.graphics.Matrix;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

class Transformation {

    /** Log tag. */
    private static final String TAG = "Transformation";

    @NonNull
    private final File srcFile;

    private int maxWidth = ImageUtils.MAX_IMAGE_WIDTH_PX;
    private int maxHeight = ImageUtils.MAX_IMAGE_HEIGHT_PX;
    private boolean scale;

    private int explicitRotation;
    private int surfaceRotation;

    /**
     * Constructor.
     * <p>
     * Sets a single file as source and destination.
     *
     * @param file to transform; The file will not be modified.
     */
    Transformation(@NonNull final File file) {
        srcFile = file;
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
     * @param width  to use
     * @param height to use
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("unused")
    @NonNull
    Transformation setScale(final int width,
                            final int height) {
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

        return this;
    }

    /**
     * Process the input file.
     *
     * @return the transformed bitmap OR the source bitmap
     * if the transformation fails for any reason.
     */
    @NonNull
    Optional<Bitmap> transform() {
        // Read either a scaled down version (but NOT exact dimensions),
        // or the original version.
        final Bitmap bitmap;
        if (scale) {
            bitmap = ImageUtils.decodeFile(srcFile, maxWidth, maxHeight);
        } else {
            bitmap = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
        }

        if (bitmap != null) {
            final int angle;
            if (explicitRotation != 0) {
                // just use the explicit value, ignore device and source file rotation
                angle = explicitRotation;
            } else {
                // Try to adjust the rotation automatically:
                final int exifAngle = getExifAngle(srcFile);
                angle = surfaceRotation - exifAngle;

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Log.d(TAG, "exif=" + exifAngle
                               + "|surfaceRotation=" + surfaceRotation
                               + "|angle=" + angle
                               + "|(angle % 360)=" + angle % 360);
                }
            }

            if (angle != 0) {
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
                    ServiceLocator.getInstance().getLogger().error(TAG, e);
                }
            }
            return Optional.of(bitmap);
        }

        return Optional.empty();
    }

    /**
     * Get the rotation angle from the EXIF information.
     *
     * @param file to read
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
}
