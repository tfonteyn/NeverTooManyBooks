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
import java.io.IOException;

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

        // Write out to the destination file
        if (bitmap != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "saving bitmap to destination="
                           + mTransformation.getDestFile());
            }

            if (!ImageUtils.saveBitmap(bitmap, mTransformation.getDestFile())) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Bitmap save FAILED");
                }
                bitmap = null;
            }
        }

        return new TransformedData(bitmap,
                                   mTransformation.getDestFile(),
                                   mTransformation.getReturnCode());
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

    //    /**
//     * Decode the image from the given file and scale it to
//     * <strong>FILL the given bounds</strong> while preserving the aspect ratio.
//     * <p>
//     * Optionally rotates after scaling to the given angle.
//     *
//     * @param srcFile   the file to read from
//     * @param maxWidth  Maximum desired width of the image
//     * @param maxHeight Maximum desired height of the image
//     * @param angle     to rotate. Use {@code 0} to not rotate.
//     *
//     * @return The bitmap, or {@code null} on any failure
//     */
//    @SuppressWarnings("unused")
//    @Nullable
//    @WorkerThread
//    public static Bitmap overscanScaleToFitAndRotate(@Nullable final File srcFile,
//                                                     final int maxWidth,
//                                                     final int maxHeight,
//                                                     final int angle) {
//        if (srcFile == null || !srcFile.exists()) {
//            return null;
//        }
//
//        // Read the bitmap size.
//        final BitmapFactory.Options opt = new BitmapFactory.Options();
//        opt.inJustDecodeBounds = true;
//        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), opt);
//
//        // Abort if no size info, or to small to be any good.
//        if (opt.outHeight <= 0 || opt.outWidth <= 0
//            || (opt.outHeight < MIN_IMAGE_SIDE && opt.outWidth < MIN_IMAGE_SIDE)) {
//            return null;
//        }
//
//        // Work out how to scale the file to fit in required box.
//        // Basically max out EITHER the width or the height, so the image fits in the box and
//        // is as big as possible while still preserving the aspect ratio.
//        float widthRatio = (float) maxWidth / opt.outWidth;
//        float heightRatio = (float) maxHeight / opt.outHeight;
//        float ratio = Math.min(widthRatio, heightRatio);
//
//        opt.inSampleSize = (int) Math.ceil(1 / ratio);
//        // we want to round UP to the nearest power of 2.
//        opt.inSampleSize = (int) Math.pow(2, Math.ceil(Math.log(opt.inSampleSize) / Math.log(2)));
//        // We sample at twice the size we need, and will subsequently scale it.
//        opt.inSampleSize = opt.inSampleSize / 2;
//        // sanity check
//        if (opt.inSampleSize < 1) {
//            opt.inSampleSize = 1;
//        }
//
//        // This time we want the image itself
//        opt.inJustDecodeBounds = false;
//
//        @Nullable
//        final Bitmap source = BitmapFactory.decodeFile(srcFile.getAbsolutePath(), opt);
//        if (source == null) {
//            return null;
//        }
//
//        // Fix ratio based on the new sample size
//        ratio = ratio / (1.0f / opt.inSampleSize);
//
//        final Matrix matrix = new Matrix();
//        matrix.setScale(ratio, ratio);
//        // optional rotation afterwards
//        if (angle != 0) {
//            matrix.postRotate(angle);
//        }
//        try {
//            final Bitmap scaledBitmap = Bitmap.createBitmap(source, 0, 0,
//                                                            source.getWidth(), source.getHeight(),
//                                                            matrix, true);
//            if (!source.equals(scaledBitmap)) {
//                // clean up the source right now to save memory.
//                source.recycle();
//            }
//
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
//                Log.d(TAG, "scaleToFitAndRotate"
//                           + "|width=" + scaledBitmap.getWidth()
//                           + "|height=" + scaledBitmap.getHeight());
//            }
//            return scaledBitmap;
//
//        } catch (@NonNull final OutOfMemoryError e) {
//            // this is likely to fail if we're out of memory, but let's try at least
//            Logger.error(App.getAppContext(), TAG, e);
//            return null;
//        }
//    }

    /**
     * Value class with the results.
     */
    static class TransformedData {

        @Nullable
        private final Bitmap mBitmap;
        @NonNull
        private final File mFile;
        private final int mReturnCode;

        /**
         * Constructor.
         *
         * @param bitmap     resulting bitmap; or {@code null} on failure
         * @param file       If the bitmap is set, the transformed file.
         *                   If the bitmap is {@code null}, the file value MUST BE IGNORED.
         * @param returnCode as set in {@link Transformation#setReturnCode(int)};
         *                   or {@code 0} if not set.
         */
        TransformedData(@Nullable final Bitmap bitmap,
                        @NonNull final File file,
                        final int returnCode) {
            mBitmap = bitmap;
            mFile = file;
            mReturnCode = returnCode;
        }

        @Nullable
        Bitmap getBitmap() {
            return mBitmap;
        }

        @NonNull
        File getFile() {
            return mFile;
        }

        int getReturnCode() {
            return mReturnCode;
        }

        @Override
        @NonNull
        public String toString() {
            return "TransformedData{"
                   + "mReturnCode=" + mReturnCode
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

        private int mReturnCode;

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
                    mSurfaceRotation = +90;
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

        int getReturnCode() {
            return mReturnCode;
        }

        /**
         * Set an optional return code which will be passed back.
         *
         * @param returnCode to pass back
         *
         * @return {@code this} (for chaining)
         */
        Transformation setReturnCode(final int returnCode) {
            mReturnCode = returnCode;
            return this;
        }
    }
}
