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
package com.hardbacknutter.nevertoomanybooks.widgets.fastscroller;

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
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

/**
 * Display a balloon-style overlay, following the scroll bar drag handle.
 */
public class FastScrollerOverlay
        implements FastScroller.OverlayProvider {

    @NonNull
    private final RecyclerView mView;
    @NonNull
    private final TextView mPopupView;

    @NonNull
    private final AnimationHelper mAnimationHelper;
    /** Width of the drag handle; used for positioning. */
    private final int mThumbWidth;
    /** Helper. */
    @NonNull
    private final Rect mTempRect = new Rect();
    /** Current status. */
    private boolean mIsDragging;

    /**
     * Constructor.
     *
     * @param view       to hook up
     * @param thumbWidth the width of the thumb/drag-handle
     * @param popupStyle for the TextView
     */
    FastScrollerOverlay(@NonNull final RecyclerView view,
                        final int thumbWidth,
                        @NonNull final Consumer<TextView> popupStyle) {
        Context context = view.getContext();

        mView = view;
        mThumbWidth = thumbWidth;

        mPopupView = new TextView(context);
        mPopupView.setAlpha(0);
        mPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupStyle.accept(mPopupView);

        ViewGroupOverlay overlay = mView.getOverlay();
        overlay.add(mPopupView);

        mAnimationHelper = new DefaultAnimationHelper(mView);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void showOverlay(final boolean isDragging,
                            final int thumbCenter) {

        RecyclerView.Adapter adapter = mView.getAdapter();
        if (!(adapter instanceof FastScroller.PopupTextProvider)) {
            return;
        }

        RecyclerView.LayoutManager layoutManager = mView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }

        int position = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();

        String[] popupLines = ((FastScroller.PopupTextProvider) adapter)
                .getPopupText(mView.getContext(), position);

        boolean hasPopup = popupLines.length > 0 && popupLines[0] != null;
        mPopupView.setVisibility(hasPopup ? View.VISIBLE : View.INVISIBLE);
        if (hasPopup) {
            String popupText = popupLines[0];
            if (popupLines.length > 1) {
                popupText += '\n' + popupLines[1];
            }

            int layoutDirection = mView.getLayoutDirection();
            mPopupView.setLayoutDirection(layoutDirection);

            int viewWidth = mView.getWidth();
            int viewHeight = mView.getHeight();

            mTempRect.set(mView.getPaddingLeft(), mView.getPaddingTop(),
                          mView.getPaddingRight(), mView.getPaddingBottom());
            Rect padding = mTempRect;

            FrameLayout.LayoutParams popupLPs = (FrameLayout.LayoutParams)
                    mPopupView.getLayoutParams();

            // Only need to (re)measure if the text is different.
            if (!Objects.equals(mPopupView.getText(), popupText)) {
                mPopupView.setText(popupText);

                int widthMeasureSpec = ViewGroup.getChildMeasureSpec(
                        View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
                        padding.left + padding.right + mThumbWidth
                        + popupLPs.leftMargin + popupLPs.rightMargin,
                        popupLPs.width);

                int heightMeasureSpec = ViewGroup.getChildMeasureSpec(
                        View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY),
                        padding.top + padding.bottom
                        + popupLPs.topMargin + popupLPs.bottomMargin,
                        popupLPs.height);

                mPopupView.measure(widthMeasureSpec, heightMeasureSpec);
            }

            int popupWidth = mPopupView.getMeasuredWidth();
            int popupHeight = mPopupView.getMeasuredHeight();
            int popupLeft;
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                popupLeft = padding.left + mThumbWidth + popupLPs.leftMargin;
            } else {
                popupLeft = viewWidth - popupWidth
                            - padding.right - mThumbWidth - popupLPs.rightMargin;
            }

            int popupAnchorY;
            switch (popupLPs.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    popupAnchorY = popupHeight / 2;
                    break;

                case Gravity.RIGHT:
                    // RIGHT!
                    popupAnchorY = popupHeight;
                    break;

                case Gravity.LEFT:
                    // LEFT!
                default:
                    popupAnchorY = 0;
                    break;
            }

            int popupTop = MathUtils.clamp(thumbCenter - popupAnchorY,
                                           padding.top + popupLPs.topMargin,
                                           viewHeight - padding.bottom
                                           - popupLPs.bottomMargin - popupHeight);

            layout(mView, mPopupView, popupWidth, popupHeight, popupLeft, popupTop);
        }

        if (mIsDragging != isDragging) {
            mIsDragging = isDragging;

            if (mIsDragging) {
                mView.getParent().requestDisallowInterceptTouchEvent(true);
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
    public void layout(@NonNull final View parent,
                       @NonNull final View popupView,
                       final int popupWidth,
                       final int popupHeight,
                       final int popupLeft,
                       final int popupTop) {
        int scrollX = parent.getScrollX() + popupLeft;
        int scrollY = parent.getScrollY() + popupTop;
        popupView.layout(scrollX, scrollY, scrollX + popupWidth, scrollY + popupHeight);
    }
}
