/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Last checked for diff: 2025-02-05.
 * 96438a56ed99d5f5d5917672dc433c864432ab08
 * <a href="https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/FastScroller.java">
 * HEAD</a>
 * <p>
 * 2026-01-25: forking...  google will never fix theirs.
 * <ul>
 *     <li>Allow setting a minimum size for the thumb.</li>
 *     <li>Allow setting an expanded touch area for the thumb.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Added support for tapping inside the track, and jump to a position.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Option to add an {@link OverlayProvider}.
 *         VERTICAL SCROLL ONLY</li>
 *     <li>Handles variable height rows.</li>
 *     <li>Handles {@link WindowInsetsCompat.Type#systemBars()}. </li>
 *     <li>Added {@link OnFastScrollStateChangeListener}</li>
 * </ul>
 */
class FastScrollerImpl
        extends RecyclerView.ItemDecoration
        implements RecyclerView.OnItemTouchListener,
                   FastScroller {

    private static final int SHOW_DURATION_MS = 500;
    private static final int HIDE_DELAY_AFTER_VISIBLE_MS = 1500;
    private static final int HIDE_DELAY_AFTER_DRAGGING_MS = 1200;
    private static final int HIDE_DURATION_MS = 500;

    private static final int SCROLLBAR_FULL_OPAQUE = 255;
    private static final int[] PRESSED_STATE_SET = {android.R.attr.state_pressed};
    private static final int[] EMPTY_STATE_SET = {};

    private final ValueAnimator showHideAnimator = ValueAnimator.ofFloat(0, 1);

    // Final values for the vertical scroll bar
    private final StateListDrawable verticalThumbDrawable;
    private final Drawable verticalTrackDrawable;
    private final int verticalThumbWidth;
    private final int verticalTrackWidth;

    // Final values for the horizontal scroll bar
    private final StateListDrawable horizontalThumbDrawable;
    private final Drawable horizontalTrackDrawable;
    private final int horizontalThumbHeight;
    private final int horizontalTrackHeight;

    private final int scrollbarMinimumRange;
    @Px
    private final int minimalThumbSize;
    @Px
    private final int expandedTouchArea;

    // Dynamic values for the vertical scroll bar
    private int verticalThumbHeight;
    private int verticalThumbCenterY;
    private float verticalDragY;

    // Dynamic values for the horizontal scroll bar
    private int horizontalThumbWidth;
    private int horizontalThumbCenterX;
    private float horizontalDragX;

    @NonNull
    private AnimationState animationState = AnimationState.Out;
    private final Runnable hideRunnable = () -> hide(HIDE_DURATION_MS);

    private int marginTop;
    private int marginBottom;
    private int marginLeft;
    private int marginRight;

    @Nullable
    private OverlayProvider overlayProvider;
    @Nullable
    private OnFastScrollStateChangeListener stateListener;

    private RecyclerView recyclerView;
    private int recyclerViewWidth;
    private int recyclerViewHeight;

    /**
     * Whether the document is long/wide enough to require scrolling.
     * If not, we don't show the relevant scroller.
     */
    private boolean needVerticalScrollbar;
    private boolean needHorizontalScrollbar;

    @NonNull
    private State state = State.Hidden;
    @NonNull
    private DragState dragState = DragState.None;

    private final RecyclerView.OnScrollListener scrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    updateScrollPosition(recyclerView.computeHorizontalScrollOffset(),
                                         recyclerView.computeVerticalScrollOffset());
                }
            };

    /**
     * Constructor.
     *
     * @param verticalThumbDrawable   to use
     * @param verticalTrackDrawable   to use
     * @param horizontalThumbDrawable to use
     * @param horizontalTrackDrawable to use
     * @param defaultWidth            The width of the thumb
     *                                In pixels.
     * @param scrollbarMinimumRange   In pixels.
     * @param minimalThumbSize        the minimal height the thumb can decrease to.
     *                                In pixels.
     * @param expandedTouchArea       the padding to add to the thumb to use as touch area.
     *                                In pixels.
     */
    FastScrollerImpl(@NonNull final StateListDrawable verticalThumbDrawable,
                     @NonNull final Drawable verticalTrackDrawable,
                     @NonNull final StateListDrawable horizontalThumbDrawable,
                     @NonNull final Drawable horizontalTrackDrawable,
                     @Px final int defaultWidth,
                     @Px final int scrollbarMinimumRange,
                     @Px final int minimalThumbSize,
                     @Px final int expandedTouchArea) {

        this.verticalThumbDrawable = verticalThumbDrawable;
        this.verticalTrackDrawable = verticalTrackDrawable;

        this.horizontalThumbDrawable = horizontalThumbDrawable;
        this.horizontalTrackDrawable = horizontalTrackDrawable;

        verticalThumbWidth = Math.max(defaultWidth, verticalThumbDrawable.getIntrinsicWidth());
        verticalTrackWidth = Math.max(defaultWidth, verticalTrackDrawable.getIntrinsicWidth());

        horizontalThumbHeight = Math
                .max(defaultWidth, horizontalThumbDrawable.getIntrinsicWidth());
        horizontalTrackHeight = Math
                .max(defaultWidth, horizontalTrackDrawable.getIntrinsicWidth());

        this.verticalThumbDrawable.setAlpha(SCROLLBAR_FULL_OPAQUE);
        this.verticalTrackDrawable.setAlpha(SCROLLBAR_FULL_OPAQUE);

        this.scrollbarMinimumRange = scrollbarMinimumRange;
        this.minimalThumbSize = minimalThumbSize;
        this.expandedTouchArea = expandedTouchArea;

        showHideAnimator.addListener(new AnimatorListener());
        showHideAnimator.addUpdateListener(new AnimatorUpdater());
    }

    @Override
    public void setOverlayProvider(@Nullable final OverlayProvider overlayProvider) {
        this.overlayProvider = overlayProvider;
    }

    @Override
    public void setOnFastScrollStateChangeListener(@Nullable final
                                                   OnFastScrollStateChangeListener listener) {
        stateListener = listener;
    }

    @Override
    public void attach(@Nullable final RecyclerView recyclerView)
            throws IllegalArgumentException {
        if (this.recyclerView == recyclerView) {
            return;
        }
        if (this.recyclerView != null) {
            this.recyclerView.removeItemDecoration(this);
            this.recyclerView.removeOnItemTouchListener(this);
            this.recyclerView.removeOnScrollListener(scrollListener);
            cancelHide();
        }

        this.recyclerView = recyclerView;

        if (this.recyclerView != null) {
            if (!(this.recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
                throw new IllegalArgumentException("RecyclerView must have a LinearLayoutManager");
            }

            this.recyclerView.addItemDecoration(this);
            this.recyclerView.addOnItemTouchListener(this);
            this.recyclerView.addOnScrollListener(scrollListener);
        }
    }

    private void requestRedraw() {
        recyclerView.invalidate();
    }

    private boolean isLayoutRTL() {
        return recyclerView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private void show() {
        switch (animationState) {
            case FadingOut:
                showHideAnimator.cancel();
                // fall through
            case Out:
                animationState = AnimationState.FadingIn;
                showHideAnimator.setFloatValues((float) showHideAnimator.getAnimatedValue(), 1);
                showHideAnimator.setDuration(SHOW_DURATION_MS);
                showHideAnimator.setStartDelay(0);
                showHideAnimator.start();
                break;
        }
    }

    private void hide(final int duration) {
        switch (animationState) {
            case FadingIn:
                showHideAnimator.cancel();
                // fall through
            case In:
                animationState = AnimationState.FadingOut;
                showHideAnimator.setFloatValues((float) showHideAnimator.getAnimatedValue(), 0);
                showHideAnimator.setDuration(duration);
                showHideAnimator.start();
                break;
        }
    }

    private void cancelHide() {
        recyclerView.removeCallbacks(hideRunnable);
    }

    private void resetHideDelay(final int delay) {
        cancelHide();
        recyclerView.postDelayed(hideRunnable, delay);
    }

    private void setState(@NonNull final State state) {
        if (stateListener != null) {
            // 1. If we are physically dragging, ignore any request to stop dragging
            // unless that request is coming from our own ACTION_UP (which sets DragState.None).
            if (dragState != DragState.None && state != State.Dragging) {
                return;
            }

            // 2. Only trigger the listener if the state is ACTUALLY changing
            if (state != this.state) {
                if (state == State.Dragging) {
                    // The NEW state is Dragging
                    stateListener.onFastScrollStarted();
                } else if (this.state == State.Dragging) {
                    // We WERE dragging, now we aren't
                    stateListener.onFastScrollEnded();
                }

                // Update the internal state immediately
                this.state = state;
            }
        }

        if (state == State.Dragging && this.state != State.Dragging) {
            verticalThumbDrawable.setState(PRESSED_STATE_SET);
            cancelHide();
        }

        if (state == State.Hidden) {
            requestRedraw();
        } else {
            show();
        }

        if (this.state == State.Dragging && state != State.Dragging) {
            verticalThumbDrawable.setState(EMPTY_STATE_SET);
            resetHideDelay(HIDE_DELAY_AFTER_DRAGGING_MS);

        } else if (state == State.Visible) {
            resetHideDelay(HIDE_DELAY_AFTER_VISIBLE_MS);
        }

        this.state = state;
    }

    @Override
    public void onDrawOver(@NonNull final Canvas canvas,
                           @NonNull final RecyclerView parent,
                           @NonNull final RecyclerView.State state) {
        if (recyclerViewWidth != recyclerView.getWidth()
            || recyclerViewHeight != recyclerView.getHeight()) {
            recyclerViewWidth = recyclerView.getWidth();
            recyclerViewHeight = recyclerView.getHeight();
            // This is due to the different events ordering when keyboard is opened or
            // retracted vs rotate. Hence to avoid corner cases we just disable the
            // scroller when size changed, and wait until the scroll position is recomputed
            // before showing it back.
            setState(State.Hidden);
            return;
        }
        if (animationState != AnimationState.Out) {
            if (needVerticalScrollbar) {
                drawVerticalScrollbar(canvas);
            }
            if (needHorizontalScrollbar) {
                drawHorizontalScrollbar(canvas);
            }
        }

        if (overlayProvider != null) {
            overlayProvider.showOverlay(this.state == State.Dragging, verticalThumbCenterY);
        }
    }

    private void drawVerticalScrollbar(@NonNull final Canvas canvas) {
        final int viewWidth = recyclerViewWidth;
        final int leftDx = viewWidth - verticalThumbWidth;

        final int safeAreaTop = marginTop;
        final int safeAreaBottom = recyclerViewHeight - marginBottom;

        // Calculate the raw top position
        int topDx = verticalThumbCenterY - verticalThumbHeight / 2;
        // Clamp the drawing bounds to the Safe Area
        // This ensures the drawable never physically enters any rounded corner area
        if (topDx < safeAreaTop) {
            topDx = safeAreaTop;
        } else if (topDx + verticalThumbHeight > safeAreaBottom) {
            topDx = safeAreaBottom - verticalThumbHeight;
        }

        // Set bounds for the thumb
        verticalThumbDrawable.setBounds(0, 0, verticalThumbWidth, verticalThumbHeight);

        // The track itself should also respect the margin so it doesn't look cut off
        verticalTrackDrawable.setBounds(0, safeAreaTop, verticalTrackWidth, safeAreaBottom);

        if (isLayoutRTL()) {
            verticalTrackDrawable.draw(canvas);
            canvas.translate(verticalThumbWidth, topDx);
            canvas.scale(-1, 1);
            verticalThumbDrawable.draw(canvas);
            canvas.scale(-1, 1);
            canvas.translate(-verticalThumbWidth, -topDx);
        } else {
            canvas.translate(leftDx, 0);
            verticalTrackDrawable.draw(canvas);
            canvas.translate(0, topDx);
            verticalThumbDrawable.draw(canvas);
            canvas.translate(-leftDx, -topDx);
        }
    }

    private void drawHorizontalScrollbar(@NonNull final Canvas canvas) {
        final int viewHeight = recyclerViewHeight;
        final int topDx = viewHeight - horizontalThumbHeight;

        final int safeAreaLeft = marginLeft;
        final int safeAreaRight = recyclerViewWidth - marginRight;

        // Calculate the raw left position
        int leftDx = horizontalThumbCenterX - horizontalThumbWidth / 2;
        // Clamp the drawing bounds to the Safe Area
        // This ensures the drawable never physically enters any rounded corner area
        if (leftDx < safeAreaLeft) {
            leftDx = safeAreaLeft;
        } else if (leftDx + horizontalThumbWidth > safeAreaRight) {
            leftDx = safeAreaRight - horizontalThumbWidth;
        }

        // Set bounds for the thumb
        horizontalThumbDrawable.setBounds(0, 0, horizontalThumbWidth, horizontalThumbHeight);

        // The track itself should also respect the margin so it doesn't look cut off
        horizontalTrackDrawable.setBounds(safeAreaLeft, 0, safeAreaRight, horizontalTrackHeight);

        canvas.translate(0, topDx);
        horizontalTrackDrawable.draw(canvas);
        canvas.translate(leftDx, 0);
        horizontalThumbDrawable.draw(canvas);
        // Reset X and Y
        canvas.translate(-leftDx, 0);
        canvas.translate(0, -topDx);
    }


    /**
     * Notify the scroller of external change of the scroll, e.g. through dragging or flinging on
     * the view itself.
     *
     * @param offsetX The new scroll X offset.
     * @param offsetY The new scroll Y offset.
     */
    private void updateScrollPosition(final int offsetX,
                                      final int offsetY) {
        // Pull the hardware margins first
        updateMarginFromInsets();

        final int verticalContentLength = recyclerView.computeVerticalScrollRange();
        final int verticalVisibleLength = recyclerViewHeight;
        needVerticalScrollbar = verticalContentLength - verticalVisibleLength > 0
                                && recyclerViewHeight >= scrollbarMinimumRange;

        final int horizontalContentLength = recyclerView.computeHorizontalScrollRange();
        final int horizontalVisibleLength = recyclerViewWidth;
        needHorizontalScrollbar = horizontalContentLength - horizontalVisibleLength > 0
                                  && recyclerViewWidth >= scrollbarMinimumRange;

        if (!needVerticalScrollbar && !needHorizontalScrollbar) {
            if (state != State.Hidden) {
                setState(State.Hidden);
            }
            return;
        }

        if (needVerticalScrollbar) {
            final LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();

            @SuppressWarnings("DataFlowIssue")
            final int totalItemCount = lm.getItemCount();
            if (totalItemCount == 0) {
                return;
            }

            calculateVerticalThumbCenterY(lm, totalItemCount);
        }

        if (needHorizontalScrollbar) {
            calculateHorizontalThumbCenterX(offsetX,
                                            horizontalContentLength,
                                            horizontalVisibleLength);
        }

        if (state == State.Hidden || state == State.Visible) {
            setState(State.Visible);
        }
    }

    private void updateMarginFromInsets() {
        if (recyclerView == null) {
            return;
        }

        // Fetch the insets directly from the root of the view hierarchy
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(recyclerView);
        if (insets != null) {
            final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            this.marginTop = systemBars.top;
            this.marginBottom = systemBars.bottom;
            this.marginLeft = systemBars.left;
            this.marginRight = systemBars.right;
            // Note we don't call requestRedraw here; updateScrollPosition does that
        }
    }

    private void calculateVerticalThumbCenterY(@NonNull final LinearLayoutManager lm,
                                               final int totalItemCount) {
        final int firstVisiblePos = lm.findFirstVisibleItemPosition();
        final View firstView = lm.findViewByPosition(firstVisiblePos);

        // Sanity check
        if (firstView == null) {
            return;
        }

        // Calculate the Position.
        // We take the index and add the fraction of the current item scrolled off-screen.
        // This handles variable row heights by looking only at the current (top) row's height.
        final float partialFactor = firstView.getY() / (float) firstView.getHeight();
        final float position = firstVisiblePos - partialFactor;

        // Define the Track (Safe Area)
        final int safeAreaTop = marginTop;
        final int safeAreaBottom = recyclerViewHeight - marginBottom;
        final int trackHeight = safeAreaBottom - safeAreaTop;

        // Take the average height of the first and last visible items
        // to get a more or less stable "items per screen" value.
        final int lastVisiblePos = lm.findLastVisibleItemPosition();
        final int itemsVisibleNow = Math.max(1, lastVisiblePos - firstVisiblePos);
        final float avgHeightVisible = (float) trackHeight / itemsVisibleNow;

        // Use this average to set the thumb height and the scroll ratio
        final float estimatedItemsOnScreen = (float) trackHeight / avgHeightVisible;

        final int dynamicHeight = (int) (trackHeight * (estimatedItemsOnScreen / totalItemCount));
        verticalThumbHeight = Math.max(dynamicHeight, minimalThumbSize);

        final float minCenter = safeAreaTop + (verticalThumbHeight / 2.0f);
        final float maxCenter = safeAreaBottom - (verticalThumbHeight / 2.0f);
        final float travelRange = maxCenter - minCenter;

        // Calculate Percentage
        if (totalItemCount <= itemsVisibleNow) {
            verticalThumbCenterY = (int) minCenter;
        } else {
            // Use 'totalItemCount - itemsVisibleNow' as a fixed denominator,
            // to keep the thumb stable whn rows of different height scroll by.
            float percentage = position / (totalItemCount - itemsVisibleNow);
            percentage = Math.max(0f, Math.min(1.0f, percentage));
            verticalThumbCenterY = (int) (minCenter + (percentage * travelRange));
        }
    }

    private void calculateHorizontalThumbCenterX(final int offsetX,
                                                 final int totalLength,
                                                 final int visibleLength) {
        // Horizontal uses standard pixel math as items usually have fixed widths

        horizontalThumbWidth = Math.min(visibleLength,
                                        (visibleLength * visibleLength) / totalLength);
        // Clamp to minimal size allowed
        horizontalThumbWidth = Math.max(horizontalThumbWidth, minimalThumbSize);

        // Define the Safe Drawing Range for the center of the thumb
        final int safeAreaLeft = marginLeft;
        final int safeAreaRight = recyclerViewWidth - marginRight;
        final float minCenter = safeAreaLeft + (horizontalThumbWidth / 2.0f);
        final float maxCenter = safeAreaRight - (horizontalThumbWidth / 2.0f);

        // Set the final Center X
        if (visibleLength >= totalLength) {
            horizontalThumbCenterX = (int) minCenter;
        } else {
            float percentage = offsetX / (float) (totalLength - visibleLength);
            percentage = Math.max(0f, Math.min(1.0f, percentage));
            horizontalThumbCenterX = (int) (minCenter + (percentage * (maxCenter - minCenter)));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull final RecyclerView recyclerView,
                                         @NonNull final MotionEvent ev) {

        // If we are already dragging, we MUST return true to keep
        // receiving the events (like ACTION_UP) in our onTouchEvent.
        if (state == State.Dragging) {
            return true;
        }

        final boolean handled;
        if (state == State.Visible) {
            final boolean insideVerticalThumb =
                    isPointInsideVerticalThumb(ev.getX(), ev.getY());
            final boolean insideHorizontalThumb =
                    isPointInsideHorizontalThumb(ev.getX(), ev.getY());

            if (ev.getAction() == MotionEvent.ACTION_DOWN
                && (insideVerticalThumb || insideHorizontalThumb)) {
                // Tell parents not to steal the focus now that we've grabbed the thumb
                recyclerView.getParent().requestDisallowInterceptTouchEvent(true);

                if (insideHorizontalThumb) {
                    dragState = DragState.X;
                    horizontalDragX = (int) ev.getX();
                } else if (insideVerticalThumb) {
                    dragState = DragState.Y;
                    verticalDragY = (int) ev.getY();
                }
                setState(State.Dragging);
                handled = true;
            } else if (isPointInsideTrack(ev.getX(), ev.getY())) {
                jumpToPositionFromTrack(ev.getY());
                handled = true;
            } else {
                handled = false;
            }
        } else {
            handled = false;
        }
        return handled;
    }

    @Override
    public void onTouchEvent(@NonNull final RecyclerView recyclerView,
                             @NonNull final MotionEvent me) {
        if (state == State.Hidden) {
            // nothing to do
            return;
        }

        // Tell parents not to steal touch while we are dragging
        if (state == State.Dragging) {
            recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
        }

        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            final boolean insideVerticalThumb =
                    isPointInsideVerticalThumb(me.getX(), me.getY());
            final boolean insideHorizontalThumb =
                    isPointInsideHorizontalThumb(me.getX(), me.getY());

            if (insideVerticalThumb || insideHorizontalThumb) {
                if (insideHorizontalThumb) {
                    dragState = DragState.X;
                    horizontalDragX = (int) me.getX();
                } else if (insideVerticalThumb) {
                    dragState = DragState.Y;
                    verticalDragY = (int) me.getY();
                }
                setState(State.Dragging);
            }

        } else if (me.getAction() == MotionEvent.ACTION_UP && state == State.Dragging) {
            verticalDragY = 0;
            horizontalDragX = 0;
            // Clear DragState BEFORE calling setState
            dragState = DragState.None;
            setState(State.Visible);

        } else if (me.getAction() == MotionEvent.ACTION_MOVE && state == State.Dragging) {
            show();
            if (dragState == DragState.X) {
                horizontalScrollTo(me.getX());
            }
            if (dragState == DragState.Y) {
                verticalScrollTo(me.getY());
            }
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(final boolean disallowIntercept) {
    }

    /**
     * User tapped the scrollbar; we need to jump to the given position.
     * <p>
     * With the exception of calculating the targetPosition,
     * all calculations are identical to {@link #verticalScrollTo(float)}.
     *
     * @param y of the touch event
     */
    private void jumpToPositionFromTrack(final float y) {
        final LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Define the Safe Drawing Range (Matches verticalScrollTo)
        final int safeAreaTop = marginTop;
        final int safeAreaBottom = recyclerViewHeight - marginBottom;

        // Define the travel range for the thumb center
        final float minCenter = safeAreaTop + (verticalThumbHeight / 2.0f);
        final float maxCenter = safeAreaBottom - (verticalThumbHeight / 2.0f);
        final float travelRange = maxCenter - minCenter;

        // Clamp the touch 'y' to the allowed center range
        final float clampedY = Math.max(minCenter, Math.min(maxCenter, y));

        // Calculate Percentage based on travel range
        float percentage = (clampedY - minCenter) / travelRange;
        percentage = Math.max(0.0f, Math.min(1.0f, percentage));

        // Map to Item Count
        @SuppressWarnings("DataFlowIssue")
        final int totalItems = lm.getItemCount();

        // We reuse the 'visible items' proxy to ensure the tap lands the user
        // at a consistent scroll state relative to the bottom.
        final View firstChild = recyclerView.getChildAt(0);
        final float itemHeight = (firstChild != null) ? firstChild.getHeight() : 100f;
        final float estimatedItemsVisible = (float) (safeAreaBottom - safeAreaTop) / itemHeight;

        final int targetPosition = calculateTargetPosition(percentage, totalItems,
                                                           estimatedItemsVisible);

        // Execute the scroll
        lm.scrollToPositionWithOffset(targetPosition, 0);

        // Sync the thumb position and redraw
        verticalThumbCenterY = (int) clampedY;
        requestRedraw();
    }

    private void verticalScrollTo(final float y) {
        final LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Define the Safe Drawing Range
        final int safeAreaTop = marginTop;
        final int safeAreaBottom = recyclerViewHeight - marginBottom;

        // Define the range the thumb center can actually move in
        final float minCenter = safeAreaTop + (verticalThumbHeight / 2.0f);
        final float maxCenter = safeAreaBottom - (verticalThumbHeight / 2.0f);
        final float travelRange = maxCenter - minCenter;

        // Clamp the touch 'y' to the allowed center range
        final float clampedY = Math.max(minCenter, Math.min(maxCenter, y));

        // Calculate Percentage based on travel range
        float percentage = (clampedY - minCenter) / travelRange;
        percentage = Math.max(0.0f, Math.min(1.0f, percentage));

        // Map to Item Count
        @SuppressWarnings("DataFlowIssue")
        final int totalItems = lm.getItemCount();

        // We calculate visible items as a whole number to stabilize the denominator.
        // This stops the jitter when moving between rows of different heights.
        final int firstVisible = lm.findFirstVisibleItemPosition();
        final int lastVisible = lm.findLastVisibleItemPosition();
        final int itemsVisibleNow = Math.max(1, lastVisible - firstVisible);

        final int targetPosition = calculateTargetPosition(percentage, totalItems,
                                                           itemsVisibleNow);

        // Execute the scroll
        lm.scrollToPositionWithOffset(targetPosition, 0);

        // Sync the thumb position and redraw
        verticalThumbCenterY = (int) clampedY;
        requestRedraw();
    }

    private int calculateTargetPosition(final float percentage,
                                        final int totalItems,
                                        final float itemsOnScreen) {
        final int targetPosition;
        if (percentage <= 0.02f) {
            // If we are within 2% of the top, force the first item
            targetPosition = 0;
        } else if (percentage >= 0.98f) {
            // If we are within 2% of the bottom, force the last item
            targetPosition = totalItems - 1;
        } else {
            targetPosition = (int) (percentage * (totalItems - itemsOnScreen));
        }
        return Math.max(0, Math.min(totalItems - 1, targetPosition));
    }

    private void horizontalScrollTo(final float x) {
        // Define the range the thumb is allowed to move in
        final int safeAreaLeft = marginLeft;
        final int safeAreaRight = recyclerViewWidth - marginRight;

        // Define the range the thumb center can actually move in
        final float minCenter = safeAreaLeft + (horizontalThumbWidth / 2.0f);
        final float maxCenter = safeAreaRight - (horizontalThumbWidth / 2.0f);

        // Clamp the touch 'x' to the allowed center range.
        final float clampedX = Math.max(minCenter, Math.min(maxCenter, x));

        if (Math.abs(horizontalThumbCenterX - clampedX) < 2) {
            return;
        }

        // Perform the scroll
        // Use the clamped range (maxCenter - minCenter) as the scrollbarLength
        final int scrollbarLength = (int) (maxCenter - minCenter);
        final int scrollingBy = scrollTo(
                horizontalDragX, clampedX,
                // relative range
                new int[]{0, scrollbarLength},
                recyclerView.computeHorizontalScrollRange(),
                recyclerView.computeHorizontalScrollOffset(),
                recyclerViewWidth);

        if (scrollingBy != 0) {
            recyclerView.scrollBy(scrollingBy, 0);
        }
        horizontalDragX = (int) clampedX;
    }

    private int scrollTo(final float oldDragPos,
                         final float newDragPos,
                         @NonNull final int[] scrollbarRange,
                         final int scrollRange,
                         final int scrollOffset,
                         final int viewLength) {
        final int scrollbarLength = scrollbarRange[1] - scrollbarRange[0];
        if (scrollbarLength == 0) {
            return 0;
        }
        final float percentage = (newDragPos - oldDragPos) / (float) scrollbarLength;
        final int totalPossibleOffset = scrollRange - viewLength;
        final int scrollingBy = (int) (percentage * totalPossibleOffset);
        final int absoluteOffset = scrollOffset + scrollingBy;
        if (absoluteOffset < totalPossibleOffset && absoluteOffset >= 0) {
            return scrollingBy;
        } else {
            return 0;
        }
    }

    private boolean isPointInsideVerticalThumb(final float x,
                                               final float y) {

        // Horizontal Check (respecting RTL and Expanded Touch Area)
        final boolean isInsideX;
        if (isLayoutRTL()) {
            // In RTL, the thumb is on the left. Expand to the right.
            isInsideX = x <= verticalThumbWidth + expandedTouchArea;
        } else {
            // In LTR, the thumb is on the right. Expand to the left.
            isInsideX = x >= recyclerViewWidth - verticalThumbWidth - expandedTouchArea;
        }

        if (!isInsideX) {
            return false;
        }

        // Vertical Check
        // We calculate the top and bottom based on the center.
        // Because mVerticalThumbCenterY is clamped to marginStartBottom/marginStartTop,
        // these bounds will correctly follow the thumb even near rounded corners.
        final float halfHeight = verticalThumbHeight / 2.0f;
        final float topBound = verticalThumbCenterY - halfHeight - expandedTouchArea;
        final float bottomBound = verticalThumbCenterY + halfHeight + expandedTouchArea;

        return y >= topBound && y <= bottomBound;
    }

    private boolean isPointInsideHorizontalThumb(final float x,
                                                 final float y) {
        return y >= recyclerViewHeight - horizontalThumbHeight
               && x >= horizontalThumbCenterX - (float) horizontalThumbWidth / 2
               && x <= horizontalThumbCenterX + (float) horizontalThumbWidth / 2;
    }

    private boolean isPointInsideTrack(final float x,
                                       final float y) {
        // Only register track taps within the Safe Area
        final boolean isInsideY = y >= marginTop
                                  && y <= (recyclerViewHeight - marginBottom);

        final boolean isInsideX;
        if (isLayoutRTL()) {
            isInsideX = x <= verticalTrackWidth;
        } else {
            isInsideX = x >= recyclerViewWidth - verticalTrackWidth;
        }

        return isInsideY && isInsideX;
    }

    private enum State {
        // Scroll thumb not showing
        Hidden,
        // Scroll thumb visible and moving along with the scrollbar
        Visible,
        // Scroll thumb being dragged by user
        Dragging
    }

    private enum DragState {
        None,
        X,
        Y
    }

    private enum AnimationState {
        Out,
        FadingIn,
        In,
        FadingOut
    }

    private final class AnimatorListener
            extends AnimatorListenerAdapter {

        private boolean canceled;

        @Override
        public void onAnimationEnd(final Animator animation) {
            // Cancel is always followed by a new directive, so don't update state.
            if (canceled) {
                canceled = false;
                return;
            }
            if ((float) showHideAnimator.getAnimatedValue() == 0) {
                animationState = AnimationState.Out;
                setState(State.Hidden);
            } else {
                animationState = AnimationState.In;
                requestRedraw();
            }
        }

        @Override
        public void onAnimationCancel(final Animator animation) {
            canceled = true;
        }
    }

    private final class AnimatorUpdater
            implements ValueAnimator.AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(final ValueAnimator valueAnimator) {
            final int alpha = (int) (SCROLLBAR_FULL_OPAQUE
                                     * ((float) valueAnimator.getAnimatedValue()));
            verticalThumbDrawable.setAlpha(alpha);
            verticalTrackDrawable.setAlpha(alpha);
            requestRedraw();
        }
    }
}
