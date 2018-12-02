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

package com.eleybourn.bookcatalogue.cropper;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.view.View;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

/**
 * This class is used by CropImage to display a highlighted cropping rectangle overlaid with
 * the image. There are two coordinate spaces in use. One is image, another is screen.
 * computeLayout() uses mMatrix to map from image space to screen space.
 */
class CropHighlightView {
    @SuppressWarnings("unused")
    private static final String TAG = "HighlightView";

    static final int GROW_NONE = 1;
    private static final int GROW_LEFT_EDGE = (1 << 1);
    private static final int GROW_RIGHT_EDGE = (1 << 2);
    private static final int GROW_TOP_EDGE = (1 << 3);
    private static final int GROW_BOTTOM_EDGE = (1 << 4);
    static final int MOVE = (1 << 5);

    /**  The View displaying the image. */
    @NonNull
    private final View mImageView;

    private final Paint mFocusPaint = new Paint();
    private final Paint mNoFocusPaint = new Paint();
    private final Paint mOutlinePaint = new Paint();
    boolean mIsFocused;
    /** in screen space */
    Rect mDrawRect;
    /** in image space */
    RectF mCropRect;
    Matrix mMatrix;
    private boolean mHidden;
    @NonNull
    private ModifyMode mMode = ModifyMode.None;
    /*** in image space */
    private RectF mImageRect;
    private boolean mMaintainAspectRatio = false;
    private float mInitialAspectRatio;
    private boolean mCircle = false;

    private Drawable mResizeDrawableWidth;
    private Drawable mResizeDrawableHeight;
    private Drawable mResizeDrawableDiagonal;

    @ColorInt
    private int outlinePaintNoFocus;
    @ColorInt
    private int outlinePaintCircle;
    @ColorInt
    private int outlinePaintRectangle;

    CropHighlightView(final @NonNull View imageView) {
        mImageView = imageView;
    }

    private void init() {
        // ApplicationContext so the Theme gets picked up
        Resources resources = BookCatalogueApp.getAppContext().getResources();

        mResizeDrawableWidth = resources.getDrawable(R.drawable.ic_adjust);
        mResizeDrawableHeight = resources.getDrawable(R.drawable.ic_adjust);
        mResizeDrawableDiagonal = resources.getDrawable(R.drawable.ic_crop);

        outlinePaintNoFocus = resources.getColor(R.color.CropHighlightView_outlinePaint_noFocus);
        outlinePaintCircle = resources.getColor(R.color.CropHighlightView_outlinePaint_circle);
        outlinePaintRectangle = resources.getColor(R.color.CropHighlightView_outlinePaint_rectangle);
    }

    boolean hasFocus() {
        return mIsFocused;
    }

    void setFocus(final boolean f) {
        mIsFocused = f;
    }

    public void setHidden(final boolean hidden) {
        mHidden = hidden;
    }

    void draw(final @NonNull Canvas canvas) {
        if (mHidden) {
            return;
        }
        canvas.save();
        Path path = new Path();
        if (!hasFocus()) {
            mOutlinePaint.setColor(outlinePaintNoFocus);
            canvas.drawRect(mDrawRect, mOutlinePaint);
        } else {
            Rect viewDrawingRect = new Rect();
            mImageView.getDrawingRect(viewDrawingRect);
            if (mCircle) {
                float width = mDrawRect.width();
                float height = mDrawRect.height();
                path.addCircle(mDrawRect.left + (width / 2), mDrawRect.top
                        + (height / 2), width / 2, Path.Direction.CW);
                mOutlinePaint.setColor(outlinePaintCircle);
            } else {
                path.addRect(new RectF(mDrawRect), Path.Direction.CW);
                mOutlinePaint.setColor(outlinePaintRectangle);
            }
            canvas.clipPath(path, Region.Op.DIFFERENCE);
            canvas.drawRect(viewDrawingRect, hasFocus() ? mFocusPaint : mNoFocusPaint);

            canvas.restore();
            canvas.drawPath(path, mOutlinePaint);

            if (mMode == ModifyMode.Grow) {
                if (mCircle) {
                    int width = mResizeDrawableDiagonal.getIntrinsicWidth();
                    int height = mResizeDrawableDiagonal.getIntrinsicHeight();

                    int d = (int) Math.round(Math.cos(/* 45deg */Math.PI / 4D)
                            * (mDrawRect.width() / 2D));
                    int x = mDrawRect.left + (mDrawRect.width() / 2) + d
                            - width / 2;
                    int y = mDrawRect.top + (mDrawRect.height() / 2) - d
                            - height / 2;
                    mResizeDrawableDiagonal.setBounds(x, y, x
                            + mResizeDrawableDiagonal.getIntrinsicWidth(), y
                            + mResizeDrawableDiagonal.getIntrinsicHeight());
                    mResizeDrawableDiagonal.draw(canvas);
                } else {
                    int left = mDrawRect.left + 1;
                    int right = mDrawRect.right + 1;
                    int top = mDrawRect.top + 4;
                    int bottom = mDrawRect.bottom + 3;

                    int widthWidth = mResizeDrawableWidth.getIntrinsicWidth() / 2;
                    int widthHeight = mResizeDrawableWidth.getIntrinsicHeight() / 2;
                    int heightHeight = mResizeDrawableHeight
                            .getIntrinsicHeight() / 2;
                    int heightWidth = mResizeDrawableHeight.getIntrinsicWidth() / 2;

                    int xMiddle = mDrawRect.left
                            + ((mDrawRect.right - mDrawRect.left) / 2);
                    int yMiddle = mDrawRect.top
                            + ((mDrawRect.bottom - mDrawRect.top) / 2);

                    mResizeDrawableWidth.setBounds(left - widthWidth, yMiddle
                            - widthHeight, left + widthWidth, yMiddle
                            + widthHeight);
                    mResizeDrawableWidth.draw(canvas);

                    mResizeDrawableWidth.setBounds(right - widthWidth, yMiddle
                            - widthHeight, right + widthWidth, yMiddle
                            + widthHeight);
                    mResizeDrawableWidth.draw(canvas);

                    mResizeDrawableHeight.setBounds(xMiddle - heightWidth, top
                            - heightHeight, xMiddle + heightWidth, top
                            + heightHeight);
                    mResizeDrawableHeight.draw(canvas);

                    mResizeDrawableHeight.setBounds(xMiddle - heightWidth,
                            bottom - heightHeight, xMiddle + heightWidth,
                            bottom + heightHeight);
                    mResizeDrawableHeight.draw(canvas);
                }
            }
        }
    }

    @NonNull
    public ModifyMode getMode() {
        return mMode;
    }

    public void setMode(final @NonNull ModifyMode mode) {
        if (mode != mMode) {
            mMode = mode;
            mImageView.invalidate();
        }
    }

    /** Determines which edges are hit by touching at (x, y). */
    int getHit(final float x, final float y) {
        Rect r = computeLayout();
        final float hysteresis = 20F;
        int hitValue = GROW_NONE;

        if (mCircle) {
            float distX = x - r.centerX();
            float distY = y - r.centerY();
            int distanceFromCenter = (int) Math.sqrt(distX * distX + distY * distY);
            int radius = mDrawRect.width() / 2;
            int delta = distanceFromCenter - radius;
            if (Math.abs(delta) <= hysteresis) {
                if (Math.abs(distY) > Math.abs(distX)) {
                    if (distY < 0) {
                        hitValue = GROW_TOP_EDGE;
                    } else {
                        hitValue = GROW_BOTTOM_EDGE;
                    }
                } else {
                    if (distX < 0) {
                        hitValue = GROW_LEFT_EDGE;
                    } else {
                        hitValue = GROW_RIGHT_EDGE;
                    }
                }
            } else if (distanceFromCenter < radius) {
                hitValue = MOVE;
            } else {
                hitValue = GROW_NONE;
            }
        } else {
            // verticalCheck makes sure the position is between the top and
            // the bottom edge (with some tolerance). Similar for horizontalCheck.
            boolean verticalCheck = (y >= r.top - hysteresis)
                    && (y < r.bottom + hysteresis);
            boolean horizontalCheck = (x >= r.left - hysteresis)
                    && (x < r.right + hysteresis);

            // Check whether the position is near some edge(s).
            if ((Math.abs(r.left - x) < hysteresis) && verticalCheck) {
                hitValue |= GROW_LEFT_EDGE;
            }
            if ((Math.abs(r.right - x) < hysteresis) && verticalCheck) {
                hitValue |= GROW_RIGHT_EDGE;
            }
            if ((Math.abs(r.top - y) < hysteresis) && horizontalCheck) {
                hitValue |= GROW_TOP_EDGE;
            }
            if ((Math.abs(r.bottom - y) < hysteresis) && horizontalCheck) {
                hitValue |= GROW_BOTTOM_EDGE;
            }

            // Not near any edge but inside the rectangle: move.
            if (hitValue == GROW_NONE && r.contains((int) x, (int) y)) {
                hitValue = MOVE;
            }
        }
        return hitValue;
    }

    /**
     * Handles motion (dx, dy) in screen space.
     * The "edge" parameter specifies which edges the user is dragging.
     */
    void handleMotion(final int edge, float dx, float dy) {
        Rect r = computeLayout();
        switch (edge) {
            case GROW_NONE:
                return;
            case MOVE:
                // Convert to image space before sending to moveBy().
                moveBy(dx * (mCropRect.width() / r.width()),
                        dy * (mCropRect.height() / r.height()));
                break;
            default:
                if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0) {
                    dx = 0;
                }

                if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0) {
                    dy = 0;
                }

                // Convert to image space before sending to growBy().
                float xDelta = dx * (mCropRect.width() / r.width());
                float yDelta = dy * (mCropRect.height() / r.height());
                growBy((((edge & GROW_LEFT_EDGE) != 0) ? -1 : 1) * xDelta,
                        (((edge & GROW_TOP_EDGE) != 0) ? -1 : 1) * yDelta);
                break;
        }
    }

    /** Grows the cropping rectangle by (dx, dy) in image space. */
    private void moveBy(final float dx, final float dy) {
        Rect rect = new Rect(mDrawRect);

        mCropRect.offset(dx, dy);

        // Put the cropping rectangle inside image rectangle.
        mCropRect.offset(Math.max(0, mImageRect.left - mCropRect.left),
                Math.max(0, mImageRect.top - mCropRect.top));

        mCropRect.offset(Math.min(0, mImageRect.right - mCropRect.right),
                Math.min(0, mImageRect.bottom - mCropRect.bottom));

        mDrawRect = computeLayout();
        rect.union(mDrawRect);
        rect.inset(-10, -10);
        mImageView.invalidate(rect);
    }

    /** Grows the cropping rectangle by (dx, dy) in image space. */
    private void growBy(float dx, float dy) {
        if (mMaintainAspectRatio) {
            if (dx != 0) {
                dy = dx / mInitialAspectRatio;
            } else if (dy != 0) {
                dx = dy * mInitialAspectRatio;
            }
        }

        // Don't let the cropping rectangle grow too fast.
        // Grow at most half of the difference between the image rectangle and
        // the cropping rectangle.
        RectF r = new RectF(mCropRect);
        if (dx > 0F && r.width() + 2 * dx > mImageRect.width()) {
            dx = (mImageRect.width() - r.width()) / 2F; // adjustment
            if (mMaintainAspectRatio) {
                dy = dx / mInitialAspectRatio;
            }
        }
        if (dy > 0F && r.height() + 2 * dy > mImageRect.height()) {
            dy = (mImageRect.height() - r.height()) / 2F; // adjustment
            if (mMaintainAspectRatio) {
                dx = dy * mInitialAspectRatio;
            }
        }

        r.inset(-dx, -dy);

        // Don't let the cropping rectangle shrink too fast.
        final float widthCap = 25F;
        if (r.width() < widthCap) {
            r.inset(-(widthCap - r.width()) / 2F, 0F);
        }
        float heightCap = mMaintainAspectRatio ? (widthCap / mInitialAspectRatio)
                : widthCap;
        if (r.height() < heightCap) {
            r.inset(0F, -(heightCap - r.height()) / 2F);
        }

        // Put the cropping rectangle inside the image rectangle.
        if (r.left < mImageRect.left) {
            r.offset(mImageRect.left - r.left, 0F);
        } else if (r.right > mImageRect.right) {
            r.offset(-(r.right - mImageRect.right), 0);
        }
        if (r.top < mImageRect.top) {
            r.offset(0F, mImageRect.top - r.top);
        } else if (r.bottom > mImageRect.bottom) {
            r.offset(0F, -(r.bottom - mImageRect.bottom));
        }

        mCropRect.set(r);
        mDrawRect = computeLayout();
        mImageView.invalidate();
    }

    /** Returns the cropping rectangle in image space. */
    @NonNull
    Rect getCropRect() {
        return new Rect((int) mCropRect.left, (int) mCropRect.top,
                (int) mCropRect.right, (int) mCropRect.bottom);
    }

    /** Maps the cropping rectangle from image space to screen space. */
    private Rect computeLayout() {
        RectF r = new RectF(mCropRect.left, mCropRect.top, mCropRect.right,
                mCropRect.bottom);
        mMatrix.mapRect(r);
        return new Rect(Math.round(r.left), Math.round(r.top),
                Math.round(r.right), Math.round(r.bottom));
    }

    void invalidate() {
        mDrawRect = computeLayout();
    }

    public void setup(final @NonNull Matrix m,
                      final @NonNull Rect imageRect,
                      final @NonNull RectF cropRect,
                      final boolean circle,
                      boolean maintainAspectRatio) {
        if (circle) {
            maintainAspectRatio = true;
        }
        mMatrix = new Matrix(m);

        mCropRect = cropRect;
        mImageRect = new RectF(imageRect);
        mMaintainAspectRatio = maintainAspectRatio;
        mCircle = circle;

        mInitialAspectRatio = mCropRect.width() / mCropRect.height();
        mDrawRect = computeLayout();

        mFocusPaint.setARGB(125, 50, 50, 50);
        mNoFocusPaint.setARGB(125, 50, 50, 50);
        mOutlinePaint.setStrokeWidth(3F);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setAntiAlias(true);

        mMode = ModifyMode.None;
        init();
    }
    enum ModifyMode {
        None, Move, Grow
    }
}