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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

public class TransFormTask
        extends AsyncTask<Void, Void, Bitmap> {

    /** Log tag. */
    private static final String TAG = "TransFormTask";

    @NonNull
    private final File mFile;
    @NonNull
    private final WeakReference<OnAfterTransformListener> mListener;

    private int mMaxWidth = ImageUtils.MAX_IMAGE_WIDTH_PX;
    private int mMaxHeight = ImageUtils.MAX_IMAGE_HEIGHT_PX;
    private boolean mScale;
    private int mExplicitAngle;
    private int mSurfaceRotation;
    private int mReturnCode;

    /**
     * Constructor.
     * <p>
     * Note that by default this task <strong>only</strong> decodes the file to a Bitmap.
     * <ol>
     * <li>To scale the input, call one of the {@link #setScale} methods.</li>
     * <li>For rotation based on the input, call {@link #setWindowManager}.</li>
     * <li>For specific rotation, call {@link #setRotate}. Can be combined with input-rotation</li>
     * </ol>
     * before executing this task.
     *
     * @param srcFile  file to transform
     * @param listener where to send results
     */
    TransFormTask(@NonNull final File srcFile,
                  @Nullable final OnAfterTransformListener listener) {

        mFile = srcFile;
        mListener = new WeakReference<>(listener);
    }

    /**
     * Enable scaling to default dimensions.
     *
     * @param scale flag
     *
     * @return TransFormTask (for chaining)
     */
    TransFormTask setScale(final boolean scale) {
        mScale = scale;
        return this;
    }

    /**
     * Enable scaling to the given dimensions.
     *
     * @param width  to use
     * @param height to use
     *
     * @return TransFormTask (for chaining)
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    TransFormTask setScale(final int width,
                           final int height) {
        mMaxWidth = width;
        mMaxHeight = height;
        mScale = true;
        return this;
    }

    /**
     * Set an explicit angle to rotate the image.
     *
     * @param angle to rotate; or {@code 0} for none.
     *
     * @return TransFormTask (for chaining)
     */
    TransFormTask setRotate(final int angle) {
        mExplicitAngle = angle;
        return this;
    }

    /**
     * Optional. Use the window manager to correct any device rotation automatically.
     * Will be ignored if {@link #setRotate(int)} is called.
     *
     * @param windowManager to get the rotation from
     *
     * @return TransFormTask (for chaining)
     */
    TransFormTask setWindowManager(@NonNull final WindowManager windowManager) {
        switch (windowManager.getDefaultDisplay().getRotation()) {
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

    /**
     * Set an optional return code which will be passed back in
     * {@link OnAfterTransformListener#onFinished}.
     *
     * @param returnCode to pass back
     *
     * @return TransFormTask (for chaining)
     */
    TransFormTask setReturnCode(final int returnCode) {
        mReturnCode = returnCode;
        return this;
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
            Logger.error(App.getTaskContext(), TAG, e);
        }

        return source;
    }

    /**
     * Get the rotation angle from the EXIF information.
     *
     * @return angle; or {@code 0} on any failure
     */
    private int getExifAngle() {
        final ExifInterface exif;
        try {
            exif = new ExifInterface(mFile);
        } catch (@NonNull final IOException ignore) {
            return 0;
        }

        final int rotation;
        int orientation = exif.getAttributeInt(
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

    @Override
    @Nullable
    protected Bitmap doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);

        @Nullable
        Bitmap bitmap;

        // Read either a scaled down version (but NOT exact dimensions) or
        // the original version.
        if (mScale) {
            bitmap = ImageUtils.decodeFile(mFile, mMaxWidth, mMaxHeight);
        } else {
            bitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath());
        }

        // Try and get the rotation right
        int angle;
        if (mExplicitAngle != 0) {
            // use explicit value, ignore device and input
            angle = mExplicitAngle;
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "angle=" + angle);
            }

        } else {
            // negate exif to compensate
            int compensateExif = -getExifAngle();

            angle = compensateExif + mSurfaceRotation;

            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "exif=" + compensateExif
                           + "|mSurfaceRotation=" + mSurfaceRotation
                           + "|angle=" + angle
                           + "|angle % 360=" + angle % 360
                     );
            }
        }

        // Rotate if we can/need to.
        if (bitmap != null && angle != 0) {
            bitmap = rotate(bitmap, angle);
        }

        // Write back to the file
        if (bitmap != null) {
            if (ImageUtils.saveBitmap(bitmap, mFile)) {
                return bitmap;
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(@Nullable final Bitmap bitmap) {
        if (mListener.get() != null) {
            mListener.get().onFinished(new TransformedData(bitmap, mFile, mReturnCode));
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onAfterTransform|" + ErrorMsg.LISTENER_WAS_DEAD);
            }
        }
    }

    public interface OnAfterTransformListener {

        /**
         * Called after a transformation operation.
         *
         * @param data to return
         */
        void onFinished(@NonNull TransformedData data);
    }

    public static class TransformedData
            implements LiveDataEvent {

        /** {@link LiveDataEvent}. */
        private boolean mHasBeenHandled;

        @Nullable
        public final Bitmap bitmap;
        @Nullable
        public final File file;
        final int returnCode;

        /**
         * @param bitmap     resulting bitmap; or {@code null} on failure
         * @param file       If the bitmap is set, the transformed file.
         *                   If the bitmap is {@code null}, the file value MUST BE IGNORED.
         * @param returnCode as set in {@link #setReturnCode(int)}; or {@code 0} if not set.
         */
        TransformedData(@Nullable final Bitmap bitmap,
                        @Nullable final File file,
                        final int returnCode) {
            this.bitmap = bitmap;
            this.file = file;
            this.returnCode = returnCode;
        }

        @Override
        public boolean isNewEvent() {
            boolean isNew = !mHasBeenHandled;
            mHasBeenHandled = true;
            return isNew;
        }
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

}
