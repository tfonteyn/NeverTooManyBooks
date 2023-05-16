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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
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

    /** This is the base transformation which is used to show the image initially. */
    private final Matrix baseMatrix = new Matrix();
    /**
     * This is the supplementary transformation which reflects what
     * the user has done in terms of zooming and panning.
     */
    private final Matrix suppMatrix = new Matrix();
    /**
     * This is the final matrix which is computed as the concatenation
     * of the base matrix and the supplementary matrix.
     */
    private final Matrix displayMatrix = new Matrix();

    /** Temporary buffer used for getting the values out of a matrix. */
    private final float[] matrixValues = new float[9];

    /** The bitmap currently being displayed. */
    @Nullable
    private Bitmap bitmap;

    private int left;
    private int right;
    private int top;
    private int bottom;
    private int width = -1;
    private int height = -1;

    @Nullable
    private HighlightView highlightView;
    @Nullable
    private HighlightView motionHighlightView;
    @HighlightView.MotionEdgeHit
    private int motionEdge;

    private float lastX;
    private float lastY;
    private float maxZoom;

    /**
     * A runnable which will be executed during the {@link #onLayout} stage.
     * It is used to run the {@link #setBitmapMatrix} at that time.
     */
    @Nullable
    private Runnable onLayoutRunnable;

    /** Set by the activity when it is saving/quiting. */
    private boolean noTouching;

    /**
     * Constructor used by the xml inflater.
     *
     * @param context This will be the hosting {@link CropImageActivity}
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
    @UiThread
    void initCropView(@NonNull final Bitmap bitmap) {
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        // Flag indicating if by default the crop rectangle should be the whole image.
        final boolean wholeImage = global.getBoolean(Prefs.pk_image_cropper_frame_whole, false);

        setBitmapMatrix(bitmap);
        //noinspection FloatingPointEquality
        if (getScale() == 1f) {
            center();
        }

        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();

        final Rect imageRect = new Rect(0, 0, bitmapWidth, bitmapHeight);

        final int cropWidth;
        final int cropHeight;
        if (wholeImage) {
            cropWidth = bitmapWidth;
            cropHeight = bitmapHeight;
        } else {
            final int dv = Math.min(bitmapWidth, bitmapHeight);
            cropWidth = dv;
            cropHeight = dv;
        }

        final int cropLeft = (bitmapWidth - cropWidth) / 2;
        final int cropTop = (bitmapHeight - cropHeight) / 2;

        final RectF cropRect = new RectF(cropLeft, cropTop,
                                         cropLeft + cropWidth, cropTop + cropHeight);

        highlightView = new HighlightView(this, imageRect, cropRect);
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
        if (highlightView == null || bitmap == null) {
            return null;
        }

        // Stop responding to user touches.
        noTouching = true;

        final Rect cropRect = new Rect((int) highlightView.cropRect.left,
                                       (int) highlightView.cropRect.top,
                                       (int) highlightView.cropRect.right,
                                       (int) highlightView.cropRect.bottom);
        final int cropWidth = cropRect.width();
        final int cropHeight = cropRect.height();

        final Bitmap croppedBitmap = Bitmap.createBitmap(cropWidth, cropHeight,
                                                         Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(croppedBitmap);
        final Rect dstRect = new Rect(0, 0, cropWidth, cropHeight);
        canvas.drawBitmap(bitmap, cropRect, dstRect, null);
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
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        width = right - left;
        height = bottom - top;
        final Runnable r = onLayoutRunnable;
        if (r != null) {
            onLayoutRunnable = null;
            r.run();
        }
        if (bitmap != null) {
            setBaseMatrix(bitmap);
            setImageMatrix(getImageViewMatrix());
        }
        if (bitmap != null && highlightView != null) {
            highlightView.matrix.set(getImageMatrix());
            highlightView.invalidate();
            centerOn(highlightView);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent event) {
        if (noTouching) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (highlightView != null) {
                    @HighlightView.MotionEdgeHit
                    final int edge = highlightView.getMotionEdgeHit(event.getX(), event.getY());
                    if (edge != HighlightView.GROW_NONE) {
                        motionEdge = edge;
                        motionHighlightView = highlightView;
                        lastX = event.getX();
                        lastY = event.getY();
                        if (edge == HighlightView.MOVE) {
                            motionHighlightView.setMode(HighlightView.ModifyMode.Move);
                        } else {
                            motionHighlightView.setMode(HighlightView.ModifyMode.Grow);
                        }
                        break;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (motionHighlightView != null) {
                    centerOn(motionHighlightView);
                    motionHighlightView.setMode(HighlightView.ModifyMode.None);
                }
                motionHighlightView = null;
                center();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (motionHighlightView != null) {
                    motionHighlightView.handleMotion(motionEdge,
                                                     event.getX() - lastX,
                                                     event.getY() - lastY);
                    lastX = event.getX();
                    lastY = event.getY();
                    ensureVisible(motionHighlightView);
                }

                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around.
                //noinspection FloatingPointEquality
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
        this.bitmap = bitmap;
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (highlightView != null) {
            highlightView.onDraw(canvas);
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
        suppMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    /**
     * Update the bitmap, prepare the base matrix according to the size
     * of the bitmap, reset the supplementary matrix and calculate the maximum zoom allowed.
     */
    private void setBitmapMatrix(@NonNull final Bitmap bitmap) {
        // postpone to run during layout pass if the View has not been measured yet.
        if (getWidth() <= 0) {
            onLayoutRunnable = () -> setBitmapMatrix(bitmap);
            return;
        }

        setBaseMatrix(bitmap);
        setImageBitmap(bitmap);
        suppMatrix.reset();
        setImageMatrix(getImageViewMatrix());

        // Set the maximum zoom, which is relative to the base matrix.
        maxZoom = ZOOM_FACTOR * Math.max((float) bitmap.getWidth() / (float) width,
                                         (float) bitmap.getHeight() / (float) height);
    }

    /** Setup the base matrix so that the image is centered and scaled properly. */
    private void setBaseMatrix(@NonNull final Bitmap bitmap) {

        baseMatrix.reset();

        final float widthScale = Math.min((float) getWidth() / (float) bitmap.getWidth(),
                                          Float.MAX_VALUE);
        final float heightScale = Math.min((float) getHeight() / (float) bitmap.getHeight(),
                                           Float.MAX_VALUE);
        final float scaleFactor = Math.min(widthScale, heightScale);

        baseMatrix.postScale(scaleFactor, scaleFactor);

        final float bitmapWidth = (float) bitmap.getWidth() * scaleFactor;
        final float bitmapHeight = (float) bitmap.getHeight() * scaleFactor;
        baseMatrix.postTranslate(((float) getWidth() - bitmapWidth) / 2f,
                                 ((float) getHeight() - bitmapHeight) / 2f);
    }

    /**
     * Combine the base matrix and the supp matrix to make the final matrix.
     */
    @NonNull
    private Matrix getImageViewMatrix() {
        displayMatrix.set(baseMatrix);
        displayMatrix.postConcat(suppMatrix);
        return displayMatrix;
    }

    /**
     * Center as much as possible in one or both axis. Centering is defined as follows:
     * If the image is scaled down below the view's dimensions then center it (literally).
     * If the image is scaled larger than the view and is translated out of view then
     * translate it back into view (i.e. eliminate black bars).
     */
    private void center() {
        if (bitmap == null) {
            return;
        }

        final RectF rect = new RectF(0, 0,
                                     bitmap.getWidth(),
                                     bitmap.getHeight());

        getImageViewMatrix().mapRect(rect);

        final float deltaX;
        final int viewWidth = getWidth();
        final float rectWidth = rect.width();
        if (rectWidth < viewWidth) {
            deltaX = (viewWidth - rectWidth) / 2 - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        } else {
            deltaX = 0;
        }

        final float deltaY;
        final int viewHeight = getHeight();
        final float rectHeight = rect.height();
        if (rectHeight < viewHeight) {
            deltaY = (viewHeight - rectHeight) / 2 - rect.top;
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

        float zoom = Math.min((float) getWidth() / (float) hv.drawRect.width() * 0.6f,
                              (float) getHeight() / (float) hv.drawRect.height() * 0.6f)
                     * getScale();
        zoom = Math.max(1f, zoom);

        if (Math.abs(zoom - getScale()) / zoom > 0.1) {
            final float[] coordinates = {hv.cropRect.centerX(), hv.cropRect.centerY()};
            getImageMatrix().mapPoints(coordinates);
            final float duration = 300f;
            final float incrementPerMs = (zoom - getScale()) / duration;
            final float oldScale = getScale();
            final long startTime = System.currentTimeMillis();

            getHandler().post(new Runnable() {
                public void run() {
                    final long now = System.currentTimeMillis();
                    final float currentMs = Math.min(duration, now - startTime);
                    final float target = oldScale + incrementPerMs * currentMs;
                    zoomTo(target, coordinates[0], coordinates[1]);

                    if (currentMs < duration) {
                        getHandler().post(this);
                    }
                }
            });
        }

        ensureVisible(hv);
    }

    /** Pan the displayed image to make sure the cropping rectangle is visible. */
    private void ensureVisible(@NonNull final HighlightView hv) {
        final Rect r = hv.drawRect;

        final int panDeltaX1 = Math.max(0, left - r.left);
        final int panDeltaX2 = Math.min(0, right - r.right);
        final int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;

        final int panDeltaY1 = Math.max(0, top - r.top);
        final int panDeltaY2 = Math.min(0, bottom - r.bottom);
        final int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

        if (panDeltaX != 0 || panDeltaY != 0) {
            postTranslate((float) panDeltaX, (float) panDeltaY);
            setImageMatrix(getImageViewMatrix());
        }
    }

    private void zoomTo(final float scale,
                        final float centerX,
                        final float centerY) {
        final float deltaScale = Math.max(scale, maxZoom) / getScale();
        suppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center();
        if (highlightView != null) {
            highlightView.matrix.set(getImageMatrix());
            highlightView.invalidate();
        }
    }

    private void postTranslate(final float dx,
                               final float dy) {
        suppMatrix.postTranslate(dx, dy);
        if (highlightView != null) {
            highlightView.matrix.postTranslate(dx, dy);
            highlightView.invalidate();
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
        @NonNull
        final RectF cropRect;
        @NonNull
        final Matrix matrix;
        final Rect imageViewDrawingRect = new Rect();
        final Path path = new Path();
        /** The View displaying the image. */
        @NonNull
        private final CropImageView imageView;
        /*** in image space. */
        @NonNull
        private final RectF imageRect;
        /** Drag handle. */
        private final Drawable resizeHorizontal;
        /** Drag handle. */
        private final Drawable resizeVertical;
        private final Paint focusPaint = new Paint();
        private final Paint outlinePaint = new Paint();
        private final RectF drawRectF = new RectF();
        /** in screen space. */
        @NonNull
        Rect drawRect;
        @NonNull
        private ModifyMode modifyMode = ModifyMode.None;

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
            this.imageView = imageView;
            matrix = new Matrix(this.imageView.getImageMatrix());
            this.imageRect = new RectF(imageRect);
            this.cropRect = cropRect;

            drawRect = computeLayout();

            final Context context = this.imageView.getContext();
            final Resources res = context.getResources();
            final Resources.Theme theme = context.getTheme();

            resizeHorizontal = res.getDrawable(R.drawable.ic_baseline_adjust_24, theme);
            resizeVertical = res.getDrawable(R.drawable.ic_baseline_adjust_24, theme);

            focusPaint.setColor(res.getColor(R.color.cropper_focus, theme));

            outlinePaint.setStrokeWidth(3F);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setAntiAlias(true);
            outlinePaint.setColor(res.getColor(R.color.cropper_outline_rectangle, theme));
        }

        void onDraw(@NonNull final Canvas canvas) {
            canvas.save();

            imageView.getDrawingRect(imageViewDrawingRect);

            path.reset();
            drawRectF.set(drawRect);
            path.addRect(drawRectF, Path.Direction.CW);

            canvas.clipOutPath(path);
            canvas.drawRect(imageViewDrawingRect, focusPaint);
            canvas.restore();
            canvas.drawPath(path, outlinePaint);

            if (modifyMode == ModifyMode.Grow) {
                int width;
                int height;
                int middle;

                // draw the two horizontal resize handles
                width = resizeHorizontal.getIntrinsicWidth();
                height = resizeHorizontal.getIntrinsicHeight();
                middle = drawRect.top + (drawRect.bottom - drawRect.top) / 2;

                resizeHorizontal.setBounds(drawRect.left - width, middle - height,
                                           drawRect.left + width, middle + height);
                resizeHorizontal.draw(canvas);

                resizeHorizontal.setBounds(drawRect.right - width, middle - height,
                                           drawRect.right + width, middle + height);
                resizeHorizontal.draw(canvas);

                // draw the two vertical resize handles
                width = resizeVertical.getIntrinsicWidth();
                height = resizeVertical.getIntrinsicHeight();
                middle = drawRect.left + (drawRect.right - drawRect.left) / 2;

                resizeVertical.setBounds(middle - width, drawRect.top - height,
                                         middle + width, drawRect.top + height);
                resizeVertical.draw(canvas);

                resizeVertical.setBounds(middle - width, drawRect.bottom - height,
                                         middle + width, drawRect.bottom + height);
                resizeVertical.draw(canvas);
            }
        }

        public void setMode(@NonNull final ModifyMode mode) {
            if (mode != modifyMode) {
                modifyMode = mode;
                imageView.invalidate();
            }
        }

        /** Determines which edges are hit by touching at (x, y). */
        @MotionEdgeHit
        int getMotionEdgeHit(final float x,
                             final float y) {

            final Rect r = computeLayout();

            // vertical: check if the position is between the top and the bottom edge,
            // (with some tolerance). Similar for horizontal.
            final boolean vertical = y >= r.top - HYSTERESIS && y < r.bottom + HYSTERESIS;
            final boolean horizontal = x >= r.left - HYSTERESIS && x < r.right + HYSTERESIS;

            int hitValue = GROW_NONE;

            // Check whether the position is near some edge(s).
            if (Math.abs(r.left - x) < HYSTERESIS && vertical) {
                hitValue |= GROW_LEFT_EDGE;
            }
            if (Math.abs(r.right - x) < HYSTERESIS && vertical) {
                hitValue |= GROW_RIGHT_EDGE;
            }
            if (Math.abs(r.top - y) < HYSTERESIS && horizontal) {
                hitValue |= GROW_TOP_EDGE;
            }
            if (Math.abs(r.bottom - y) < HYSTERESIS && horizontal) {
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
                moveBy(dx * (cropRect.width() / r.width()),
                       dy * (cropRect.height() / r.height()));
            } else {
                if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0) {
                    dx = 0;
                }

                if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0) {
                    dy = 0;
                }

                // Convert to image space before sending to growBy().
                final float xDelta = dx * (cropRect.width() / r.width());
                final float yDelta = dy * (cropRect.height() / r.height());
                growBy(((edge & GROW_LEFT_EDGE) != 0 ? -1 : 1) * xDelta,
                       ((edge & GROW_TOP_EDGE) != 0 ? -1 : 1) * yDelta);
            }
        }

        /** Grows the cropping rectangle by (dx, dy) in image space. */
        private void moveBy(final float dx,
                            final float dy) {

            final Rect rect = new Rect(drawRect);

            cropRect.offset(dx, dy);

            // Put the cropping rectangle inside image rectangle.
            cropRect.offset(Math.max(0, imageRect.left - cropRect.left),
                            Math.max(0, imageRect.top - cropRect.top));

            cropRect.offset(Math.min(0, imageRect.right - cropRect.right),
                            Math.min(0, imageRect.bottom - cropRect.bottom));

            drawRect = computeLayout();
            rect.union(drawRect);
            rect.inset(-10, -10);
            imageView.invalidate();
        }

        /** Grows the cropping rectangle by (dx, dy) in image space. */
        private void growBy(float dx,
                            float dy) {

            // Don't let the cropping rectangle grow too fast.
            // Grow at most half of the difference between the image rectangle and
            // the cropping rectangle.
            final RectF rect = new RectF(cropRect);
            if (dx > 0F && rect.width() + 2 * dx > imageRect.width()) {
                // adjustment
                dx = (imageRect.width() - rect.width()) / 2f;
            }
            if (dy > 0F && rect.height() + 2 * dy > imageRect.height()) {
                // adjustment
                dy = (imageRect.height() - rect.height()) / 2f;
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
            if (rect.left < imageRect.left) {
                rect.offset(imageRect.left - rect.left, 0F);
            } else if (rect.right > imageRect.right) {
                rect.offset(-(rect.right - imageRect.right), 0);
            }

            if (rect.top < imageRect.top) {
                rect.offset(0F, imageRect.top - rect.top);
            } else if (rect.bottom > imageRect.bottom) {
                rect.offset(0F, -(rect.bottom - imageRect.bottom));
            }

            cropRect.set(rect);
            drawRect = computeLayout();
            imageView.invalidate();
        }

        /** Maps the cropping rectangle from image space to screen space. */
        @NonNull
        private Rect computeLayout() {
            final RectF r = new RectF(cropRect.left, cropRect.top,
                                      cropRect.right, cropRect.bottom);
            matrix.mapRect(r);
            return new Rect(Math.round(r.left), Math.round(r.top),
                            Math.round(r.right), Math.round(r.bottom));
        }

        void invalidate() {
            drawRect = computeLayout();
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
