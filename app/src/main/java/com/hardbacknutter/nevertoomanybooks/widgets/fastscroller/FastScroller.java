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
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Fast scroll drag bar height too short when there are lots of items in the recyclerview.
 * <a href="https://issuetracker.google.com/issues/64729576">64729576</a>
 * <a href="https://github.com/caarmen/RecyclerViewBug/">HackFastScroller.java</a>
 *
 * <a href="https://stackoverflow.com/questions/47846873/recyclerview-fast-scroll-thumb-height-too-small-for-large-data-set">
 * stackoverflow</a>
 */
public class FastScroller {

    /**
     * Constructor.
     *
     * @param recyclerView the View to hook up
     */
    public static void attach(@NonNull final RecyclerView recyclerView) {

        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            throw new IllegalArgumentException("Not a LinearLayoutManager");
        }

        final Context context = recyclerView.getContext();
        final StateListDrawable verticalThumbDrawable = (StateListDrawable)
                context.getDrawable(R.drawable.fastscroll_thumb);
        final Drawable verticalTrackDrawable =
                context.getDrawable(R.drawable.fastscroll_track);
        final StateListDrawable horizontalThumbDrawable = (StateListDrawable)
                context.getDrawable(R.drawable.fastscroll_thumb);
        final Drawable horizontalTrackDrawable =
                context.getDrawable(R.drawable.fastscroll_track);

        final Resources resources = context.getResources();
        //noinspection ConstantConditions
        final FastScrollerImpl fastScroller = new FastScrollerImpl(
                recyclerView,
                verticalThumbDrawable, verticalTrackDrawable,
                horizontalThumbDrawable, horizontalTrackDrawable,
                resources.getDimensionPixelSize(R.dimen.cfs_default_thickness),
                resources.getDimensionPixelSize(R.dimen.cfs_minimum_range),
                resources.getDimensionPixelOffset(R.dimen.cfs_margin));


        //Note: do not test the adapter here for being a PopupTextProvider,
        // it can still be null.
        final OverlayProvider overlay;
        int thumbWidth = verticalThumbDrawable.getIntrinsicWidth();
        switch (getFastScrollerOverlayStyle()) {
            case OverlayProvider.STYLE_CLASSIC: {
                overlay = new ClassicOverlay(recyclerView, thumbWidth);
                break;
            }
            case OverlayProvider.STYLE_MD1: {
                overlay = new FastScrollerOverlay(recyclerView, thumbWidth, PopupStyles.DEFAULT);
                break;
            }
            case OverlayProvider.STYLE_MD2: {
                overlay = new FastScrollerOverlay(recyclerView, thumbWidth, PopupStyles.MD2);
                break;
            }

            default:
                throw new IllegalArgumentException();
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
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return context.getResources().getColor(tv.resourceId, theme);
    }

    /** FIXME: Compile time set FastScroller overlay style... maybe make a real preference ? */
    @OverlayProvider.Style
    private static int getFastScrollerOverlayStyle() {
        // return FastScroller.OverlayProvider.STYLE_CLASSIC;
        return OverlayProvider.STYLE_MD1;
        // return FastScroller.OverlayProvider.STYLE_MD2;
    }

    /**
     * The adapter should implement this interface.
     * The OverlayProvider will call the method to get the text to display.
     */
    public interface PopupTextProvider {

        /**
         * Get the popup text lines for the given position.
         *
         * @param position to use
         *
         * @return an array with the lines. The length of the array is variable.
         * <strong>CAN RETURN {@code null}</strong>
         */
        @Nullable
        String[] getPopupText(int position);
    }

    public interface OverlayProvider {

        /** Classic BC. */
        int STYLE_CLASSIC = 0;
        /** Material Design. */
        int STYLE_MD1 = 1;
        /** Material Design 2. */
        int STYLE_MD2 = 2;

        /**
         * Called to draw the overlay.
         *
         * @param isDragging  flag
         * @param thumbCenter the offset from the top to the center of the thumb/drag-handle
         */
        void showOverlay(boolean isDragging,
                         int thumbCenter);

        @IntDef({STYLE_CLASSIC, STYLE_MD1, STYLE_MD2})
        @Retention(RetentionPolicy.SOURCE)
        @interface Style {

        }
    }
}
