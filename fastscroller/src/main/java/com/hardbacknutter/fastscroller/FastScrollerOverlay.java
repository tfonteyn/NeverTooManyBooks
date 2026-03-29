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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Display a balloon-style overlay, following the scroll bar drag handle.
 * <p>
 * zhanghai: FastScroller; but removed thumb/track handling.
 */
class FastScrollerOverlay
        implements OverlayProvider {

    @NonNull
    private final RecyclerView mRecyclerView;
    @NonNull
    private final TextView mPopupView;
    @NonNull
    private final AnimationHelper mAnimationHelper;
    /** Width of the drag handle; used for positioning. */
    private final int mThumbWidth;

    /** Helper. */
    @NonNull
    private final Rect mTempRect = new Rect();
    @Nullable
    private Rect mUserPadding;
    /** Current status. */
    private boolean mIsDragging;

    private int popupInterval = 5;
    private int previousPopupTextPosition;
    @Nullable
    private CharSequence previousPopupText;

    /**
     * Constructor.
     *
     * @param recyclerView to hook up
     * @param padding      (optional) fixed padding overruling the view's padding
     * @param thumbWidth   Width of the thumb/drag-handle
     * @param popupStyle   for the TextView
     */
    FastScrollerOverlay(@NonNull final RecyclerView recyclerView,
                        @Nullable final Rect padding,
                        final int thumbWidth,
                        @NonNull final Consumer<TextView> popupStyle) {

        final Context context = recyclerView.getContext();

        mRecyclerView = recyclerView;
        mUserPadding = padding;
        mAnimationHelper = new DefaultAnimationHelper(mRecyclerView);

        mThumbWidth = thumbWidth;

        mPopupView = new TextView(context);

        // Gravity is set by the popupStyle
        mPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupStyle.accept(mPopupView);

        final ViewGroupOverlay overlay = mRecyclerView.getOverlay();
        overlay.add(mPopupView);

        mPopupView.setAlpha(0);
    }

    @Override
    public void setInterval(final int interval) {
        popupInterval = interval;
    }

    @Override
    public void setPadding(final int left,
                           @SuppressWarnings("SameParameterValue") final int top,
                           final int right,
                           final int bottom) {
        if (mUserPadding != null && mUserPadding.left == left && mUserPadding.top == top
            && mUserPadding.right == right && mUserPadding.bottom == bottom) {
            return;
        }
        if (mUserPadding == null) {
            mUserPadding = new Rect();
        }
        mUserPadding.set(left, top, right, bottom);
        mRecyclerView.invalidate();
    }

    @NonNull
    private Rect getPadding() {
        if (mUserPadding != null) {
            mTempRect.set(mUserPadding);
        } else {
            mTempRect.set(mRecyclerView.getPaddingLeft(),
                          mRecyclerView.getPaddingTop(),
                          mRecyclerView.getPaddingRight(),
                          mRecyclerView.getPaddingBottom());
        }
        return mTempRect;
    }

    /**
     * Draw the overlay.
     *
     * @param isDragging  flag
     * @param thumbCenter the offset from the top to the centre of the thumb/drag-handle
     *
     * @see <a href="https://github.com/zhanghai/AndroidFastScroll/blob/93af2c0481bba5e1e8ebc1c6437713afe46abfc2/library/src/main/java/me/zhanghai/android/fastscroll/FastScroller.java#L170">
     *         github.com/zhanghai/AndroidFastScroll</a>
     */
    @SuppressLint("RtlHardcoded")
    @Override
    public void showOverlay(final boolean isDragging,
                            final int thumbCenter) {

        if (mIsDragging != isDragging) {
            mIsDragging = isDragging;
            if (mIsDragging) {
                mRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                mAnimationHelper.showPopup(mPopupView);
            } else {
                mAnimationHelper.hidePopup(mPopupView);
            }
        }

        // Are we done?
        if (!mIsDragging) {
            return;
        }

        final RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter = selectAdapter();
        final RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();

        if (!(adapter instanceof PopupTextProvider)
            || !(layoutManager instanceof LinearLayoutManager)) {
            // gone: we will never show it.
            mPopupView.setVisibility(View.GONE);
            return;
        }

        final int position = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) {
            // There wasn't anything before, just bail out
            return;
        }

        final CharSequence text = getCachedPopupText((PopupTextProvider) adapter, position);
        if (text == null || text.length() == 0) {
            // invisible: we're not showing this time, but will likely in the future
            mPopupView.setVisibility(View.INVISIBLE);
            return;
        }

        // we have text to show
        mPopupView.setVisibility(View.VISIBLE);

        final int viewWidth = mRecyclerView.getWidth();
        final int viewHeight = mRecyclerView.getHeight();
        final Rect padding = getPadding();
        final int layoutDirection = mRecyclerView.getLayoutDirection();
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                mPopupView.getLayoutParams();

        preparePopupView(viewWidth, viewHeight, padding, lp, layoutDirection,
                         text);
        performPopupLayout(viewWidth, viewHeight, padding, lp, layoutDirection,
                           thumbCenter);
    }

    /**
     * Select the first adapter which is a {@link PopupTextProvider}.
     *
     * @return adapter
     */
    @Nullable
    private RecyclerView.Adapter<? extends RecyclerView.ViewHolder> selectAdapter() {

        RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<? extends RecyclerView.ViewHolder>)
                        mRecyclerView.getAdapter();

        if (adapter instanceof ConcatAdapter) {
            final Optional<? extends RecyclerView.Adapter<? extends RecyclerView.ViewHolder>>
                    first = ((ConcatAdapter) adapter)
                    .getAdapters()
                    .stream()
                    .filter(a -> a instanceof PopupTextProvider)
                    .findFirst();

            if (first.isPresent()) {
                adapter = first.get();
            }
        }
        return adapter;
    }

    @Nullable
    private CharSequence getCachedPopupText(@NonNull final PopupTextProvider provider,
                                            final int position) {
        // no need to check on previousPopupText being null, as it just
        // means the very first few requests won't get any text... that's fine
        if (Math.abs(position - previousPopupTextPosition) < popupInterval) {
            return previousPopupText;
        }

        final CharSequence text = provider.getPopupText(mRecyclerView.getContext(), position);
        if (text != null && !text.equals(previousPopupText)) {
            mRecyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        previousPopupText = text;
        previousPopupTextPosition = position;
        return previousPopupText;
    }

    private void preparePopupView(final int viewWidth,
                                  final int viewHeight,
                                  @NonNull final Rect padding,
                                  @NonNull final FrameLayout.LayoutParams lp,
                                  final int layoutDirection,
                                  @NonNull final CharSequence text) {
        // Only measure if the text has changed.
        if (Objects.equals(mPopupView.getText(), text)) {
            return;
        }

        mPopupView.setText(text);
        mPopupView.setLayoutDirection(layoutDirection);

        final int widthMeasureSpec = ViewGroup.getChildMeasureSpec(
                View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
                padding.left + padding.right + mThumbWidth
                + lp.leftMargin + lp.rightMargin,
                lp.width);

        final int heightMeasureSpec = ViewGroup.getChildMeasureSpec(
                View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY),
                padding.top + padding.bottom
                + lp.topMargin + lp.bottomMargin,
                lp.height);

        mPopupView.measure(widthMeasureSpec, heightMeasureSpec);
    }

    private void performPopupLayout(final int viewWidth,
                                    final int viewHeight,
                                    @NonNull final Rect padding,
                                    @NonNull final FrameLayout.LayoutParams lp,
                                    final int layoutDirection,
                                    final int thumbCenter) {

        final int popupWidth = mPopupView.getMeasuredWidth();
        final int popupHeight = mPopupView.getMeasuredHeight();

        // Horizontal: Positioning relative to thumb and RTL direction
        final int popupLeft;
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            popupLeft = padding.left + mThumbWidth + lp.leftMargin;
        } else {
            popupLeft = viewWidth - padding.right - mThumbWidth - lp.rightMargin - popupWidth;
        }

        // Vertical: Anchor alignment using VERTICAL_GRAVITY_MASK
        final int popupAnchorY;
        switch (lp.gravity & Gravity.VERTICAL_GRAVITY_MASK) {
            case Gravity.CENTER_VERTICAL:
                // Centre of thumb
                popupAnchorY = popupHeight / 2;
                break;

            case Gravity.BOTTOM:
                // Near the bottom of the thumb
                popupAnchorY = (int) (popupHeight * 0.9f);
                break;

            case Gravity.TOP:
                // Near the top of the thumb
                popupAnchorY = (int) (popupHeight * 0.1f);
                break;

            default:
                // Fallback to centre
                popupAnchorY = popupHeight / 2;
                break;
        }

        final int popupTop = MathUtils.clamp(
                thumbCenter - popupAnchorY,
                padding.top + lp.topMargin,
                viewHeight - padding.bottom - lp.bottomMargin - popupHeight);

        layoutView(mRecyclerView, mPopupView, popupWidth, popupHeight, popupLeft, popupTop);
    }

    /**
     * Layout the popup view.
     *
     * @param parent      the parent of the popup View
     * @param popupView   the popup
     * @param popupWidth  the popup
     * @param popupHeight the popup
     * @param popupLeft   the popup
     * @param popupTop    the popup
     */
    void layoutView(@NonNull final View parent,
                    @NonNull final View popupView,
                    final int popupWidth,
                    final int popupHeight,
                    final int popupLeft,
                    final int popupTop) {
        final int scrollX = parent.getScrollX() + popupLeft;
        final int scrollY = parent.getScrollY() + popupTop;
        popupView.layout(scrollX, scrollY, scrollX + popupWidth, scrollY + popupHeight);
    }

    interface AnimationHelper {

        void showScrollbar(@NonNull View trackView,
                           @NonNull View thumbView);

        void hideScrollbar(@NonNull View trackView,
                           @NonNull View thumbView);

        boolean isScrollbarAutoHideEnabled();

        int getScrollbarAutoHideDelayMillis();

        void showPopup(@NonNull View popupView);

        void hidePopup(@NonNull View popupView);
    }
}
