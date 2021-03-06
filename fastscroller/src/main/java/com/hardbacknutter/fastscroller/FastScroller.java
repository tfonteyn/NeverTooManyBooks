/*
 * @Copyright 2018-2021 HardBackNutter
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

/**
 * This is the glue class which hooks up the RecyclerView with the actual
 * FastScroller implementation and an optional Overlay provider.
 * <p>
 * Fast scroll drag bar height too short when there are lots of items in the recyclerview.
 * <a href="https://issuetracker.google.com/issues/64729576">64729576</a>
 * <a href="https://github.com/caarmen/RecyclerViewBug/">HackFastScroller.java</a>
 *
 * <a href="https://stackoverflow.com/questions/47846873">
 * recyclerview-fast-scroll-thumb-height-too-small-for-large-data-set</a>
 */
public final class FastScroller {

    private FastScroller() {
    }

    /**
     * Constructor.
     * <p>
     * ENHANCE: move the drawable and dimen settings to a declarable style,
     * and read them from the xml definition of a RecyclerView
     *
     * @param recyclerView the View to hook up
     * @param overlayType  Optional overlay
     */
    public static void attach(@NonNull final RecyclerView recyclerView,
                              @OverlayProvider.Style final int overlayType) {

        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            throw new IllegalArgumentException("Not a LinearLayoutManager");
        }

        //Note: do not test the adapter here for being a PopupTextProvider,
        // it can still be null.

        final Context context = recyclerView.getContext();

        // These will resolve to the Material style default drawables.
        final Drawable track = getDrawable(context, android.R.attr.fastScrollTrackDrawable);
        final Drawable thumb = getDrawable(context, android.R.attr.fastScrollThumbDrawable);

        final StateListDrawable thumbDrawable = (StateListDrawable) thumb;

        final Resources resources = context.getResources();
        final FastScrollerImpl fastScroller = new FastScrollerImpl(
                recyclerView, thumbDrawable, track, thumbDrawable, track,
                resources.getDimensionPixelSize(R.dimen.fs_default_thickness),
                resources.getDimensionPixelSize(R.dimen.fs_minimum_range),
                resources.getDimensionPixelOffset(R.dimen.fs_margin),
                resources.getDimensionPixelSize(R.dimen.fs_minimal_thumb_size)
        );

        final OverlayProvider overlay;
        switch (overlayType) {
            case OverlayProvider.STYLE_MD2:
                overlay = new FastScrollerOverlay(recyclerView, null, thumbDrawable,
                                                  PopupStyles.MD2);
                break;

            case OverlayProvider.STYLE_MD1:
                overlay = new FastScrollerOverlay(recyclerView, null, thumbDrawable,
                                                  PopupStyles.MD);
                break;

            case OverlayProvider.STYLE_STATIC:
                overlay = new ClassicOverlay(recyclerView, null, thumbDrawable);
                break;

            case OverlayProvider.STYLE_NONE:
            default:
                overlay = null;
                break;
        }
        fastScroller.setOverlayProvider(overlay);

        recyclerView.setOnApplyWindowInsetsListener(
                new ScrollingViewOnApplyWindowInsetsListener(recyclerView, overlay));
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
                           @SuppressWarnings("SameParameterValue") @AttrRes final int attr)
            throws Resources.NotFoundException {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        return context.getResources().getColor(tv.resourceId, theme);
    }

    /**
     * Get a Drawable for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A Drawable
     *
     * @throws Resources.NotFoundException if the given ID does not exist.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private static Drawable getDrawable(@NonNull final Context context,
                                        @AttrRes final int attr)
            throws Resources.NotFoundException {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        return context.getResources().getDrawable(tv.resourceId, theme);
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

        /** Don't show any overlay. */
        int STYLE_NONE = 0;

        /** Show a static (non-moving) overlay. Classic BC. */
        int STYLE_STATIC = 1;
        /** Dynamic Material Design. */
        int STYLE_MD1 = 2;
        /** Dynamic Material Design 2. */
        int STYLE_MD2 = 3;

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

        @IntDef({STYLE_NONE, STYLE_STATIC, STYLE_MD1, STYLE_MD2})
        @Retention(RetentionPolicy.SOURCE)
        @interface Style {

        }
    }
}
