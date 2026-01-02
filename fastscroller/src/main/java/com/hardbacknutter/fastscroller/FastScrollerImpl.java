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

package com.hardbacknutter.fastscroller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Last checked for diff: 2025-02-05
 * 96438a56ed99d5f5d5917672dc433c864432ab08
 * <a href="https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/FastScroller.java">
 * HEAD</a>
 * Class responsible to animate and provide a fast scroller.
 * <p>
 * Search for 'HARDBACKNUTTER' to see modifications done.
 * <ul>
 *     <li>Allow setting a minimum size for the thumb.</li>
 *     <li>Allow setting an expanded touch area for the thumb.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Added support for tapping inside the track, and jump to a position.
 *         Calculation of the position is approximate only.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Option to add an index-overlay.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Added {@link OnFastScrollStateChangeListener}</li>
 * </ul>
 */
@SuppressWarnings("ALL")
public class FastScrollerImpl
        extends RecyclerView.ItemDecoration
        implements RecyclerView.OnItemTouchListener,
        // HARDBACKNUTTER - BEGIN
                   FastScroller
        // HARDBACKNUTTER - END
{

    // Scroll thumb not showing
    private static final int STATE_HIDDEN = 0;
    // Scroll thumb visible and moving along with the scrollbar
    private static final int STATE_VISIBLE = 1;
    // Scroll thumb being dragged by user
    private static final int STATE_DRAGGING = 2;
    private static final int DRAG_NONE = 0;
    private static final int DRAG_X = 1;
    private static final int DRAG_Y = 2;
    private static final int ANIMATION_STATE_OUT = 0;
    private static final int ANIMATION_STATE_FADING_IN = 1;
    private static final int ANIMATION_STATE_IN = 2;
    private static final int ANIMATION_STATE_FADING_OUT = 3;
    private static final int SHOW_DURATION_MS = 500;
    private static final int HIDE_DELAY_AFTER_VISIBLE_MS = 1500;
    private static final int HIDE_DELAY_AFTER_DRAGGING_MS = 1200;
    private static final int HIDE_DURATION_MS = 500;
    private static final int SCROLLBAR_FULL_OPAQUE = 255;
    private static final int[] PRESSED_STATE_SET = new int[]{android.R.attr.state_pressed};
    private static final int[] EMPTY_STATE_SET = new int[]{};
    // Final values for the vertical scroll bar
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final StateListDrawable mVerticalThumbDrawable;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Drawable mVerticalTrackDrawable;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final ValueAnimator mShowHideAnimator = ValueAnimator.ofFloat(0, 1);
    private final int mScrollbarMinimumRange;
    // HARDBACKNUTTER - BEGIN
    private int mMargin;
    // HARDBACKNUTTER - END
    private final int mVerticalThumbWidth;
    private final int mVerticalTrackWidth;
    // Final values for the horizontal scroll bar
    private final StateListDrawable mHorizontalThumbDrawable;
    private final Drawable mHorizontalTrackDrawable;

    private final int mHorizontalThumbHeight;
    private final int mHorizontalTrackHeight;
    private final int[] mVerticalRange = new int[2];
    private final int[] mHorizontalRange = new int[2];
    // Dynamic values for the vertical scroll bar
    @VisibleForTesting
    int mVerticalThumbHeight;
    @VisibleForTesting
    int mVerticalThumbCenterY;
    @VisibleForTesting
    float mVerticalDragY;
    // Dynamic values for the horizontal scroll bar
    @VisibleForTesting
    int mHorizontalThumbWidth;
    @VisibleForTesting
    int mHorizontalThumbCenterX;
    @VisibleForTesting
    float mHorizontalDragX;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    @AnimationState
    int mAnimationState = ANIMATION_STATE_OUT;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide(HIDE_DURATION_MS);
        }
    };

    // HARDBACKNUTTER - BEGIN
    @Px
    private final int mMinimalThumbSize;
    @Px
    private final int mExpandedTouchArea;
    @Nullable
    private OverlayProvider mOverlayProvider;
    @Nullable
    private OnFastScrollStateChangeListener mStateListener;
    // HARDBACKNUTTER - END

    private int mRecyclerViewWidth = 0;
    private int mRecyclerViewHeight = 0;
    private RecyclerView mRecyclerView;
    /**
     * Whether the document is long/wide enough to require scrolling. If not, we don't show the
     * relevant scroller.
     */
    private boolean mNeedVerticalScrollbar = false;
    private boolean mNeedHorizontalScrollbar = false;
    @State
    private int mState = STATE_HIDDEN;
    private final RecyclerView.OnScrollListener
            mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView,
                               int dx,
                               int dy) {
            updateScrollPosition(recyclerView.computeHorizontalScrollOffset(),
                                 recyclerView.computeVerticalScrollOffset());
        }
    };
    @DragState
    private int mDragState = DRAG_NONE;

    /**
     * HARDBACKNUTTER - ADDED javadocs for this constructor.
     *
     * @param recyclerView
     * @param verticalThumbDrawable
     * @param verticalTrackDrawable
     * @param horizontalThumbDrawable
     * @param horizontalTrackDrawable
     * @param defaultWidth
     * @param scrollbarMinimumRange
     * @param margin
     * @param minimalThumbSize        the minimal height the thumb can decrease to.
     *                                In pixels.
     * @param expandedTouchArea       the padding to add to the thumb to use as touch area.
     *                                In pixels.
     */
    public FastScrollerImpl(RecyclerView recyclerView,
                            StateListDrawable verticalThumbDrawable,
                            Drawable verticalTrackDrawable,
                            StateListDrawable horizontalThumbDrawable,
                            Drawable horizontalTrackDrawable,
                            int defaultWidth,
                            int scrollbarMinimumRange,
                            int margin,
                            // HARDBACKNUTTER - BEGIN
                            @Px
                            final int minimalThumbSize,
                            @Px final int expandedTouchArea
                            // HARDBACKNUTTER - END
    ) {

        mVerticalThumbDrawable = verticalThumbDrawable;
        mVerticalTrackDrawable = verticalTrackDrawable;
        mHorizontalThumbDrawable = horizontalThumbDrawable;
        mHorizontalTrackDrawable = horizontalTrackDrawable;
        // HARDBACKNUTTER - BEGIN
        mMinimalThumbSize = minimalThumbSize;
        mExpandedTouchArea = expandedTouchArea;
        // HARDBACKNUTTER - END

        mVerticalThumbWidth = Math.max(defaultWidth, verticalThumbDrawable.getIntrinsicWidth());
        mVerticalTrackWidth = Math.max(defaultWidth, verticalTrackDrawable.getIntrinsicWidth());
        mHorizontalThumbHeight = Math
                .max(defaultWidth, horizontalThumbDrawable.getIntrinsicWidth());
        mHorizontalTrackHeight = Math
                .max(defaultWidth, horizontalTrackDrawable.getIntrinsicWidth());
        mScrollbarMinimumRange = scrollbarMinimumRange;
        mMargin = margin;
        mVerticalThumbDrawable.setAlpha(SCROLLBAR_FULL_OPAQUE);
        mVerticalTrackDrawable.setAlpha(SCROLLBAR_FULL_OPAQUE);
        mShowHideAnimator.addListener(new AnimatorListener());
        mShowHideAnimator.addUpdateListener(new AnimatorUpdater());
        attachToRecyclerView(recyclerView);
    }

    // HARDBACKNUTTER - BEGIN
    @Override
    public void setOverlayProvider(@Nullable final OverlayProvider overlayProvider) {
        mOverlayProvider = overlayProvider;
    }

    @Override
    public void setOnFastScrollStateChangeListener(@Nullable final OnFastScrollStateChangeListener listener) {
        mStateListener = listener;
    }

    // HARDBACKNUTTER - END

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            setupCallbacks();
        }
    }

    private void setupCallbacks() {
        mRecyclerView.addItemDecoration(this);
        mRecyclerView.addOnItemTouchListener(this);
        mRecyclerView.addOnScrollListener(mOnScrollListener);
    }

    private void destroyCallbacks() {
        mRecyclerView.removeItemDecoration(this);
        mRecyclerView.removeOnItemTouchListener(this);
        mRecyclerView.removeOnScrollListener(mOnScrollListener);
        cancelHide();
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void requestRedraw() {
        mRecyclerView.invalidate();
    }

    void setState(@State int state) {
        // HARDBACKNUTTER - BEGIN
        if (mStateListener != null) {
            // 1. If we are physically dragging, ignore any request to stop dragging
            // unless that request is coming from our own ACTION_UP (which sets DRAG_NONE).
            if (mDragState != DRAG_NONE && state != STATE_DRAGGING) {
                return;
            }

            // 2. Only trigger the listener if the state is ACTUALLY changing
            if (state != mState) {
                if (state == STATE_DRAGGING) {
                    mStateListener.onFastScrollStarted();
                } else if (mState == STATE_DRAGGING) {
                    // We were dragging, now we aren't
                    mStateListener.onFastScrollEnded();
                }

                // Update the internal state immediately
                mState = state;
            }
        }
        // HARDBACKNUTTER - END

        if (state == STATE_DRAGGING && mState != STATE_DRAGGING) {
            mVerticalThumbDrawable.setState(PRESSED_STATE_SET);
            cancelHide();
        }
        if (state == STATE_HIDDEN) {
            requestRedraw();
        } else {
            show();
        }
        if (mState == STATE_DRAGGING && state != STATE_DRAGGING) {
            mVerticalThumbDrawable.setState(EMPTY_STATE_SET);
            resetHideDelay(HIDE_DELAY_AFTER_DRAGGING_MS);
        } else if (state == STATE_VISIBLE) {
            resetHideDelay(HIDE_DELAY_AFTER_VISIBLE_MS);
        }
        mState = state;
    }

    private boolean isLayoutRTL() {
        return mRecyclerView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    public boolean isDragging() {
        return mState == STATE_DRAGGING;
    }

    @VisibleForTesting
    boolean isVisible() {
        return mState == STATE_VISIBLE;
    }

    public void show() {
        switch (mAnimationState) {
            case ANIMATION_STATE_FADING_OUT:
                mShowHideAnimator.cancel();
                // fall through
            case ANIMATION_STATE_OUT:
                mAnimationState = ANIMATION_STATE_FADING_IN;
                mShowHideAnimator.setFloatValues((float) mShowHideAnimator.getAnimatedValue(), 1);
                mShowHideAnimator.setDuration(SHOW_DURATION_MS);
                mShowHideAnimator.setStartDelay(0);
                mShowHideAnimator.start();
                break;
        }
    }

    @VisibleForTesting
    void hide(int duration) {
        switch (mAnimationState) {
            case ANIMATION_STATE_FADING_IN:
                mShowHideAnimator.cancel();
                // fall through
            case ANIMATION_STATE_IN:
                mAnimationState = ANIMATION_STATE_FADING_OUT;
                mShowHideAnimator.setFloatValues((float) mShowHideAnimator.getAnimatedValue(), 0);
                mShowHideAnimator.setDuration(duration);
                mShowHideAnimator.start();
                break;
        }
    }

    private void cancelHide() {
        mRecyclerView.removeCallbacks(mHideRunnable);
    }

    private void resetHideDelay(int delay) {
        cancelHide();
        mRecyclerView.postDelayed(mHideRunnable, delay);
    }

    @Override
    public void onDrawOver(Canvas canvas,
                           RecyclerView parent,
                           RecyclerView.State state) {
        if (mRecyclerViewWidth != mRecyclerView.getWidth()
            || mRecyclerViewHeight != mRecyclerView.getHeight()) {
            mRecyclerViewWidth = mRecyclerView.getWidth();
            mRecyclerViewHeight = mRecyclerView.getHeight();
            // This is due to the different events ordering when keyboard is opened or
            // retracted vs rotate. Hence to avoid corner cases we just disable the
            // scroller when size changed, and wait until the scroll position is recomputed
            // before showing it back.
            setState(STATE_HIDDEN);
            return;
        }
        if (mAnimationState != ANIMATION_STATE_OUT) {
            if (mNeedVerticalScrollbar) {
                drawVerticalScrollbar(canvas);
            }
            if (mNeedHorizontalScrollbar) {
                drawHorizontalScrollbar(canvas);
            }
        }

        // HARDBACKNUTTER - BEGIN
        if (mOverlayProvider != null) {
            mOverlayProvider.showOverlay(isDragging(), mVerticalThumbCenterY);
        }
        // HARDBACKNUTTER - END
    }

    private void drawVerticalScrollbar(Canvas canvas) {
        int viewWidth = mRecyclerViewWidth;
        int left = viewWidth - mVerticalThumbWidth;

        // HARDBACKNUTTER - BEGIN
        // 1. Calculate the raw top position
        int top = mVerticalThumbCenterY - mVerticalThumbHeight / 2;

        // 2. Clamp the drawing bounds to the Safe Zone (mMargin)
        // This ensures the drawable never physically enters the rounded corner area
        if (top < mMargin) {
            top = mMargin;
        } else if (top + mVerticalThumbHeight > mRecyclerViewHeight - mMargin) {
            top = mRecyclerViewHeight - mMargin - mVerticalThumbHeight;
        }

        // 3. Set bounds
        mVerticalThumbDrawable.setBounds(0, 0, mVerticalThumbWidth, mVerticalThumbHeight);

        // The track itself should also respect the margin so it doesn't look cut off
        mVerticalTrackDrawable.setBounds(0, mMargin, mVerticalTrackWidth,
                                         mRecyclerViewHeight - mMargin);
        // HARDBACKNUTTER - END

        if (isLayoutRTL()) {
            mVerticalTrackDrawable.draw(canvas);
            canvas.translate(mVerticalThumbWidth, top);
            canvas.scale(-1, 1);
            mVerticalThumbDrawable.draw(canvas);
            canvas.scale(-1, 1);
            canvas.translate(-mVerticalThumbWidth, -top);
        } else {
            canvas.translate(left, 0);
            mVerticalTrackDrawable.draw(canvas);
            canvas.translate(0, top);
            mVerticalThumbDrawable.draw(canvas);
            canvas.translate(-left, -top);
        }
    }

    private void drawHorizontalScrollbar(Canvas canvas) {
        int viewHeight = mRecyclerViewHeight;
        int top = viewHeight - mHorizontalThumbHeight;

        // HARDBACKNUTTER - BEGIN
        // 1. Calculate the raw left position
        int left = mHorizontalThumbCenterX - mHorizontalThumbWidth / 2;

        // 2. Clamp the drawing bounds to the Safe Zone (mMargin)
        if (left < mMargin) {
            left = mMargin;
        } else if (left + mHorizontalThumbWidth > mRecyclerViewWidth - mMargin) {
            left = mRecyclerViewWidth - mMargin - mHorizontalThumbWidth;
        }

        // 3. Set bounds
        mHorizontalThumbDrawable.setBounds(0, 0,
                                           mHorizontalThumbWidth,
                                           mHorizontalThumbHeight);

        // The track respects left/right margins
        mHorizontalTrackDrawable.setBounds(mMargin, 0,
                                           mRecyclerViewWidth - mMargin,
                                           mHorizontalTrackHeight);

        canvas.translate(0, top);
        mHorizontalTrackDrawable.draw(canvas);
        canvas.translate(left, 0);
        mHorizontalThumbDrawable.draw(canvas);
        // Reset X
        canvas.translate(-left, 0);
        // Reset Y
        canvas.translate(0, -top);
        // HARDBACKNUTTER - END
    }

    // HARDBACKNUTTER - BEGIN
    private void updateMarginFromInsets() {
        if (mRecyclerView == null) {
            return;
        }

        // Fetch the insets directly from the root of the view hierarchy
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mRecyclerView);
        if (insets != null) {
            final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Take the larger of the two to clear corners & nav bar
            final int newMargin = Math.max(systemBars.top, systemBars.bottom);

            // Only update if it actually changed to avoid infinite loops
            if (this.mMargin != newMargin) {
                this.mMargin = newMargin;
                // We don't call requestRedraw here because updateScrollPosition
                // is already in the middle of a logic pass.
            }
        }
    }
    // HARDBACKNUTTER - END

    /**
     * Notify the scroller of external change of the scroll, e.g. through dragging or flinging on
     * the view itself.
     *
     * @param offsetX The new scroll X offset.
     * @param offsetY The new scroll Y offset.
     */
    void updateScrollPosition(int offsetX,
                              int offsetY) {
        // HARDBACKNUTTER - BEGIN
        // Pull the hardware margins first
        updateMarginFromInsets();
        // HARDBACKNUTTER - END

        int verticalContentLength = mRecyclerView.computeVerticalScrollRange();
        int verticalVisibleLength = mRecyclerViewHeight;
        mNeedVerticalScrollbar = verticalContentLength - verticalVisibleLength > 0
                                 && mRecyclerViewHeight >= mScrollbarMinimumRange;
        int horizontalContentLength = mRecyclerView.computeHorizontalScrollRange();
        int horizontalVisibleLength = mRecyclerViewWidth;
        mNeedHorizontalScrollbar = horizontalContentLength - horizontalVisibleLength > 0
                                   && mRecyclerViewWidth >= mScrollbarMinimumRange;
        if (!mNeedVerticalScrollbar && !mNeedHorizontalScrollbar) {
            if (mState != STATE_HIDDEN) {
                setState(STATE_HIDDEN);
            }
            return;
        }
        if (mNeedVerticalScrollbar) {
            // HARDBACKNUTTER - BEGIN
            // This calculates thumb size based on how many items are on screen vs total items
            final LinearLayoutManager layoutManager = (LinearLayoutManager)
                    mRecyclerView.getLayoutManager();
            if (layoutManager == null) {
                return;
            }

            final int totalItemCount = layoutManager.getItemCount();
            if (totalItemCount == 0) {
                return;
            }

            // 1. Calculate Progress based on Item Index, not Pixels
            final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
            final int lastVisiblePos = layoutManager.findLastVisibleItemPosition();
            final int visibleItemCount = lastVisiblePos - firstVisiblePos + 1;

            // 1. Calculate Thumb Height based on visible item ratio
            final float itemsVisibleRatio = (float) visibleItemCount / totalItemCount;
            mVerticalThumbHeight = (int) (itemsVisibleRatio * (mRecyclerViewHeight - 2 * mMargin));
            mVerticalThumbHeight = Math.max(mVerticalThumbHeight, mMinimalThumbSize);

            // 2. Define the Safe Drawing Range for the center of the thumb
            final int safeAreaTop = mMargin;
            final int safeAreaBottom = mRecyclerViewHeight - mMargin;

            final float minCenter = safeAreaTop + (mVerticalThumbHeight / 2.0f);
            final float maxCenter = safeAreaBottom - (mVerticalThumbHeight / 2.0f);
            final float travelRange = maxCenter - minCenter;

            // 3. Calculate Scroll Fraction (0.0 to 1.0) based on Index
            float scrollFraction;
            if (visibleItemCount >= totalItemCount) {
                scrollFraction = 0;
            } else {
                // This ensures that when the last item is visible, fraction is exactly 1.0
                scrollFraction = (float) firstVisiblePos / (totalItemCount - visibleItemCount);
            }
            scrollFraction = Math.max(0f, Math.min(1.0f, scrollFraction));

            // 4. Set the final Center Y
            mVerticalThumbCenterY = (int) (minCenter + (scrollFraction * travelRange));
            // HARDBACKNUTTER - END
        }
        if (mNeedHorizontalScrollbar) {
            // HARDBACKNUTTER - BEGIN
            // Horizontal often uses standard pixel math as items usually have fixed widths
            final float maxScrollOffset = (float) (horizontalContentLength - horizontalVisibleLength);
            final float scrollFraction = maxScrollOffset > 0 ? (offsetX / maxScrollOffset) : 0;

            mHorizontalThumbWidth = Math.min(horizontalVisibleLength,
                                             (horizontalVisibleLength * horizontalVisibleLength) / horizontalContentLength);
            mHorizontalThumbWidth = Math.max(mHorizontalThumbWidth, mMinimalThumbSize);

            final int safeAreaLeft = mMargin;
            final int safeAreaRight = mRecyclerViewWidth - mMargin;

            final float minCenter = safeAreaLeft + (mHorizontalThumbWidth / 2.0f);
            final float maxCenter = safeAreaRight - (mHorizontalThumbWidth / 2.0f);
            final float travelRange = maxCenter - minCenter;

            mHorizontalThumbCenterX = (int) (minCenter + (scrollFraction * travelRange));        // HARDBACKNUTTER - END
        }
        if (mState == STATE_HIDDEN || mState == STATE_VISIBLE) {
            setState(STATE_VISIBLE);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                         @NonNull MotionEvent ev) {

        // HARDBACKNUTTER - BEGIN
        // If we are already dragging, we MUST return true to keep
        // receiving the events (like ACTION_UP) in our onTouchEvent.
        if (mState == STATE_DRAGGING) {
            return true;
        }
        // HARDBACKNUTTER - END

        final boolean handled;
        if (mState == STATE_VISIBLE) {
            boolean insideVerticalThumb = isPointInsideVerticalThumb(ev.getX(), ev.getY());
            boolean insideHorizontalThumb = isPointInsideHorizontalThumb(ev.getX(), ev.getY());
            if (ev.getAction() == MotionEvent.ACTION_DOWN
                && (insideVerticalThumb || insideHorizontalThumb)) {

                // HARDBACKNUTTER - BEGIN
                // Tell parents not to steal the focus now that we've grabbed the thumb
                recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                // HARDBACKNUTTER - END

                if (insideHorizontalThumb) {
                    mDragState = DRAG_X;
                    mHorizontalDragX = (int) ev.getX();
                } else if (insideVerticalThumb) {
                    mDragState = DRAG_Y;
                    mVerticalDragY = (int) ev.getY();
                }
                setState(STATE_DRAGGING);
                handled = true;
                // HARDBACKNUTTER - BEGIN
            } else if (isPointInsideTrack(ev.getX(), ev.getY())) {
                jumpToPositionFromTrack(ev.getY());
                handled = true;
                // HARDBACKNUTTER - END
            } else {
                handled = false;
            }
        } else {
            handled = mState == STATE_DRAGGING;
        }
        return handled;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView recyclerView,
                             @NonNull MotionEvent me) {
        if (mState == STATE_HIDDEN) {
            return;
        }

        // HARDBACKNUTTER - BEGIN
        // Tell parents not to steal touch while we are dragging
        if (mState == STATE_DRAGGING) {
            recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
        }
        // HARDBACKNUTTER - END

        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            boolean insideVerticalThumb = isPointInsideVerticalThumb(me.getX(), me.getY());
            boolean insideHorizontalThumb = isPointInsideHorizontalThumb(me.getX(), me.getY());
            if (insideVerticalThumb || insideHorizontalThumb) {
                if (insideHorizontalThumb) {
                    mDragState = DRAG_X;
                    mHorizontalDragX = (int) me.getX();
                } else if (insideVerticalThumb) {
                    mDragState = DRAG_Y;
                    mVerticalDragY = (int) me.getY();
                }
                setState(STATE_DRAGGING);
            }
        } else if (me.getAction() == MotionEvent.ACTION_UP && mState == STATE_DRAGGING) {
            mVerticalDragY = 0;
            mHorizontalDragX = 0;
            // HARDBACKNUTTER - BEGIN
            // Clear DragState BEFORE calling setState
            mDragState = DRAG_NONE;
            setState(STATE_VISIBLE);
            // HARDBACKNUTTER - END
        } else if (me.getAction() == MotionEvent.ACTION_MOVE && mState == STATE_DRAGGING) {
            show();
            if (mDragState == DRAG_X) {
                horizontalScrollTo(me.getX());
            }
            if (mDragState == DRAG_Y) {
                verticalScrollTo(me.getY());
            }
        }
    }

    // HARDBACKNUTTER - BEGIN
    private void jumpToPositionFromTrack(final float y) {
        final RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
        if (adapter == null) {
            return;
        }

        final int rvHeight = mRecyclerView.getHeight();

        // 1. Establish the "Safe Track" boundaries (same as updateScrollPosition)
        final int safeAreaTop = mMargin;
        final int safeAreaBottom = rvHeight - mMargin;

        // 2. Define the range the THUMB CENTER can actually move in
        final float minCenter = safeAreaTop + (mVerticalThumbHeight / 2.0f);
        final float maxCenter = safeAreaBottom - (mVerticalThumbHeight / 2.0f);
        final float travelRange = maxCenter - minCenter;

        // 3. Clamp the touch 'y' to the allowed center range
        // This forces a tap at the absolute screen edge to be treated
        // as a tap at the "max possible" thumb position.
        final float clampedY = Math.max(minCenter, Math.min(maxCenter, y));

        // 4. Calculate Percentage based on TRAVEL RANGE
        // If clampedY == maxCenter, percentage will be exactly 1.0 (100%)
        float percentage = (clampedY - minCenter) / travelRange;
        percentage = Math.max(0.0f, Math.min(1.0f, percentage));

        // 5. Map to Item Count
        final int totalItems = adapter.getItemCount();
        int targetPosition;
        if (percentage >= 0.98f) {
            // If we are within 2% of the bottom, force the last item
            targetPosition = totalItems - 1;
        } else if (percentage <= 0.02f) {
            // Force the first item at the top
            targetPosition = 0;
        } else {
            targetPosition = (int) (percentage * totalItems);
        }

        // Ensure it's never out of bounds
        targetPosition = Math.max(0, Math.min(totalItems - 1, targetPosition));

        // 6. Execute the Jump with Snapping
        final RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            // Offset 0 ensures the item is at the VERY TOP
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(targetPosition, 0);
        } else {
            mRecyclerView.scrollToPosition(targetPosition);
        }

        // 7. Sync the thumb position visually immediately
        mVerticalThumbCenterY = (int) clampedY;
        requestRedraw();
    }
    // HARDBACKNUTTER - END

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    private void verticalScrollTo(float y) {
        // HARDBACKNUTTER - BEGIN
        final LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
        if (lm == null) {
            return;
        }

        // 1. Same Travel Range logic
        final int safeAreaTop = mMargin;
        final int safeAreaBottom = mRecyclerViewHeight - mMargin;
        final float minCenter = safeAreaTop + (mVerticalThumbHeight / 2.0f);
        final float maxCenter = safeAreaBottom - (mVerticalThumbHeight / 2.0f);
        final float travelRange = maxCenter - minCenter;

        // 2. Calculate Percentage of the physical drag
        final float clampedY = Math.max(minCenter, Math.min(maxCenter, y));
        final float percentage = (clampedY - minCenter) / travelRange;

        // 3. Map to Item Index
        final int totalItems = lm.getItemCount();
        final int targetPosition = (int) (percentage * (totalItems - 1));

        // 4. Scroll to the exact item
        // This is much more reliable for variable heights than scrollBy(pixels)
        lm.scrollToPositionWithOffset(targetPosition, 0);

        mVerticalThumbCenterY = (int) clampedY;
        requestRedraw();
        // HARDBACKNUTTER - END
    }

    private void horizontalScrollTo(float x) {
        // HARDBACKNUTTER - BEGIN
        // 1. Define the range the thumb center is allowed to move in
        final int safeAreaLeft = mMargin;
        final int safeAreaRight = mRecyclerViewWidth - mMargin;

        final float minCenter = safeAreaLeft + (mHorizontalThumbWidth / 2.0f);
        final float maxCenter = safeAreaRight - (mHorizontalThumbWidth / 2.0f);

        // 2. Clamp the input X
        x = Math.max(minCenter, Math.min(maxCenter, x));

        if (Math.abs(mHorizontalThumbCenterX - x) < 2) {
            return;
        }

        // 3. Perform the scroll
        // Note: We use the clamped range (maxCenter - minCenter) as the scrollbarLength
        final int scrollbarLength = (int) (maxCenter - minCenter);
        final int scrollingBy = scrollTo(mHorizontalDragX, x,
                                         new int[]{0, scrollbarLength}, // relative range
                                         mRecyclerView.computeHorizontalScrollRange(),
                                         mRecyclerView.computeHorizontalScrollOffset(),
                                         mRecyclerViewWidth);
        // HARDBACKNUTTER - END
        if (scrollingBy != 0) {
            mRecyclerView.scrollBy(scrollingBy, 0);
        }
        mHorizontalDragX = x;
    }

    private int scrollTo(float oldDragPos,
                         float newDragPos,
                         int[] scrollbarRange,
                         int scrollRange,
                         int scrollOffset,
                         int viewLength) {
        int scrollbarLength = scrollbarRange[1] - scrollbarRange[0];
        if (scrollbarLength == 0) {
            return 0;
        }
        float percentage = ((newDragPos - oldDragPos) / (float) scrollbarLength);
        int totalPossibleOffset = scrollRange - viewLength;
        int scrollingBy = (int) (percentage * totalPossibleOffset);
        int absoluteOffset = scrollOffset + scrollingBy;
        if (absoluteOffset < totalPossibleOffset && absoluteOffset >= 0) {
            return scrollingBy;
        } else {
            return 0;
        }
    }

    @VisibleForTesting
    boolean isPointInsideVerticalThumb(float x,
                                       float y) {
        // HARDBACKNUTTER - BEGIN
        // 1. Horizontal Check (respecting RTL and Expanded Touch Area)
        final boolean isInsideX;
        if (isLayoutRTL()) {
            // In RTL, the thumb is on the left. Expand to the right.
            isInsideX = x <= mVerticalThumbWidth + mExpandedTouchArea;
        } else {
            // In LTR, the thumb is on the right. Expand to the left.
            isInsideX = x >= mRecyclerViewWidth - mVerticalThumbWidth - mExpandedTouchArea;
        }

        if (!isInsideX) {
            return false;
        }

        // 2. Vertical Check
        // We calculate the top and bottom based on the center.
        // Because mVerticalThumbCenterY is now clamped to mMargin,
        // these bounds will correctly follow the thumb even near rounded corners.
        final float halfHeight = mVerticalThumbHeight / 2.0f;
        final float topBound = mVerticalThumbCenterY - halfHeight - mExpandedTouchArea;
        final float bottomBound = mVerticalThumbCenterY + halfHeight + mExpandedTouchArea;

        return y >= topBound && y <= bottomBound;
        // HARDBACKNUTTER - END
    }

    @VisibleForTesting
    boolean isPointInsideHorizontalThumb(float x,
                                         float y) {
        return (y >= mRecyclerViewHeight - mHorizontalThumbHeight)
               && x >= mHorizontalThumbCenterX - mHorizontalThumbWidth / 2
               && x <= mHorizontalThumbCenterX + mHorizontalThumbWidth / 2;
    }

    // HARDBACKNUTTER - BEGIN
    private boolean isPointInsideTrack(final float x,
                                       final float y) {
        // Only register track taps within the Safe Zone (mMargin)
        final boolean isInsideY = y >= mMargin && y <= (mRecyclerViewHeight - mMargin);

        final boolean isInsideX;
        if (isLayoutRTL()) {
            isInsideX = x <= mVerticalTrackWidth;
        } else {
            isInsideX = x >= mRecyclerViewWidth - mVerticalTrackWidth;
        }

        return isInsideY && isInsideX;
    }
    // HARDBACKNUTTER - END

    @VisibleForTesting
    Drawable getHorizontalTrackDrawable() {
        return mHorizontalTrackDrawable;
    }

    @VisibleForTesting
    Drawable getHorizontalThumbDrawable() {
        return mHorizontalThumbDrawable;
    }

    @VisibleForTesting
    Drawable getVerticalTrackDrawable() {
        return mVerticalTrackDrawable;
    }

    @VisibleForTesting
    Drawable getVerticalThumbDrawable() {
        return mVerticalThumbDrawable;
    }

    /**
     * Gets the (min, max) vertical positions of the vertical scroll bar.
     */
    private int[] getVerticalRange() {
        mVerticalRange[0] = mMargin;
        mVerticalRange[1] = mRecyclerViewHeight - mMargin;
        return mVerticalRange;
    }

    /**
     * Gets the (min, max) horizontal positions of the horizontal scroll bar.
     */
    private int[] getHorizontalRange() {
        mHorizontalRange[0] = mMargin;
        mHorizontalRange[1] = mRecyclerViewWidth - mMargin;
        return mHorizontalRange;
    }

    @IntDef({STATE_HIDDEN, STATE_VISIBLE, STATE_DRAGGING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {

    }

    @IntDef({DRAG_X, DRAG_Y, DRAG_NONE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DragState {

    }

    @IntDef({ANIMATION_STATE_OUT, ANIMATION_STATE_FADING_IN, ANIMATION_STATE_IN,
             ANIMATION_STATE_FADING_OUT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface AnimationState {

    }

    private class AnimatorListener
            extends AnimatorListenerAdapter {

        private boolean mCanceled = false;

        AnimatorListener() {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            // Cancel is always followed by a new directive, so don't update state.
            if (mCanceled) {
                mCanceled = false;
                return;
            }
            if ((float) mShowHideAnimator.getAnimatedValue() == 0) {
                mAnimationState = ANIMATION_STATE_OUT;
                setState(STATE_HIDDEN);
            } else {
                mAnimationState = ANIMATION_STATE_IN;
                requestRedraw();
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceled = true;
        }
    }

    private class AnimatorUpdater
            implements ValueAnimator.AnimatorUpdateListener {

        AnimatorUpdater() {
        }

        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            int alpha = (int) (SCROLLBAR_FULL_OPAQUE * ((float) valueAnimator.getAnimatedValue()));
            mVerticalThumbDrawable.setAlpha(alpha);
            mVerticalTrackDrawable.setAlpha(alpha);
            requestRedraw();
        }
    }
}
