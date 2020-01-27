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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Fast scroll drag bar height too short when there are lots of items in the recyclerview
 * <a href="https://issuetracker.google.com/issues/64729576">64729576</a>
 * <a href="https://github.com/caarmen/RecyclerViewBug/">HackFastScroller.java</a>
 *
 * <a href="https://stackoverflow.com/questions/47846873/recyclerview-fast-scroll-thumb-height-too-small-for-large-data-set">
 * stackoverflow</a>
 */
public class FastScroller {

    //    private static final OverlayStyle FS_OVERLAY = OverlayStyle.Classic;
    private static final OverlayStyle FS_OVERLAY = OverlayStyle.MD2;
//    private static final OverlayStyle FS_OVERLAY = OverlayStyle.MD1;

    /**
     * Constructor.
     *
     * @param recyclerView the View to hook up
     */
    public static void init(@NonNull final RecyclerView recyclerView) {

        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            throw new IllegalArgumentException("Not a LinearLayoutManager");
        }

        Context context = recyclerView.getContext();
        StateListDrawable verticalThumbDrawable = (StateListDrawable)
                context.getDrawable(R.drawable.fastscroll_thumb);
        Drawable verticalTrackDrawable =
                context.getDrawable(R.drawable.fastscroll_track);
        StateListDrawable horizontalThumbDrawable = (StateListDrawable)
                context.getDrawable(R.drawable.fastscroll_thumb);
        Drawable horizontalTrackDrawable =
                context.getDrawable(R.drawable.fastscroll_track);

        Resources resources = context.getResources();
        //noinspection ConstantConditions
        FastScrollerImpl fastScroller = new FastScrollerImpl(
                recyclerView,
                verticalThumbDrawable, verticalTrackDrawable,
                horizontalThumbDrawable, horizontalTrackDrawable,
                resources.getDimensionPixelSize(R.dimen.cfs_default_thickness),
                resources.getDimensionPixelSize(R.dimen.cfs_minimum_range),
                resources.getDimensionPixelOffset(R.dimen.cfs_margin));


        //Note: do not test the adapter here for being a PopupTextProvider,
        // it can still be null.
        OverlayProvider overlay = null;
        int thumbWidth = verticalThumbDrawable.getIntrinsicWidth();
        switch (FS_OVERLAY) {
            case Classic:
                overlay = new ClassicOverlay(recyclerView, thumbWidth, PopupStyles.CLASSIC);
                break;

            case MD2: {
                overlay = new FastScrollerOverlay(recyclerView, thumbWidth, PopupStyles.MD2);
                break;
            }
            case MD1: {
                overlay = new FastScrollerOverlay(recyclerView, thumbWidth,
                                                  PopupStyles.DEFAULT);
                break;
            }
        }

        fastScroller.setOverlayProvider(overlay);
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    @ColorInt
    static int getColorInt(@NonNull final Context context,
                           @SuppressWarnings("SameParameterValue") @AttrRes final int attr) {
        Resources.Theme theme = context.getTheme();
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return context.getResources().getColor(tv.resourceId, theme);
    }

    private enum OverlayStyle {
        Classic,
        MD2,
        MD1
    }

    /**
     * The adapter should implement this interface.
     * The OverlayProvider can then call the method to get the text to display.
     */
    public interface PopupTextProvider {

        @NonNull
        String[] getPopupText(@NonNull Context context,
                              int position);
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
    }
}
