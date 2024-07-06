/*
 * @Copyright 2018-2024 HardBackNutter
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
        implements FastScroller.OverlayProvider {

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
        mPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupStyle.accept(mPopupView);

        final ViewGroupOverlay overlay = mRecyclerView.getOverlay();
        overlay.add(mPopupView);

        mPopupView.setAlpha(0);
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
     * @param thumbCenter the offset from the top to the center of the thumb/drag-handle
     *
     * @see <a href="https://github.com/zhanghai/AndroidFastScroll/blob/93af2c0481bba5e1e8ebc1c6437713afe46abfc2/library/src/main/java/me/zhanghai/android/fastscroll/FastScroller.java#L170">
     *         github.com/zhanghai/AndroidFastScroll</a>
     */
    @SuppressLint("RtlHardcoded")
    @Override
    public void showOverlay(final boolean isDragging,
                            final int thumbCenter) {

        RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<? extends RecyclerView.ViewHolder>)
                        mRecyclerView.getAdapter();

        if (adapter instanceof ConcatAdapter) {
            final Optional<? extends RecyclerView.Adapter<? extends RecyclerView.ViewHolder>>
                    first = ((ConcatAdapter) adapter)
                    .getAdapters()
                    .stream()
                    .filter(a -> a instanceof FastScroller.PopupTextProvider)
                    .findFirst();

            if (first.isPresent()) {
                adapter = first.get();
            }
        }

        if (!(adapter instanceof FastScroller.PopupTextProvider)) {
            return;
        }

        final RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }

        final int position = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        final CharSequence[] popupLines = ((FastScroller.PopupTextProvider) adapter)
                .getPopupText(position);

        // Do we have at least one line of text ?
        final boolean hasPopup = popupLines != null
                                 && popupLines.length > 0
                                 && popupLines[0] != null;

        mPopupView.setVisibility(hasPopup ? View.VISIBLE : View.INVISIBLE);
        if (hasPopup) {
            final StringBuilder popupText = new StringBuilder(popupLines[0]);
            if (popupLines.length > 1) {
                for (int line = 1; line < popupLines.length; line++) {
                    if (popupLines[line] != null) {
                        popupText.append('\n').append(popupLines[line]);
                    }
                }
            }

            final int layoutDirection = mRecyclerView.getLayoutDirection();
            mPopupView.setLayoutDirection(layoutDirection);

            final boolean isLayoutRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL;
            final int viewWidth = mRecyclerView.getWidth();
            final int viewHeight = mRecyclerView.getHeight();

            final Rect padding = getPadding();

            final FrameLayout.LayoutParams popupLayoutParams = (FrameLayout.LayoutParams)
                    mPopupView.getLayoutParams();

            // Only need to (re)measure if the text is different.
            if (!Objects.equals(mPopupView.getText(), popupText.toString())) {
                mPopupView.setText(popupText.toString());

                final int widthMeasureSpec = ViewGroup.getChildMeasureSpec(
                        View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
                        padding.left + padding.right + mThumbWidth
                        + popupLayoutParams.leftMargin + popupLayoutParams.rightMargin,
                        popupLayoutParams.width);

                final int heightMeasureSpec = ViewGroup.getChildMeasureSpec(
                        View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY),
                        padding.top + padding.bottom
                        + popupLayoutParams.topMargin + popupLayoutParams.bottomMargin,
                        popupLayoutParams.height);

                mPopupView.measure(widthMeasureSpec, heightMeasureSpec);
            }

            final int popupWidth = mPopupView.getMeasuredWidth();
            final int popupHeight = mPopupView.getMeasuredHeight();
            final int popupLeft;
            if (isLayoutRtl) {
                popupLeft = padding.left + mThumbWidth + popupLayoutParams.leftMargin;
            } else {
                popupLeft = viewWidth - padding.right - mThumbWidth - popupLayoutParams.rightMargin
                            - popupWidth;
            }

            final int popupAnchorY;
            switch (popupLayoutParams.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    popupAnchorY = popupHeight / 2;
                    break;

                case Gravity.RIGHT:
                    // RIGHT! not end!
                    popupAnchorY = popupHeight;
                    break;

                case Gravity.LEFT:
                    // LEFT! not start!
                default:
                    popupAnchorY = 0;
                    break;
            }

            // zhanghai: thumbCenter = thumbTop + thumbAnchorY
            final int popupTop = MathUtils.clamp(thumbCenter - popupAnchorY,
                                                 padding.top + popupLayoutParams.topMargin,
                                                 viewHeight - padding.bottom
                                                 - popupLayoutParams.bottomMargin - popupHeight);

            layoutView(mRecyclerView, mPopupView, popupWidth, popupHeight, popupLeft, popupTop);
        }

        if (mIsDragging != isDragging) {
            mIsDragging = isDragging;

            if (mIsDragging) {
                mRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
            }

            if (mIsDragging) {
                mAnimationHelper.showPopup(mPopupView);
            } else {
                mAnimationHelper.hidePopup(mPopupView);
            }
        }
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
