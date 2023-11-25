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

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Custom view to be used in the layout.
 * <p>
 * Depends on / works in conjunction with {@link CropImageActivity}.
 */
public class CropImageView
        extends AppCompatImageView {

    private static final String TAG = "CropImageView";

    /** 400% zoom regardless of screen or image orientation. */
    private static final int ZOOM_FACTOR = 4;
    private static final int ZOOM_DURATION_IN_MILLIS = 300;

    /**
     * Factor by which a change in the cropping rectangle's size is
     * considered significantly enough to rescale it.
     */
    private static final float TEN_PERCENT = 0.1f;
    private static final float SIXTY_PERCENT = 0.6f;

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

    /**
     * Coordinates from {@link #onLayout}.
     * Stored to use for {@link #ensureVisible(HighlightView)}.
     */
    private final Rect currentLayoutRect = new Rect();

    @NonNull
    private final ScaleGestureDetector scaleGestureDetector;

    /** The bitmap currently being displayed. */
    @Nullable
    private Bitmap bitmap;

    /** The highlighted view overlay representing the cropped image. */
    @Nullable
    private HighlightView highlightView;
    /** Tracks the view during a motion in progress. */
    @Nullable
    private HighlightView motionHighlightView;

    private enum InScaleGestureStep {
        NotActive,
        Begin,
        InProgress,
        Ended
    }

    @NonNull
    private InScaleGestureStep inScaleGestureStep = InScaleGestureStep.NotActive;

    @SuppressWarnings("FieldCanBeLocal")
    private final ScaleGestureDetector.SimpleOnScaleGestureListener onScaleGestureListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                @Override
                public boolean onScaleBegin(@NonNull final ScaleGestureDetector detector) {
                    inScaleGestureStep = InScaleGestureStep.Begin;
                    motionHighlightView = highlightView;
                    motionHighlightView.setInMotion(InMotion.RESIZE);
                    return true;
                }

                @Override
                public boolean onScale(@NonNull final ScaleGestureDetector detector) {
                    inScaleGestureStep = InScaleGestureStep.InProgress;
                    final float dx = detector.getCurrentSpanX() - detector.getPreviousSpanX();
                    final float dy = detector.getCurrentSpanY() - detector.getPreviousSpanY();
                    motionHighlightView.resizeBy(dx, dy);
                    return true;
                }

                @Override
                public void onScaleEnd(@NonNull final ScaleGestureDetector detector) {
                    inScaleGestureStep = InScaleGestureStep.Ended;
                    motionHighlightView.setInMotion(InMotion.NO);
                    motionHighlightView = null;
                }
            };
    /**
     * Tracks the {@code x} coordinate of the current MotionEvent. Set on {@code ACTION_DOWN}
     * and updated during {@code ACTION_MOVE}.
     */
    private float lastX;

    /**
     * Tracks the {@code y} coordinate of the current MotionEvent. Set on {@code ACTION_DOWN}
     * and updated during {@code ACTION_MOVE}.
     */
    private float lastY;

    /** The maximum zoom scale as computed when setting the Bitmap. */
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
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public CropImageView(@NonNull final Context context,
                         @Nullable final AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleGestureDetector = new ScaleGestureDetector(context, onScaleGestureListener);
    }

    /**
     * Setup the view with the given bitmap.
     *
     * @param bitmap to edit
     */
    @UiThread
    void setInitialBitmap(@NonNull final Bitmap bitmap) {
        setBitmapMatrix(bitmap);
        if (isFullSize()) {
            centerBitmap();
        }

        highlightView = new HighlightView(this, bitmap.getWidth(), bitmap.getHeight());
        invalidate();
    }

    /**
     * Revert the view to the initial bitmap.
     */
    @UiThread
    void resetBitmap() {
        if (bitmap != null) {
            setInitialBitmap(bitmap);
        }
    }

    /**
     * Get the finished cropped bitmap. After this call, the Activity should quit
     * (even if the result is invalid).
     *
     * @return cropped bitmap, or {@code null} on any failure.
     */
    @SuppressWarnings("WeakerAccess")
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

        currentLayoutRect.set(left, top, right, bottom);

        final Runnable r = onLayoutRunnable;
        if (r != null) {
            onLayoutRunnable = null;
            r.run();
        }

        if (bitmap != null) {
            setBaseMatrix(bitmap);
            setImageMatrix(computeImageMatrix());

            if (highlightView != null) {
                highlightView.setMatrix(getImageMatrix());
                recenterAndScale(highlightView);
                ensureVisible(highlightView);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent event) {
        if (noTouching) {
            return false;
        }

        // ALWAYS give it ALL events! This seems to be crucial to the detector working properly.
        scaleGestureDetector.onTouchEvent(event);

        // If the detector has just gone through "onScaleEnd", reset it and we're done here.
        if (inScaleGestureStep == InScaleGestureStep.Ended) {
            inScaleGestureStep = InScaleGestureStep.NotActive;
            return true;
        }

        // If the detector is handling the events, we're done here.
        if (inScaleGestureStep != InScaleGestureStep.NotActive) {
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (highlightView != null) {
                    final int motionAction = InMotion
                            .determineInMotion(event, highlightView.computeLayout());
                    if (motionAction != InMotion.NO) {
                        motionHighlightView = highlightView;
                        motionHighlightView.setInMotion(motionAction);
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (motionHighlightView != null) {
                    motionHighlightView.handleMotion(event.getX() - lastX,
                                                     event.getY() - lastY);
                    // update for next pass
                    lastX = event.getX();
                    lastY = event.getY();
                    ensureVisible(motionHighlightView);
                }

                // if we're not zoomed in, then there's no point in even allowing
                // the user to move the image around.
                if (isFullSize()) {
                    centerBitmap();
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (motionHighlightView != null) {
                    recenterAndScale(motionHighlightView);
                    ensureVisible(motionHighlightView);
                    motionHighlightView.setInMotion(InMotion.NO);
                }
                motionHighlightView = null;
                centerBitmap();
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
     * Check if the image is full-size.
     *
     * @return {@code true} if it is
     */
    private boolean isFullSize() {
        return Math.abs(getScale() - 1.0f) < 0.01f;
    }

    /**
     * Update the bitmap, prepare the base matrix according to the size
     * of the bitmap, reset the supplementary matrix and calculate the maximum zoom allowed.
     *
     * @param bitmap to use
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
        setImageMatrix(computeImageMatrix());

        // Set the maximum zoom, which is relative to the base matrix.
        maxZoom = ZOOM_FACTOR * Math.max((float) bitmap.getWidth()
                                         / (float) currentLayoutRect.width(),
                                         (float) bitmap.getHeight()
                                         / (float) currentLayoutRect.height());
    }

    /**
     * Setup the base matrix so that the image is centered and scaled properly.
     *
     * @param bitmap to use
     */
    private void setBaseMatrix(@NonNull final Bitmap bitmap) {

        baseMatrix.reset();

        // scaling
        final float widthScale = Math.min((float) getWidth() / (float) bitmap.getWidth(),
                                          Float.MAX_VALUE);
        final float heightScale = Math.min((float) getHeight() / (float) bitmap.getHeight(),
                                           Float.MAX_VALUE);
        final float scaleFactor = Math.min(widthScale, heightScale);
        baseMatrix.postScale(scaleFactor, scaleFactor);

        // centering
        final float bitmapWidth = (float) bitmap.getWidth() * scaleFactor;
        final float bitmapHeight = (float) bitmap.getHeight() * scaleFactor;
        baseMatrix.postTranslate(((float) getWidth() - bitmapWidth) / 2.0f,
                                 ((float) getHeight() - bitmapHeight) / 2.0f);
    }

    /**
     * Combine the {@link #baseMatrix} and the {@link #suppMatrix}
     * to make the final {@link #displayMatrix}.
     *
     * @return the {@link #displayMatrix} to display
     */
    @NonNull
    private Matrix computeImageMatrix() {
        displayMatrix.set(baseMatrix);
        displayMatrix.postConcat(suppMatrix);
        return displayMatrix;
    }

    private void postTranslate(final float dx,
                               final float dy) {
        suppMatrix.postTranslate(dx, dy);
        if (highlightView != null) {
            highlightView.postTranslate(dx, dy);
        }
    }


    /**
     * Center as much as possible in one or both axis. Centering is defined as follows:
     * <ul>
     *     <li>If the image is scaled down below the view's dimensions then center it (literally).
     *     </li>
     *     <li>If the image is scaled larger than the view and is translated out of view then
     *         translate it back into view (i.e. eliminate black bars).</li>
     * </ul>
     */
    private void centerBitmap() {
        if (bitmap == null) {
            return;
        }

        final RectF bitmapRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());

        computeImageMatrix().mapRect(bitmapRect);

        final float dx;
        final int viewWidth = getWidth();
        final float bitmapWidth = bitmapRect.width();
        if (bitmapWidth < viewWidth) {
            dx = (viewWidth - bitmapWidth) / 2 - bitmapRect.left;
        } else if (bitmapRect.left > 0) {
            dx = -bitmapRect.left;
        } else if (bitmapRect.right < viewWidth) {
            dx = viewWidth - bitmapRect.right;
        } else {
            dx = 0;
        }

        final float dy;
        final int viewHeight = getHeight();
        final float bitmapHeight = bitmapRect.height();
        if (bitmapHeight < viewHeight) {
            dy = (viewHeight - bitmapHeight) / 2 - bitmapRect.top;
        } else if (bitmapRect.top > 0) {
            dy = -bitmapRect.top;
        } else if (bitmapRect.bottom < viewHeight) {
            dy = viewHeight - bitmapRect.bottom;
        } else {
            dy = 0;
        }

        postTranslate(dx, dy);
        setImageMatrix(computeImageMatrix());
    }

    /**
     * If the cropping rectangle's size changed significantly, change the
     * view's center and scale according to the cropping rectangle.
     *
     * @param highlightView to process
     */
    private void recenterAndScale(@NonNull final HighlightView highlightView) {

        final float scaleFactor =
                Math.max(1f,
                         Math.min((float) getWidth() / (float) highlightView.drawRect.width(),
                                  (float) getHeight() / (float) highlightView.drawRect.height())
                         * SIXTY_PERCENT
                         * getScale());

        // If more than 10% difference then recenter/scale.
        if (Math.abs(scaleFactor - getScale()) / scaleFactor > TEN_PERCENT) {
            final float[] coordinates = {
                    highlightView.cropRect.centerX(),
                    highlightView.cropRect.centerY()};
            getImageMatrix().mapPoints(coordinates);

            // Scale increment per millisecond.
            final float incrementPerMs = (scaleFactor - getScale()) / ZOOM_DURATION_IN_MILLIS;
            final long startTime = System.currentTimeMillis();

            getHandler().post(new Runnable() {
                public void run() {
                    final long now = System.currentTimeMillis();
                    final float currentMs = Math.min(ZOOM_DURATION_IN_MILLIS, now - startTime);
                    final float targetScale = getScale() + incrementPerMs * currentMs;
                    zoom(targetScale, coordinates[0], coordinates[1]);

                    if (currentMs < ZOOM_DURATION_IN_MILLIS) {
                        getHandler().post(this);
                    }
                }

                /**
                 * Zoom to the given scale, centering on the given x/y coordinates.
                 *
                 * @param scale   for the zoom
                 * @param centerX coordinate
                 * @param centerY coordinate
                 */
                private void zoom(final float scale,
                                  final float centerX,
                                  final float centerY) {
                    final float scaleFactor = Math.max(scale, maxZoom) / getScale();
                    suppMatrix.postScale(scaleFactor, scaleFactor, centerX, centerY);
                    setImageMatrix(computeImageMatrix());
                    centerBitmap();
                    highlightView.setMatrix(getImageMatrix());
                }
            });
        }
    }

    /**
     * Pan the displayed image to make sure the given {@link HighlightView} is visible.
     *
     * @param highlightView to be forced visible
     */
    private void ensureVisible(@NonNull final HighlightView highlightView) {
        final Rect rect = highlightView.drawRect;

        final int panDxLeft = Math.max(0, currentLayoutRect.left - rect.left);
        final int panDxRight = Math.min(0, currentLayoutRect.right - rect.right);
        final int panDeltaX = panDxLeft != 0 ? panDxLeft : panDxRight;

        final int panDxTop = Math.max(0, currentLayoutRect.top - rect.top);
        final int panDxBottom = Math.min(0, currentLayoutRect.bottom - rect.bottom);
        final int panDeltaY = panDxTop != 0 ? panDxTop : panDxBottom;

        if (panDeltaX != 0 || panDeltaY != 0) {
            postTranslate((float) panDeltaX, (float) panDeltaY);
            setImageMatrix(computeImageMatrix());
        }
    }


    @IntDef(flag = true, value = {
            InMotion.NO,
            InMotion.MOVE,
            InMotion.RESIZE_LEFT_EDGE, InMotion.RESIZE_RIGHT_EDGE,
            InMotion.RESIZE_TOP_EDGE, InMotion.RESIZE_BOTTOM_EDGE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface MotionAction {

    }

    private static final class InMotion {

        static final int NO = 0;

        /** Mutually exclusive with ALL other flags. */
        static final int MOVE = 1;

        static final int RESIZE_LEFT_EDGE = 1 << 1;
        static final int RESIZE_RIGHT_EDGE = 1 << 2;
        static final int RESIZE_HORIZONTAL = RESIZE_LEFT_EDGE | RESIZE_RIGHT_EDGE;
        static final int RESIZE_TOP_EDGE = 1 << 3;
        static final int RESIZE_BOTTOM_EDGE = 1 << 4;
        static final int RESIZE_VERTICAL = RESIZE_TOP_EDGE | RESIZE_BOTTOM_EDGE;
        static final int RESIZE = RESIZE_HORIZONTAL | RESIZE_VERTICAL;


        /** Tolerance +- for determining a 'hit' of one or more of the edges. */
        private static final float HYSTERESIS = 40f;

        // DEBUG
        @NonNull
        static String toString(@MotionAction final int action) {
            final StringJoiner sj = new StringJoiner("|");

            if ((action & RESIZE_LEFT_EDGE) != 0) {
                sj.add("RESIZE_LEFT_EDGE");
            }
            if ((action & RESIZE_RIGHT_EDGE) != 0) {
                sj.add("RESIZE_RIGHT_EDGE");
            }
            if ((action & RESIZE_TOP_EDGE) != 0) {
                sj.add("RESIZE_TOP_EDGE");
            }
            if ((action & RESIZE_BOTTOM_EDGE) != 0) {
                sj.add("RESIZE_BOTTOM_EDGE");
            }
            if ((action & MOVE) != 0) {
                sj.add("MOVE");
            }
            return sj.toString();
        }

        /**
         * Determines whether the given event is a resize initiated from
         * one or more edges, or if it was a move.
         *
         * @param event to analyse
         * @param rect  The rectangle around the highlighted area,
         *              i.e. the current cropped selection
         *
         * @return the {@link MotionAction} code
         */
        @MotionAction
        private static int determineInMotion(@NonNull final MotionEvent event,
                                             @NonNull final Rect rect) {

            final float x = event.getX();
            final float y = event.getY();

            // vertical: check if the position is between the top and the bottom edge,
            // (with some tolerance). Similar for horizontal.
            final boolean vertical = y >= rect.top - HYSTERESIS && y < rect.bottom + HYSTERESIS;
            final boolean horizontal = x >= rect.left - HYSTERESIS && x < rect.right + HYSTERESIS;

            @MotionAction
            int action = NO;

            // Check whether the position is near one or more of the rectangle edges.
            if (Math.abs(rect.left - x) < HYSTERESIS && vertical) {
                action |= RESIZE_LEFT_EDGE;
            }
            if (Math.abs(rect.right - x) < HYSTERESIS && vertical) {
                action |= RESIZE_RIGHT_EDGE;
            }
            if (Math.abs(rect.top - y) < HYSTERESIS && horizontal) {
                action |= RESIZE_TOP_EDGE;
            }
            if (Math.abs(rect.bottom - y) < HYSTERESIS && horizontal) {
                action |= RESIZE_BOTTOM_EDGE;
            }
            if (action != NO) {
                // It's a resize
                return action;
            }

            // Otherwise check if it was inside the rectangle
            if (rect.contains((int) x, (int) y)) {
                // then it's a move
                return MOVE;
            }

            // Outside of the highlighted rectangle
            return NO;
        }
    }

    /**
     * Displays a highlighted cropping rectangle overlaid on the image.
     * There are two coordinate spaces: image and screen.
     * {@link #computeLayout()} uses a Matrix to map from image to screen space.
     */
    private static class HighlightView {

        /**
         * Resize at most half of the difference between
         * the image rectangle and the cropping rectangle.
         */
        private static final float MAX_RESIZE_FACTOR = 0.5f;

        /**
         * The minimum width an height of the cropping rectangle.
         * Using 26 means the two handle-icons will just not touch.
         */
        private static final float CROP_RECT_MIN_SIZE = 26.0f;

        /** The cropping rectangle border thickness. */
        private static final float STROKE_WIDTH = 3.0f;
        /** in image space. */
        @NonNull
        final RectF cropRect;
        /** in screen space. */
        @NonNull
        final Rect drawRect;
        @NonNull
        private final Matrix matrix;
        private final Rect imageViewDrawingRect = new Rect();
        private final Path path = new Path();
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

        /**
         * Bitmask value.
         * Bits 0..3 indicate a combination of resizing by touch/moving close to one or more edges.
         * Bit 4 indicates it's a move by touch/moving the inside of the rectangle.
         */
        @MotionAction
        private int inMotion = InMotion.NO;

        /**
         * Constructor.
         *
         * @param imageView The View displaying the image.
         * @param width     of the image displayed
         * @param height    of the image displayed
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        HighlightView(@NonNull final CropImageView imageView,
                      final int width,
                      final int height) {

            this.imageView = imageView;
            this.imageRect = new RectF(0, 0, width, height);
            this.cropRect = new RectF(0, 0, width, height);

            this.matrix = new Matrix(this.imageView.getImageMatrix());
            this.drawRect = computeLayout();

            final Context context = this.imageView.getContext();
            final Resources res = context.getResources();
            final Resources.Theme theme = context.getTheme();

            resizeHorizontal = res.getDrawable(R.drawable.ic_baseline_adjust_24, theme);
            resizeVertical = res.getDrawable(R.drawable.ic_baseline_adjust_24, theme);

            final int focusColor = AttrUtils.getColorInt(
                    context, com.google.android.material.R.attr.colorSurface);
            final int outlineColor = AttrUtils.getColorInt(
                    context, com.google.android.material.R.attr.colorOnSurface);
            focusPaint.setColor(focusColor);

            outlinePaint.setStrokeWidth(STROKE_WIDTH);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setAntiAlias(true);
            outlinePaint.setColor(outlineColor);
        }

        void setMatrix(@NonNull final Matrix src) {
            matrix.set(src);
            drawRect.set(computeLayout());
        }

        void postTranslate(final float dx,
                           final float dy) {
            matrix.postTranslate(dx, dy);
            drawRect.set(computeLayout());
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

            // If we're currently resizing, also draw the edge-grabber icon/areas.
            if (inMotion != InMotion.NO && inMotion != InMotion.MOVE) {
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

        /**
         * Called from a {@code MotionEvent.ACTION_DOWN} to set the required/current action.
         * Called from a {@code MotionEvent.ACTION_UP} to reset to {@link InMotion#NO}.
         * <p>
         * This is the action to be acted upon when a {@code MotionEvent.ACTION_MOVE} happens
         * and {@link #handleMotion(float, float)} is called.
         *
         * @param inMotion to set
         */
        void setInMotion(@MotionAction final int inMotion) {
            // only invalidate the view if the mode is actually changing.
            if (this.inMotion != inMotion) {
                this.inMotion = inMotion;
                imageView.invalidate();
            }
        }

        /**
         * Handles motion (dx, dy) in screen space.
         *
         * @param dx the delta in the x direction
         * @param dy the delta in the y direction
         */
        void handleMotion(final float dx,
                          final float dy) {

            if (inMotion == InMotion.NO) {
                return;
            }

            final Rect rect = computeLayout();
            if (inMotion == InMotion.MOVE) {
                // Convert to image space before doing the actual move
                final float dxImage = dx * (cropRect.width() / rect.width());
                final float dyImage = dy * (cropRect.height() / rect.height());

                moveBy(dxImage, dyImage);

            } else {
                // Convert to image space before doing the actual resizing
                final float dxImage;
                final float dyImage;

                if ((InMotion.RESIZE_HORIZONTAL & inMotion) != 0) {
                    dxImage = dx * (cropRect.width() / rect.width())
                              * ((inMotion & InMotion.RESIZE_LEFT_EDGE) != 0 ? -1 : 1);
                } else {
                    dxImage = 0f;
                }

                if ((InMotion.RESIZE_VERTICAL & inMotion) != 0) {
                    dyImage = dy * (cropRect.height() / rect.height())
                              * ((inMotion & InMotion.RESIZE_TOP_EDGE) != 0 ? -1 : 1);
                } else {
                    dyImage = 0f;
                }

                resizeBy(dxImage, dyImage);
            }
        }

        /**
         * Move the cropping rectangle by (dx, dy) in image space.
         *
         * @param dx the delta in the x direction
         * @param dy the delta in the y direction
         */
        private void moveBy(final float dx,
                            final float dy) {

            final Rect rect = new Rect(drawRect);

            cropRect.offset(dx, dy);

            // Put the cropping rectangle inside image rectangle.
            cropRect.offset(Math.max(0, imageRect.left - cropRect.left),
                            Math.max(0, imageRect.top - cropRect.top));

            cropRect.offset(Math.min(0, imageRect.right - cropRect.right),
                            Math.min(0, imageRect.bottom - cropRect.bottom));

            drawRect.set(computeLayout());
            rect.union(drawRect);
            rect.inset(-10, -10);

            imageView.invalidate();
        }

        /**
         * Resize the cropping rectangle by (dx, dy) in image space.
         *
         * @param dx the delta in the x direction
         * @param dy the delta in the y direction
         */
        private void resizeBy(final float dx,
                              final float dy) {

            // Use a temp copy to do all calculations
            final RectF rect = new RectF(cropRect);

            // Don't let the cropping rectangle resize too fast.

            // Limit the deltas to a percentage of the difference between
            // the image rectangle and the cropping rectangle.
            final float maxDx = (imageRect.width() - rect.width()) * MAX_RESIZE_FACTOR;
            final float maxDy = (imageRect.height() - rect.height()) * MAX_RESIZE_FACTOR;

            final float actualDx;
            if (dx > 0.0f && dx > maxDx) {
                actualDx = maxDx;
            } else {
                actualDx = dx;
            }
            final float actualDy;
            if (dy > 0.0f && dy > maxDy) {
                actualDy = maxDy;
            } else {
                actualDy = dy;
            }

            rect.inset(-actualDx, -actualDy);

            // Don't let the cropping rectangle get too small.
            if (rect.width() < CROP_RECT_MIN_SIZE) {
                rect.inset(-(CROP_RECT_MIN_SIZE - rect.width()) / 2.0f, 0.0f);
            }
            if (rect.height() < CROP_RECT_MIN_SIZE) {
                rect.inset(0.0f, -(CROP_RECT_MIN_SIZE - rect.height()) / 2.0f);
            }

            // Finally make sure the cropping rectangle sits inside the image rectangle.
            if (rect.left < imageRect.left) {
                rect.offset(imageRect.left - rect.left, 0.0f);
            } else if (rect.right > imageRect.right) {
                rect.offset(-(rect.right - imageRect.right), 0.0f);
            }
            if (rect.top < imageRect.top) {
                rect.offset(0.0f, imageRect.top - rect.top);
            } else if (rect.bottom > imageRect.bottom) {
                rect.offset(0.0f, -(rect.bottom - imageRect.bottom));
            }

            // all done; set the final outcome and request a redraw.
            cropRect.set(rect);
            drawRect.set(computeLayout());
            imageView.invalidate();
        }

        /**
         * Maps the cropping rectangle from image space to screen space.
         *
         * @return the rectangle to draw
         */
        @NonNull
        private Rect computeLayout() {
            final RectF rectF = new RectF(cropRect);
            matrix.mapRect(rectF);
            return new Rect(Math.round(rectF.left), Math.round(rectF.top),
                            Math.round(rectF.right), Math.round(rectF.bottom));
        }
    }
}
