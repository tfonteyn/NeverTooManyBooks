/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

/**
 * Note that by default this task <strong>only</strong> decodes the file to a Bitmap.
 * <ol>
 * <li>To scale the input, call one of the {@link Transformation#setScale} methods.</li>
 * <li>For rotation based on the input, call {@link Transformation#setSurfaceRotation}.</li>
 * <li>For specific rotation, call {@link Transformation#setRotation}.
 *     Can be combined with input-rotation</li>
 * </ol>
 * before executing this task.
 * <p>
 * The transformation is done "in-place", i.e the srcFile is overwritten with the result.
 * To use a different file, and leaving the srcFile unchanged,
 * call {@link Transformation#setDestinationFile(File)}.
 */
public class TransFormTask
        extends MTask<TransFormTask.TransformedData> {

    /** Log tag. */
    private static final String TAG = "TransFormTask";

    private Transformation mTransformation;

    TransFormTask() {
        super(R.id.TASK_ID_IMAGE_TRANSFORMATION, TAG);
    }

    void transform(@NonNull final Transformation transformation) {
        mTransformation = transformation;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected TransformedData doWork(@NonNull final Context context) {

        @Nullable
        Bitmap bitmap;

        // Read either a scaled down version (but NOT exact dimensions),
        // or the original version.
        if (mTransformation.isScale()) {
            bitmap = ImageUtils.decodeFile(mTransformation.getSrcFile(),
                                           mTransformation.getMaxWidth(),
                                           mTransformation.getMaxHeight());
        } else {
            bitmap = BitmapFactory.decodeFile(mTransformation.getSrcFile().getAbsolutePath());
        }

        // Rotate if needed
        if (bitmap != null) {
            final int angle = calculateRotationAngle();
            if (angle != 0) {
                bitmap = rotate(bitmap, angle);
            }
        }

        if (bitmap != null) {
            bitmap = writeFile(bitmap);
        }

        return new TransformedData(bitmap,
                                   mTransformation.getDestFile(),
                                   mTransformation.getNextAction());
    }

    /**
     * Write out to the destination file
     *
     * @param bitmap to write
     *
     * @return (potentially) compressed bitmap; or {@code null} on any error.
     */
    @Nullable
    private Bitmap writeFile(@NonNull final Bitmap bitmap) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "saving bitmap to destination=" + mTransformation.getDestFile());
        }

        try {
            try (OutputStream os = new FileOutputStream(
                    mTransformation.getDestFile().getAbsoluteFile())) {
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    return bitmap;
                }
            }
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "Bitmap save FAILED", e);
            }
        }

        return null;
    }


    /**
     * Rotate the given bitmap.
     *
     * @param source bitmap to rotate
     * @param angle  rotate by the specified amount
     *
     * @return the rotated bitmap OR the source bitmap if the rotation fails for any reason.
     */
    @NonNull
    @WorkerThread
    private Bitmap rotate(@NonNull final Bitmap source,
                          final int angle) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "rotate angle=" + angle);
        }
        try {
            final Matrix matrix = new Matrix();
            matrix.setRotate(angle);
            final Bitmap rotatedBitmap = Bitmap.createBitmap(source, 0, 0,
                                                             source.getWidth(), source.getHeight(),
                                                             matrix, true);
            if (rotatedBitmap != source) {
                // clean up the old one right now to save memory.
                source.recycle();
                return rotatedBitmap;
            }
        } catch (@NonNull final OutOfMemoryError e) {
            // this is likely to fail if we're out of memory, but let's try at least
            Logger.error(TAG, e);
        }

        return source;
    }

    private int calculateRotationAngle() {
        if (mTransformation.getExplicitRotation() != 0) {
            // just use the explicit value, ignore device and source file rotation
            return mTransformation.getExplicitRotation();

        } else {
            // Try to adjust the rotation automatically:
            final int exifAngle = getExifAngle();
            final int angle = mTransformation.getSurfaceRotation() - exifAngle;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "exif=" + exifAngle
                           + "|mSurfaceRotation=" + mTransformation.getSurfaceRotation()
                           + "|angle=" + angle
                           + "|(angle % 360)=" + angle % 360
                     );
            }

            return angle;
        }
    }

    /**
     * Get the rotation angle from the EXIF information.
     *
     * @return angle; or {@code 0} on any failure
     */
    private int getExifAngle() {
        final ExifInterface exif;
        try {
            exif = new ExifInterface(mTransformation.getSrcFile());
        } catch (@NonNull final IOException ignore) {
            return 0;
        }

        final int rotation;
        final int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
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
     * Value class with the results.
     */
    static class TransformedData {

        @Nullable
        private final Bitmap mBitmap;
        @NonNull
        private final File mFile;
        @NonNull
        private final CoverHandler.NextAction mNextAction;

        /**
         * Constructor.
         *
         * @param bitmap resulting bitmap; or {@code null} on failure
         * @param file   If the bitmap is set, the transformed file.
         *               If the bitmap is {@code null}, the file value MUST BE IGNORED.
         * @param action as set in
         *               {@link Transformation#setNextAction(CoverHandler.NextAction)};
         *               or {@code 0} if not set.
         */
        TransformedData(@Nullable final Bitmap bitmap,
                        @NonNull final File file,
                        @NonNull final CoverHandler.NextAction action) {
            mBitmap = bitmap;
            mFile = file;
            mNextAction = action;
        }

        @Nullable
        Bitmap getBitmap() {
            return mBitmap;
        }

        @NonNull
        File getFile() {
            return mFile;
        }

        @NonNull
        CoverHandler.NextAction getNextAction() {
            return mNextAction;
        }

        @Override
        @NonNull
        public String toString() {
            return "TransformedData{"
                   + "mNextAction=" + mNextAction
                   + ", mBitmap=" + (mBitmap != null)
                   + ", mFile=" + mFile.getAbsolutePath() + '}';
        }
    }

    /** Value class with the input. */
    static class Transformation {

        @NonNull
        private final File mSrcFile;
        @NonNull
        private File mDestFile;

        private int mMaxWidth = ImageUtils.MAX_IMAGE_WIDTH_PX;
        private int mMaxHeight = ImageUtils.MAX_IMAGE_HEIGHT_PX;
        private boolean mScale;

        private int mExplicitRotation;
        private int mSurfaceRotation;

        private CoverHandler.NextAction mNextAction;

        /**
         * Constructor.
         * <p>
         * Sets a single file as source and destination.
         *
         * @param file to transform
         */
        Transformation(@NonNull final File file) {
            mSrcFile = file;
            mDestFile = mSrcFile;
        }

        /**
         * Set a separate destination file. The source file will not be modified.
         *
         * @param dstFile to write to
         *
         * @return {@code this} (for chaining)
         */
        @SuppressWarnings("unused")
        Transformation setDestinationFile(@NonNull final File dstFile) {
            mDestFile = dstFile;
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
        Transformation setScale(final int width,
                                final int height) {
            mMaxWidth = width;
            mMaxHeight = height;
            mScale = true;
            return this;
        }

        /**
         * Optional. Set an explicit angle to rotate the image.
         *
         * @param rotation to rotate; or {@code 0} for none.
         *
         * @return {@code this} (for chaining)
         */
        Transformation setRotation(final int rotation) {
            mExplicitRotation = rotation;
            return this;
        }

        @NonNull
        File getSrcFile() {
            return mSrcFile;
        }

        @NonNull
        File getDestFile() {
            return mDestFile;
        }

        int getMaxWidth() {
            return mMaxWidth;
        }

        int getMaxHeight() {
            return mMaxHeight;
        }

        boolean isScale() {
            return mScale;
        }

        /**
         * Enable scaling to default dimensions.
         *
         * @param scale flag
         *
         * @return {@code this} (for chaining)
         */
        Transformation setScale(final boolean scale) {
            mScale = scale;
            return this;
        }

        int getExplicitRotation() {
            return mExplicitRotation;
        }

        int getSurfaceRotation() {
            return mSurfaceRotation;
        }

        /**
         * Optional. Set the device rotation.
         * Will be ignored if {@link #setRotation(int)} is set to a non-zero value.
         *
         * @param surfaceRotation as taken from the window manager
         *
         * @return {@code this} (for chaining)
         */
        Transformation setSurfaceRotation(final int surfaceRotation) {
            switch (surfaceRotation) {
                case Surface.ROTATION_0:
                    mSurfaceRotation = 90;
                    break;

                case Surface.ROTATION_180:
                    mSurfaceRotation = -90;
                    break;

                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                default:
                    mSurfaceRotation = 0;
                    break;
            }

            return this;
        }

        @NonNull
        CoverHandler.NextAction getNextAction() {
            return mNextAction;
        }

        /**
         * Set an optional return code which will be passed back.
         *
         * @param action to pass back
         *
         * @return {@code this} (for chaining)
         */
        Transformation setNextAction(@NonNull final CoverHandler.NextAction action) {
            mNextAction = action;
            return this;
        }
    }
}
