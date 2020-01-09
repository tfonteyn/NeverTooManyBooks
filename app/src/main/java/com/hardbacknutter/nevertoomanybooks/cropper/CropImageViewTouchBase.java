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

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.nevertoomanybooks.cropper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public abstract class CropImageViewTouchBase
        extends AppCompatImageView {

    /**
     * We get 'unsupported feature' crashes if the option to always use GL is turned on.
     * See:
     * <a href="http://developer.android.com/guide/topics/graphics/hardware-accel.html>
     *     http://developer.android.com/guide/topics/graphics/hardware-accel.html</a>
     * <a href="http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath">
     *     http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath</a>
     * so for API level > 11, we turn it off manually.
     * <p>
     * 2018-11-30: making this a configuration option
     * <p>
     * Actual system values:
     * <p>
     * View.LAYER_TYPE_SOFTWARE 1
     * View.LAYER_TYPE_HARDWARE 2
     * <p>
     * We use 1 and 2; and 'abuse' -1 to mean 'leave it unset'
     * <p>
     * see {@link View#setLayerType(int, Paint)}
     */
    private static final int LAYER_TYPE_USE_DEFAULT = -1;

    private static final float SCALE_RATE = 1.25F;
    /** Maximum upscaling for a viewed image. */
    private static final float SCALE_LIMIT_MAX = Float.MAX_VALUE;
    /** The current bitmap being displayed. */
    final CropRotateBitmap mBitmapDisplayed = new CropRotateBitmap();
    /**
     * This is the base transformation which is used to show the image
     * initially. The current computation for this shows the image in
     * its entirety, letter boxing as needed. One could choose to
     * show the image as cropped instead.
     * <p>
     * This matrix is recomputed when we go from the thumbnail image to
     * the full size image.
     */
    private final Matrix mBaseMatrix = new Matrix();
    /**
     * This is the supplementary transformation which reflects what
     * the user has done in terms of zooming and panning.
     * <p>
     * This matrix remains the same when we go from the thumbnail image
     * to the full size image.
     */
    private final Matrix mSuppMatrix = new Matrix();
    private final Handler mHandler = new Handler();
    /**
     * This is the final matrix which is computed as the concatenation
     * of the base matrix and the supplementary matrix.
     */
    private final Matrix mDisplayMatrix = new Matrix();
    /** Temporary buffer used for getting the values out of a matrix. */
    private final float[] mMatrixValues = new float[9];
    int mLeft;
    int mRight;
    int mTop;
    int mBottom;
    private int mThisWidth = -1;
    private int mThisHeight = -1;
    private float mMaxZoom;
    private Recycler mRecycler;
    @Nullable
    private Runnable mOnLayoutRunnable;

    public CropImageViewTouchBase(@NonNull final Context context) {
        super(context);
        init();
    }

    public CropImageViewTouchBase(@NonNull final Context context,
                                  @NonNull final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ImageView.ScaleType.MATRIX);

        // specific for Android Studio so the view can render in the layout editor.
        if (isInEditMode()) {
            return;
        }

        int type = PIntString.getListPreference(Prefs.pk_image_cropper_layer_type,
                                                LAYER_TYPE_USE_DEFAULT);
        if (type == LAYER_TYPE_USE_DEFAULT) {
            return;
        }
        setLayerType(type, null);
    }

    @SuppressWarnings("unused")
    public void setRecycler(@NonNull final Recycler r) {
        mRecycler = r;
    }

    @Override
    @CallSuper
    public boolean onKeyDown(final int keyCode,
                             @NonNull final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && getScale() > 1.0f) {
            // If we're large in, pressing Back jumps out to show the entire
            // image, otherwise Back returns the user to the gallery.
            zoomTo(1.0f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @CallSuper
    protected void onLayout(final boolean changed,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mLeft = left;
        mRight = right;
        mTop = top;
        mBottom = bottom;
        mThisWidth = right - left;
        mThisHeight = bottom - top;
        Runnable r = mOnLayoutRunnable;
        if (r != null) {
            mOnLayoutRunnable = null;
            r.run();
        }
        if (mBitmapDisplayed.getBitmap() != null) {
            getProperBaseMatrix(mBitmapDisplayed, mBaseMatrix);
            setImageMatrix(getImageViewMatrix());
        }
    }

    @Override
    public void setImageBitmap(@Nullable final Bitmap bm) {
        setImageBitmap(bm, 0);
    }

    @CallSuper
    private void setImageBitmap(@Nullable final Bitmap bitmap,
                                final int rotation) {
        super.setImageBitmap(bitmap);

        Bitmap old = mBitmapDisplayed.getBitmap();
        mBitmapDisplayed.setBitmap(bitmap);
        mBitmapDisplayed.setRotation(rotation);

        if (old != null && old != bitmap && mRecycler != null) {
            mRecycler.recycle(old);
        }
    }

    public void clear() {
        setImageBitmapResetBase(null, true);
    }

    /**
     * This function changes bitmap, reset base matrix according to the size
     * of the bitmap, and optionally reset the supplementary matrix.
     */
    public void setImageBitmapResetBase(@Nullable final Bitmap bitmap,
                                        final boolean resetSupp) {
        setImageRotateBitmapResetBase(new CropRotateBitmap(bitmap), resetSupp);
    }

    private void setImageRotateBitmapResetBase(@NonNull final CropRotateBitmap bitmap,
                                               final boolean resetSupp) {
        final int viewWidth = getWidth();

        if (viewWidth <= 0) {
            mOnLayoutRunnable = () -> setImageRotateBitmapResetBase(bitmap, resetSupp);
            return;
        }

        if (bitmap.getBitmap() != null) {
            getProperBaseMatrix(bitmap, mBaseMatrix);
            setImageBitmap(bitmap.getBitmap(), bitmap.getRotation());
        } else {
            mBaseMatrix.reset();
            setImageBitmap(null);
        }

        if (resetSupp) {
            mSuppMatrix.reset();
        }
        setImageMatrix(getImageViewMatrix());
        mMaxZoom = maxZoom();
    }

    /**
     * Center as much as possible in one or both axis. Centering is defined as follows:
     * if the image is scaled down below the view's dimensions then center it (literally).
     * If the image is scaled larger than the view and is translated out of view then
     * translate it back into view (i.e. eliminate black bars).
     */
    @SuppressWarnings("SameParameterValue")
    void center(final boolean horizontal,
                final boolean vertical) {
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        Matrix matrix = getImageViewMatrix();

        RectF rect = new RectF(0, 0, mBitmapDisplayed.getBitmap().getWidth(),
                               mBitmapDisplayed.getBitmap().getHeight());

        matrix.mapRect(rect);

        float height = rect.height();
        float width = rect.width();

        float deltaX = 0;
        float deltaY = 0;

        if (vertical) {
            int viewHeight = getHeight();
            if (height < viewHeight) {
                deltaY = (viewHeight - height) / 2 - rect.top;
            } else if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = getHeight() - rect.bottom;
            }
        }

        if (horizontal) {
            int viewWidth = getWidth();
            if (width < viewWidth) {
                deltaX = (viewWidth - width) / 2 - rect.left;
            } else if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }
        }

        postTranslate(deltaX, deltaY);
        setImageMatrix(getImageViewMatrix());
    }

    private float getValue(@NonNull final Matrix matrix,
                           @SuppressWarnings("SameParameterValue") final int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    // Get the SCALE factor out of the matrix.
    private float getScale(@NonNull final Matrix matrix) {
        return getValue(matrix, Matrix.MSCALE_X);
    }

    float getScale() {
        return getScale(mSuppMatrix);
    }

    /** Setup the base matrix so that the image is centered and scaled properly. */
    private void getProperBaseMatrix(@NonNull final CropRotateBitmap bitmap,
                                     @NonNull final Matrix matrix) {
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float w = bitmap.getWidth();
        float h = bitmap.getHeight();
        // int rotation = bitmap.getRotation();
        matrix.reset();

        // Originally We limited up-scaling to 2x otherwise the result may look bad if
        // it's a small icon. However, we need to crop small thumbnails on huge phones...so
        // there is little choice. We now have no effective limit.
        float widthScale = Math.min(viewWidth / w, SCALE_LIMIT_MAX);
        float heightScale = Math.min(viewHeight / h, SCALE_LIMIT_MAX);
        float scaleFactor = Math.min(widthScale, heightScale);

        matrix.postConcat(bitmap.getRotateMatrix());
        matrix.postScale(scaleFactor, scaleFactor);

        matrix.postTranslate((viewWidth - w * scaleFactor) / 2F,
                             (viewHeight - h * scaleFactor) / 2F);
    }

    /**
     * Combine the base matrix and the supp matrix to make the final matrix.
     */
    @NonNull
    private Matrix getImageViewMatrix() {
        // The final matrix is computed as the concatenation of the base matrix
        // and the supplementary matrix.
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    /**
     * Sets the maximum zoom, which is a SCALE relative to the base matrix. It
     * is calculated to show the image at 400% zoom regardless of screen or
     * image orientation. If in the future we decode the full 3 mega-pixel image,
     * rather than the current 1024x768, this should be changed down to 200%.
     */
    private float maxZoom() {
        if (mBitmapDisplayed.getBitmap() == null) {
            return 1F;
        }

        float fw = (float) mBitmapDisplayed.getWidth() / (float) mThisWidth;
        float fh = (float) mBitmapDisplayed.getHeight() / (float) mThisHeight;
        return Math.max(fw, fh) * 4;
    }

    void zoomTo(float scale,
                final float centerX,
                final float centerY) {
        if (scale > mMaxZoom) {
            scale = mMaxZoom;
        }

        float oldScale = getScale();
        float deltaScale = scale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center(true, true);
    }

    void zoomTo(final float scale,
                final float centerX,
                final float centerY,
                @SuppressWarnings("SameParameterValue") final float durationMs) {
        final float incrementPerMs = (scale - getScale()) / durationMs;
        final float oldScale = getScale();
        final long startTime = System.currentTimeMillis();

        mHandler.post(new Runnable() {
            public void run() {
                long now = System.currentTimeMillis();
                float currentMs = Math.min(durationMs, now - startTime);
                float target = oldScale + (incrementPerMs * currentMs);
                zoomTo(target, centerX, centerY);

                if (currentMs < durationMs) {
                    mHandler.post(this);
                }
            }
        });
    }

    private void zoomTo(@SuppressWarnings("SameParameterValue") final float scale) {
        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        zoomTo(scale, cx, cy);
    }

    void zoomIn() {
        zoomIn(SCALE_RATE);
    }

    private void zoomIn(@SuppressWarnings("SameParameterValue") final float rate) {
        if (getScale() >= mMaxZoom) {
            // Don't let the user zoom into the molecular level.
            return;
        }
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        mSuppMatrix.postScale(rate, rate, cx, cy);
        setImageMatrix(getImageViewMatrix());
    }

    void zoomOut() {
        zoomOut(SCALE_RATE);
    }

    private void zoomOut(@SuppressWarnings("SameParameterValue") final float rate) {
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        // Zoom out to at most 1x.
        Matrix tmp = new Matrix(mSuppMatrix);
        tmp.postScale(1F / rate, 1F / rate, cx, cy);

        if (getScale(tmp) < 1F) {
            mSuppMatrix.setScale(1F, 1F, cx, cy);
        } else {
            mSuppMatrix.postScale(1F / rate, 1F / rate, cx, cy);
        }
        setImageMatrix(getImageViewMatrix());
        center(true, true);
    }

    void postTranslate(final float dx,
                       final float dy) {
        mSuppMatrix.postTranslate(dx, dy);
    }

    void panBy(final float dx,
               final float dy) {
        postTranslate(dx, dy);
        setImageMatrix(getImageViewMatrix());
    }

    /**
     * ImageViewTouchBase will pass a Bitmap to the Recycler if it has finished
     * its use of that Bitmap.
     */
    interface Recycler {

        void recycle(@NonNull Bitmap bm);
    }
}
