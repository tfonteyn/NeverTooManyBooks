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
package com.hardbacknutter.fastscroller;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This is the glue class which hooks up the {@link RecyclerView} with the actual
 * {@link FastScrollerImpl} and an optional {@link OverlayProvider}.
 * <p>
 * Fast scroll drag bar height too short when there are lots of items in the recyclerview.
 * <a href="https://issuetracker.google.com/issues/64729576">64729576</a>
 * <a href="https://github.com/caarmen/RecyclerViewBug/">HackFastScroller.java</a>
 * <p>
 * <a href="https://stackoverflow.com/questions/47846873">
 * recyclerview-fast-scroll-thumb-height-too-small-for-large-data-set</a>
 * <p>
 * <strong>IMPORTANT:</strong>
 *     <ul>
 *     <li>{@code  android:scrollbarSize} is ignored!
 *     Instead the size of the passed {@code Drawable} is used.</li>
 *      <li>{@code android:scrollbarStyle} is ignored!
 *      Instead set {@code padding} sufficiently large to contain the scrollbar</li>
 *     </ul>
 * <p>
 * ENHANCE: move the dimen settings to a declarable style,
 * and read them from the xml definition of a RecyclerView
 * <pre>{@code
 * <!-- NOT IMPLEMENTED YET -->
 * <declare-styleable name="FastScroller">
 * <!-- Drawables come from system attributes:
 *      "android.R.attr.fastScrollTrackDrawable"
 *      "android.R.attr.fastScrollThumbDrawable"
 * -->
 * <!-- RecyclerView/FastScroller: R.dimen.fastscroll_default_thickness -->
 * <attr name="fsThickness" format="dimension" />
 *
 * <!-- RecyclerView/FastScroller: R.dimen.fastscroll_minimum_range -->
 * <attr name="fsMinRange" format="dimension" />
 *
 * <!-- RecyclerView/FastScroller: R.dimen.fastscroll_margin -->
 * <attr name="fsMargin" format="dimension" />
 *
 * <!-- custom: absolute minimum size of the thumb -->
 * <attr name="fsMinThumbSize" format="dimension" />
 *
 * </declare-styleable>
 * }
 * </pre>
 */
public final class FastScroller {

    private FastScroller() {
    }

    /**
     * Constructor.
     *
     * @param recyclerView the View to hook up
     * @param overlayType  Optional overlay
     */
    public static void attach(@NonNull final RecyclerView recyclerView,
                              @OverlayProviderFactory.OverlayType final int overlayType) {

        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            throw new IllegalArgumentException("Not a LinearLayoutManager");
        }

        //Note: do not test the adapter here for being a PopupTextProvider,
        // it can still be null.

        final Context context = recyclerView.getContext();

        // These will resolve to the Material style default drawables.
        final Drawable track = AttrUtils
                .getDrawable(context, android.R.attr.fastScrollTrackDrawable);
        final Drawable thumb = AttrUtils
                .getDrawable(context, android.R.attr.fastScrollThumbDrawable);

        final StateListDrawable thumbDrawable = (StateListDrawable) thumb;

        final Resources resources = context.getResources();
        final FastScrollerImpl fastScroller = new FastScrollerImpl(
                recyclerView, thumbDrawable, track, thumbDrawable, track,
                resources.getDimensionPixelSize(R.dimen.fs_default_thickness),
                resources.getDimensionPixelSize(R.dimen.fs_minimum_range),
                resources.getDimensionPixelOffset(R.dimen.fs_margin),
                resources.getDimensionPixelSize(R.dimen.fs_minimal_thumb_size)
        );

        final OverlayProvider overlay = OverlayProviderFactory
                .create(overlayType, thumbDrawable.getIntrinsicWidth(), recyclerView);

        fastScroller.setOverlayProvider(overlay);

        recyclerView.setOnApplyWindowInsetsListener(
                new ScrollingViewOnApplyWindowInsetsListener(recyclerView, overlay));
    }


    /**
     * The adapter should implement this interface.
     * The OverlayProvider will call the method to get the text to display.
     */
    @FunctionalInterface
    public interface PopupTextProvider {

        /**
         * Get the popup text lines for the given position.
         *
         * @param position to use
         *
         * @return an array with the lines. The length of the array is variable.
         *         The array itself <strong>CAN BE {@code null}</strong>.
         *         and individual lines in the array <strong>CAN BE {@code null}</strong>.
         */
        @Nullable
        CharSequence[] getPopupText(int position);
    }

    public interface OverlayProvider {

        /**
         * Called to draw the overlay.
         *
         * @param isDragging  flag
         * @param thumbCenter the offset from the top to the center of the thumb/drag-handle
         */
        void showOverlay(boolean isDragging,
                         int thumbCenter);

        /**
         * Set the padding.
         *
         * @param left   padding
         * @param top    padding
         * @param right  padding
         * @param bottom padding
         */
        void setPadding(int left,
                        int top,
                        int right,
                        int bottom);

    }
}
