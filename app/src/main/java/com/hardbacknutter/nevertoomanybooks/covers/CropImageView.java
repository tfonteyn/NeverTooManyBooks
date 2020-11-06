/*
 * @Copyright 2020 HardBackNutter
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

/*
 * Copyright (C) 2007 The Android Open Source Project
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Custom view to be used in the layout.
 * <p>
 * Depends on / works in conjunction with {@link CropImageActivity}.
 */
public class CropImageView
        extends AppCompatImageView {

    /** 400% zoom regardless of screen or image orientation. */
    private static final int ZOOM_FACTOR = 4;

    private final Handler mHandler = new Handler();

    /** This is the base transformation which is used to show the image initially. */
    private final Matrix mBaseMatrix = new Matrix();
    /**
     * This is the supplementary transformation which reflects what
     * the user has done in terms of zooming and panning.
     */
    private final Matrix mSuppMatrix = new Matrix();
    /**
     * This is the final matrix which is computed as the concatenation
     * of the base matrix and the supplementary matrix.
     */
    private final Matrix mDisplayMatrix = new Matrix();

    /** Temporary buffer used for getting the values out of a matrix. */
    private final float[] mMatrixValues = new float[9];

    /** The bitmap currently being displayed. */
    @Nullable
    private Bitmap mBitmap;

    private int mLeft;
    private int mRight;
    private int mTop;
    private int mBottom;
    private int mWidth = -1;
    private int mHeight = -1;

    @Nullable
    private HighlightView mHighlightView;
    @Nullable
    private HighlightView mMotionHighlightView;
    @HighlightView.MotionEdgeHit
    private int mMotionEdge;

    private float mLastX;
    private float mLastY;
    private float mMaxZoom;

    /**
     * A runnable which will be executed during the {@link #onLayout} stage.
     * It is used to run the {@link #setBitmapMatrix} at that time.
     */
    @Nullable
    private Runnable mOnLayoutRunnable;

    /** Set by the activity when it is saving/quiting. */
    private boolean mNoTouching;

    /**
     * Constructor used by the xml inflater.
     *
     * @param context will be the hosting {@link CropImageActivity}
     */
    public CropImageView(@NonNull final Context context,
                         @Nullable final AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
    }

    /**
     * After activity startup, call this method to setup the view with the original bitmap.
     *
     * @param bitmap to crop
     */
    public void initCropView(@NonNull final Bitmap bitmap) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Flag indicating if by default the crop rectangle should be the whole image.
        final boolean wholeImage = prefs.getBoolean(Prefs.pk_image_cropper_frame_whole, false);

        setBitmapMatrix(bitmap);
        if (getScale() == 1f) {
            center();
        }

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();

        final Rect imageRect = new Rect(0, 0, width, height);

        final int cropWidth;
        final int cropHeight;
        if (wholeImage) {
            cropWidth = width;
            cropHeight = height;
        } else {
            final int dv = Math.min(width, height);
            cropWidth = dv;
            cropHeight = dv;
        }

        final int left = (width - cropWidth) / 2;
        final int top = (height - cropHeight) / 2;

        final RectF cropRect = new RectF(left, top, left + cropWidth, top + cropHeight);

        mHighlightView = new HighlightView(this, imageRect, cropRect);
        invalidate();
    }

    /**
     * Get the finished cropped bitmap. After this call, the Activity should quit (even if the
     * result is invalid).
     *
     * @return cropped bitmap, or {@code null} on any failure.
     */
    @Nullable
    public Bitmap getCroppedBitmap() {
        if (mHighlightView == null || mBitmap == null) {
            return null;
        }

        // Stop responding to user touches.
        mNoTouching = true;

        final Rect cropRect = new Rect((int) mHighlightView.mCropRect.left,
                                       (int) mHighlightView.mCropRect.top,
                                       (int) mHighlightView.mCropRect.right,
                                       (int) mHighlightView.mCropRect.bottom);
        final int width = cropRect.width();
        final int height = cropRect.height();

        final Bitmap croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(croppedBitmap);
        final Rect dstRect = new Rect(0, 0, width, height);
        canvas.drawBitmap(mBitmap, cropRect, dstRect, null);
        return croppedBitmap;
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
        mWidth = right - left;
        mHeight = bottom - top;
        final Runnable r = mOnLayoutRunnable;
        if (r != null) {
            mOnLayoutRunnable = null;
            r.run();
        }
        if (mBitmap != null) {
            setBaseMatrix(mBitmap);
            setImageMatrix(getImageViewMatrix());
        }
        if (mBitmap != null && mHighlightView != null) {
            mHighlightView.mMatrix.set(getImageMatrix());
            mHighlightView.invalidate();
            centerOn(mHighlightView);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent event) {
        if (mNoTouching) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (mHighlightView != null) {
                    @HighlightView.MotionEdgeHit
                    final int edge = mHighlightView.getMotionEdgeHit(event.getX(), event.getY());
                    if (edge != HighlightView.GROW_NONE) {
                        mMotionEdge = edge;
                        mMotionHighlightView = mHighlightView;
                        mLastX = event.getX();
                        mLastY = event.getY();
                        if (edge == HighlightView.MOVE) {
                            mMotionHighlightView.setMode(HighlightView.ModifyMode.Move);
                        } else {
                            mMotionHighlightView.setMode(HighlightView.ModifyMode.Grow);
                        }
                        break;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mMotionHighlightView != null) {
                    centerOn(mMotionHighlightView);
                    mMotionHighlightView.setMode(HighlightView.ModifyMode.None);
                }
                mMotionHighlightView = null;
                center();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mMotionHighlightView != null) {
                    mMotionHighlightView.handleMotion(mMotionEdge,
                                                      event.getX() - mLastX,
                                                      event.getY() - mLastY);
                    mLastX = event.getX();
                    mLastY = event.getY();
                    ensureVisible(mMotionHighlightView);
                }

                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around.
                if (getScale() == 1f) {
                    // put it back to the normalized location.
                    center();
                }
                break;
            }
            default:
                break;
        }

        return true;
    }

    @Override
    public void setImageBitmap(@Nullable final Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        mBitmap = bitmap;
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (mHighlightView != null) {
            mHighlightView.onDraw(canvas);
        }
    }

    @Override
    @CallSuper
    public boolean onKeyDown(final int keyCode,
                             @NonNull final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && getScale() > 1.0f) {
            // If we're zoomed in, pressing BACK shows the entire image.
            zoomTo(1.0f, getWidth() / 2.0f, getHeight() / 2.0f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Get the {@link Matrix#MSCALE_X} factor out of the matrix.
     *
     * @return factor
     */
    private float getScale() {
        mSuppMatrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X];
    }

    /**
     * Update the bitmap, prepare the base matrix according to the size
     * of the bitmap, reset the supplementary matrix and calculate the maximum zoom allowed.
     */
    private void setBitmapMatrix(@NonNull final Bitmap bitmap) {
        // postpone to run during layout pass if the View has not been measured yet.
        if (getWidth() <= 0) {
            mOnLayoutRunnable = () -> setBitmapMatrix(bitmap);
            return;
        }

        setBaseMatrix(bitmap);
        setImageBitmap(bitmap);
        mSuppMatrix.reset();
        setImageMatrix(getImageViewMatrix());

        // Set the maximum zoom, which is relative to the base matrix.
        mMaxZoom = ZOOM_FACTOR * Math.max((float) bitmap.getWidth() / (float) mWidth,
                                          (float) bitmap.getHeight() / (float) mHeight);
    }

    /** Setup the base matrix so that the image is centered and scaled properly. */
    private void setBaseMatrix(@NonNull final Bitmap bitmap) {

        mBaseMatrix.reset();

        final float widthScale = Math.min((float) getWidth() / (float) bitmap.getWidth(),
                                          Float.MAX_VALUE);
        final float heightScale = Math.min((float) getHeight() / (float) bitmap.getHeight(),
                                           Float.MAX_VALUE);
        final float scaleFactor = Math.min(widthScale, heightScale);

        mBaseMatrix.postScale(scaleFactor, scaleFactor);

        final float bitmapWidth = (float) bitmap.getWidth() * scaleFactor;
        final float bitmapHeight = (float) bitmap.getHeight() * scaleFactor;
        mBaseMatrix.postTranslate(((float) getWidth() - bitmapWidth) / 2f,
                                  ((float) getHeight() - bitmapHeight) / 2f);
    }

    /**
     * Combine the base matrix and the supp matrix to make the final matrix.
     */
    @NonNull
    private Matrix getImageViewMatrix() {
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    /**
     * Center as much as possible in one or both axis. Centering is defined as follows:
     * If the image is scaled down below the view's dimensions then center it (literally).
     * If the image is scaled larger than the view and is translated out of view then
     * translate it back into view (i.e. eliminate black bars).
     */
    private void center() {
        if (mBitmap == null) {
            return;
        }

        final RectF rect = new RectF(0, 0,
                                     mBitmap.getWidth(),
                                     mBitmap.getHeight());

        getImageViewMatrix().mapRect(rect);

        final float deltaX;
        final int viewWidth = getWidth();
        final float width = rect.width();
        if (width < viewWidth) {
            deltaX = (viewWidth - width) / 2 - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        } else {
            deltaX = 0;
        }

        final float deltaY;
        final int viewHeight = getHeight();
        final float height = rect.height();
        if (height < viewHeight) {
            deltaY = (viewHeight - height) / 2 - rect.top;
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = getHeight() - rect.bottom;
        } else {
            deltaY = 0;
        }

        postTranslate(deltaX, deltaY);
        setImageMatrix(getImageViewMatrix());
    }

    /**
     * If the cropping rectangle's size changed significantly, change the
     * view's center and scale according to the cropping rectangle.
     */
    private void centerOn(@NonNull final HighlightView hv) {

        float zoom = Math.min((float) getWidth() / (float) hv.mDrawRect.width() * 0.6f,
                              (float) getHeight() / (float) hv.mDrawRect.height() * 0.6f)
                     * getScale();
        zoom = Math.max(1f, zoom);

        if ((Math.abs(zoom - getScale()) / zoom) > 0.1) {
            final float[] coordinates = new float[]{hv.mCropRect.centerX(), hv.mCropRect.centerY()};
            getImageMatrix().mapPoints(coordinates);
            final float duration = 300f;
            final float incrementPerMs = (zoom - getScale()) / duration;
            final float oldScale = getScale();
            final long startTime = System.currentTimeMillis();

            mHandler.post(new Runnable() {
                public void run() {
                    final long now = System.currentTimeMillis();
                    final float currentMs = Math.min(duration, now - startTime);
                    final float target = oldScale + (incrementPerMs * currentMs);
                    zoomTo(target, coordinates[0], coordinates[1]);

                    if (currentMs < duration) {
                        mHandler.post(this);
                    }
                }
            });
        }

        ensureVisible(hv);
    }

    /** Pan the displayed image to make sure the cropping rectangle is visible. */
    private void ensureVisible(@NonNull final HighlightView hv) {
        final Rect r = hv.mDrawRect;

        final int panDeltaX1 = Math.max(0, mLeft - r.left);
        final int panDeltaX2 = Math.min(0, mRight - r.right);
        final int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;

        final int panDeltaY1 = Math.max(0, mTop - r.top);
        final int panDeltaY2 = Math.min(0, mBottom - r.bottom);
        final int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

        if (panDeltaX != 0 || panDeltaY != 0) {
            postTranslate((float) panDeltaX, (float) panDeltaY);
            setImageMatrix(getImageViewMatrix());
        }
    }

    private void zoomTo(final float scale,
                        final float centerX,
                        final float centerY) {
        final float deltaScale = Math.max(scale, mMaxZoom) / getScale();
        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center();
        if (mHighlightView != null) {
            mHighlightView.mMatrix.set(getImageMatrix());
            mHighlightView.invalidate();
        }
    }

    private void postTranslate(final float dx,
                               final float dy) {
        mSuppMatrix.postTranslate(dx, dy);
        if (mHighlightView != null) {
            mHighlightView.mMatrix.postTranslate(dx, dy);
            mHighlightView.invalidate();
        }
    }

    /**
     * Display a highlighted cropping rectangle overlaid on the image.
     * There are two coordinate spaces: image and screen.
     * {@link #computeLayout()} uses a Matrix to map from image to screen space.
     */
    private static class HighlightView {

        static final int MOVE = 1 << 5;
        static final int GROW_NONE = 1;
        private static final int GROW_LEFT_EDGE = 1 << 1;
        private static final int GROW_RIGHT_EDGE = 1 << 2;
        private static final int GROW_TOP_EDGE = 1 << 3;
        private static final int GROW_BOTTOM_EDGE = 1 << 4;

        /** Tolerance +- for determining a 'hit' of one or more of the edges. */
        private static final float HYSTERESIS = 40f;

        /** in image space. */
        final RectF mCropRect;
        final Matrix mMatrix;
        final Rect mImageViewDrawingRect = new Rect();
        final Path path = new Path();
        /** The View displaying the image. */
        private final CropImageView mImageView;
        /*** in image space. */
        private final RectF mImageRect;
        /** Drag handle. */
        private final Drawable mResizeHorizontal;
        /** Drag handle. */
        private final Drawable mResizeVertical;
        private final Paint mFocusPaint = new Paint();
        private final Paint mOutlinePaint = new Paint();
        private final RectF mDrawRectF = new RectF();
        @NonNull
        private ModifyMode mMode = ModifyMode.None;
        /** in screen space. */
        private Rect mDrawRect;

        /**
         * Constructor.
         *
         * @param imageView The View displaying the image.
         * @param imageRect The Rect of that View
         * @param cropRect  The initial crop Rect
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        HighlightView(@NonNull final CropImageView imageView,
                      @NonNull final Rect imageRect,
                      @NonNull final RectF cropRect) {
            mImageView = imageView;
            mMatrix = new Matrix(mImageView.getImageMatrix());
            mImageRect = new RectF(imageRect);
            mCropRect = cropRect;

            mDrawRect = computeLayout();

            final Context context = mImageView.getContext();
            final Resources res = context.getResources();
            final Resources.Theme theme = context.getTheme();

            mResizeHorizontal = res.getDrawable(R.drawable.ic_adjust, theme);
            mResizeVertical = res.getDrawable(R.drawable.ic_adjust, theme);

            mFocusPaint.setColor(res.getColor(R.color.cropper_focus, theme));

            mOutlinePaint.setStrokeWidth(3F);
            mOutlinePaint.setStyle(Paint.Style.STROKE);
            mOutlinePaint.setAntiAlias(true);
            mOutlinePaint.setColor(res.getColor(R.color.cropper_outline_rectangle, theme));
        }

        void onDraw(@NonNull final Canvas canvas) {
            canvas.save();

            mImageView.getDrawingRect(mImageViewDrawingRect);

            path.reset();
            mDrawRectF.set(mDrawRect);
            path.addRect(mDrawRectF, Path.Direction.CW);

            canvas.clipPath(path, Region.Op.DIFFERENCE);
            canvas.drawRect(mImageViewDrawingRect, mFocusPaint);
            canvas.restore();
            canvas.drawPath(path, mOutlinePaint);

            if (mMode == ModifyMode.Grow) {
                int width;
                int height;
                int middle;

                // draw the two horizontal resize handles
                width = mResizeHorizontal.getIntrinsicWidth();
                height = mResizeHorizontal.getIntrinsicHeight();
                middle = mDrawRect.top + ((mDrawRect.bottom - mDrawRect.top) / 2);

                mResizeHorizontal.setBounds(mDrawRect.left - width, middle - height,
                                            mDrawRect.left + width, middle + height);
                mResizeHorizontal.draw(canvas);

                mResizeHorizontal.setBounds(mDrawRect.right - width, middle - height,
                                            mDrawRect.right + width, middle + height);
                mResizeHorizontal.draw(canvas);

                // draw the two vertical resize handles
                width = mResizeVertical.getIntrinsicWidth();
                height = mResizeVertical.getIntrinsicHeight();
                middle = mDrawRect.left + ((mDrawRect.right - mDrawRect.left) / 2);

                mResizeVertical.setBounds(middle - width, mDrawRect.top - height,
                                          middle + width, mDrawRect.top + height);
                mResizeVertical.draw(canvas);

                mResizeVertical.setBounds(middle - width, mDrawRect.bottom - height,
                                          middle + width, mDrawRect.bottom + height);
                mResizeVertical.draw(canvas);
            }
        }

        public void setMode(@NonNull final ModifyMode mode) {
            if (mode != mMode) {
                mMode = mode;
                mImageView.invalidate();
            }
        }

        /** Determines which edges are hit by touching at (x, y). */
        @MotionEdgeHit
        int getMotionEdgeHit(final float x,
                             final float y) {

            final Rect r = computeLayout();

            // vertical: check if the position is between the top and the bottom edge,
            // (with some tolerance). Similar for horizontal.
            final boolean vertical = (y >= r.top - HYSTERESIS) && (y < r.bottom + HYSTERESIS);
            final boolean horizontal = (x >= r.left - HYSTERESIS) && (x < r.right + HYSTERESIS);

            int hitValue = GROW_NONE;

            // Check whether the position is near some edge(s).
            if ((Math.abs(r.left - x) < HYSTERESIS) && vertical) {
                hitValue |= GROW_LEFT_EDGE;
            }
            if ((Math.abs(r.right - x) < HYSTERESIS) && vertical) {
                hitValue |= GROW_RIGHT_EDGE;
            }
            if ((Math.abs(r.top - y) < HYSTERESIS) && horizontal) {
                hitValue |= GROW_TOP_EDGE;
            }
            if ((Math.abs(r.bottom - y) < HYSTERESIS) && horizontal) {
                hitValue |= GROW_BOTTOM_EDGE;
            }

            // Not near any edge but inside the rectangle: move.
            if (hitValue == GROW_NONE && r.contains((int) x, (int) y)) {
                hitValue = MOVE;
            }
            return hitValue;
        }

        /**
         * Handles motion (dx, dy) in screen space.
         *
         * @param edge specifies which edges the user is dragging.
         */
        void handleMotion(@MotionEdgeHit final int edge,
                          float dx,
                          float dy) {

            if (edge == GROW_NONE) {
                return;
            }

            final Rect r = computeLayout();
            if (edge == MOVE) {
                // Convert to image space before sending to moveBy().
                moveBy(dx * (mCropRect.width() / r.width()),
                       dy * (mCropRect.height() / r.height()));
            } else {
                if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0) {
                    dx = 0;
                }

                if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0) {
                    dy = 0;
                }

                // Convert to image space before sending to growBy().
                final float xDelta = dx * (mCropRect.width() / r.width());
                final float yDelta = dy * (mCropRect.height() / r.height());
                growBy((((edge & GROW_LEFT_EDGE) != 0) ? -1 : 1) * xDelta,
                       (((edge & GROW_TOP_EDGE) != 0) ? -1 : 1) * yDelta);
            }
        }

        /** Grows the cropping rectangle by (dx, dy) in image space. */
        private void moveBy(final float dx,
                            final float dy) {

            final Rect rect = new Rect(mDrawRect);

            mCropRect.offset(dx, dy);

            // Put the cropping rectangle inside image rectangle.
            mCropRect.offset(Math.max(0, mImageRect.left - mCropRect.left),
                             Math.max(0, mImageRect.top - mCropRect.top));

            mCropRect.offset(Math.min(0, mImageRect.right - mCropRect.right),
                             Math.min(0, mImageRect.bottom - mCropRect.bottom));

            mDrawRect = computeLayout();
            rect.union(mDrawRect);
            rect.inset(-10, -10);
            mImageView.invalidate();
        }

        /** Grows the cropping rectangle by (dx, dy) in image space. */
        private void growBy(float dx,
                            float dy) {

            // Don't let the cropping rectangle grow too fast.
            // Grow at most half of the difference between the image rectangle and
            // the cropping rectangle.
            final RectF rect = new RectF(mCropRect);
            if (dx > 0F && rect.width() + 2 * dx > mImageRect.width()) {
                // adjustment
                dx = (mImageRect.width() - rect.width()) / 2f;
            }
            if (dy > 0F && rect.height() + 2 * dy > mImageRect.height()) {
                // adjustment
                dy = (mImageRect.height() - rect.height()) / 2f;
            }

            rect.inset(-dx, -dy);

            // Don't let the cropping rectangle shrink too fast.
            final float widthCap = 25f;
            if (rect.width() < widthCap) {
                rect.inset(-(widthCap - rect.width()) / 2F, 0F);
            }
            if (rect.height() < widthCap) {
                rect.inset(0F, -(widthCap - rect.height()) / 2F);
            }

            // Put the cropping rectangle inside the image rectangle.
            if (rect.left < mImageRect.left) {
                rect.offset(mImageRect.left - rect.left, 0F);
            } else if (rect.right > mImageRect.right) {
                rect.offset(-(rect.right - mImageRect.right), 0);
            }

            if (rect.top < mImageRect.top) {
                rect.offset(0F, mImageRect.top - rect.top);
            } else if (rect.bottom > mImageRect.bottom) {
                rect.offset(0F, -(rect.bottom - mImageRect.bottom));
            }

            mCropRect.set(rect);
            mDrawRect = computeLayout();
            mImageView.invalidate();
        }

        /** Maps the cropping rectangle from image space to screen space. */
        @NonNull
        private Rect computeLayout() {
            final RectF r = new RectF(mCropRect.left, mCropRect.top,
                                      mCropRect.right, mCropRect.bottom);
            mMatrix.mapRect(r);
            return new Rect(Math.round(r.left), Math.round(r.top),
                            Math.round(r.right), Math.round(r.bottom));
        }

        void invalidate() {
            mDrawRect = computeLayout();
        }

        enum ModifyMode {
            None, Move, Grow
        }

        @IntDef(flag = true, value = {GROW_NONE,
                                      GROW_LEFT_EDGE, GROW_RIGHT_EDGE,
                                      GROW_TOP_EDGE, GROW_BOTTOM_EDGE,
                                      MOVE})
        @Retention(RetentionPolicy.SOURCE)
        @interface MotionEdgeHit {

        }
    }
}
