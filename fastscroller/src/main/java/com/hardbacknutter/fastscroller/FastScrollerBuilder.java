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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This is the glue class which hooks up the {@link RecyclerView} with the actual
 * {@link FastScrollerImpl} and an optional {@link OverlayProvider}.
 * <p>
 * This solves the following Android bugs:
 * <ul>
 *    <li>
 *      Fast scroll drag bar height too short when there are lots of items in the recyclerview.
 *      <a href="https://issuetracker.google.com/issues/64729576">Google issue 64729576</a>
 *      See code at <a href="https://github.com/caarmen/RecyclerViewBug/">HackFastScroller.java</a>
 *    </li>
 *    <li>
 *      <a href="https://stackoverflow.com/questions/47846873">
 *          recyclerview-fast-scroll-thumb-height-too-small-for-large-data-set</a>
 *    </li>
 * </ul>
 * <strong>IMPORTANT:</strong>
 * <ul>
 *     <li>
 *         {@code  android:scrollbarSize} is ignored!
 *         Instead, the size of the passed {@code Drawable} is used.
 *     </li>
 *     <li>
 *         {@code android:scrollbarStyle} is ignored!
 *         Instead, set the RecyclerView {@code padding} sufficiently large to contain
 *         the scrollbar
 *     </li>
 *     <li>
 *         We are deliberately ignoring these <strong>private</strong> attributes
 *         which are used by the broken original RecyclerView/FastScrollerBuilder
 *         <pre>{@code
 *              <dimen name="fastscroll_default_thickness">8dp</dimen>
 *              <dimen name="fastscroll_margin">0dp</dimen>
 *              <dimen name="fastscroll_minimum_range">50dp</dimen>
 *         }</pre>
 *         Our equivalents are:
 *         <pre>{@code
 *              <dimen name="fs_thumb_thickness">8dp</dimen>
 *              <dimen name="fs_margin">0dp</dimen>
 *              <dimen name="fs_minimum_range">50dp</dimen>
 *         }</pre>
 *         and to support the minimum thumb size:
 *         <pre>{@code
 *              <dimen name="fs_thumb_min_size">48dp</dimen>
 *         }</pre>
 *     </li>
 * </ul>
 */
public class FastScrollerBuilder {

    private final DisplayMetrics displayMetrics;
    @Px
    private int thumbThickness;
    @Px
    private int minimumRange;
    @Px
    private int thumbMinSize;
    @Px
    private int expandedTouchArea;
    @NonNull
    private Drawable track;
    @NonNull
    private StateListDrawable thumb;

    @OverlayProviderFactory.OverlayType
    private int overlayType = OverlayProviderFactory.TYPE_MD2;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public FastScrollerBuilder(@NonNull final Context context) {
        // These will resolve to the Material style default drawables.
        track = AttrUtils.getDrawable(context, android.R.attr.fastScrollTrackDrawable);
        thumb = (StateListDrawable)
                AttrUtils.getDrawable(context, android.R.attr.fastScrollThumbDrawable);

        final Resources resources = context.getResources();
        displayMetrics = resources.getDisplayMetrics();

        thumbMinSize = resources.getDimensionPixelSize(R.dimen.fs_thumb_min_size);
        thumbThickness = resources.getDimensionPixelSize(R.dimen.fs_thumb_thickness);
        minimumRange = resources.getDimensionPixelSize(R.dimen.fs_minimum_range);
    }

    /**
     * Set the drawable to be used for the scrollbar track.
     * The default is {@code ?attr/fastScrollTrackDrawable}.
     * Alternatively, set the attribute in your theme.
     *
     * @param drawable to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setTrackDrawable(@NonNull final Drawable drawable) {
        this.track = drawable;
        return this;
    }

    /**
     * Set the drawable to be used for the scrollbar thumb.
     * The default is {@code ?attr/fastScrollThumbDrawable}.
     * Alternatively, set the attribute in your theme.
     *
     * @param drawable to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setThumbDrawable(@NonNull final StateListDrawable drawable) {
        this.thumb = drawable;
        return this;
    }

    @Px
    private int dp2px(@Dimension(unit = Dimension.DP) final int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics);
    }

    /**
     * Set the overlay type to be used.
     * The default is {@link OverlayProviderFactory#TYPE_MD2}.
     *
     * @param overlayType to use; one of the {@code OverlayProviderFactory#TYPE_*} values.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setOverlayType(@OverlayProviderFactory.OverlayType final int overlayType) {
        this.overlayType = overlayType;
        return this;
    }

    /**
     * Set the scrollbar thumb expanded touch-area.
     * This is basically an invisible padding around the thumb for easier grabbing.
     *
     * @param padding in dp
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setExpandedTouchArea(@Dimension(unit = Dimension.DP) final int padding) {
        this.expandedTouchArea = dp2px(padding);
        return this;
    }

    /**
     * Set the scrollbar minimum thumb size.
     * The default is {@link R.dimen#fs_thumb_min_size}.
     * Alternatively, override {@link R.dimen#fs_thumb_min_size} directly.
     *
     * @param minimalSize in dp
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setThumbMinSize(@Dimension(unit = Dimension.DP) final int minimalSize) {
        this.thumbMinSize = dp2px(minimalSize);
        return this;
    }

    /**
     * Set the scrollbar thumb (and track) thickness.
     * The default is {@link R.dimen#fs_thumb_thickness}.
     * Alternatively, override {@link R.dimen#fs_thumb_thickness} directly.
     *
     * @param thickness in dp
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FastScrollerBuilder setThumbThickness(@Dimension(unit = Dimension.DP) final int thickness) {
        this.thumbThickness = dp2px(thickness);
        return this;
    }

    @NonNull
    public FastScrollerBuilder setMinimumRange(@Dimension(unit = Dimension.DP) final int minimumRange) {
        this.minimumRange = dp2px(minimumRange);
        return this;
    }

    /**
     * Attach this FastScrollerBuilder to the given {@link RecyclerView}.
     *
     * @param recyclerView the view
     *
     * @return the fastscroller exposes it's public api only.
     *
     * @throws IllegalArgumentException if the {@link RecyclerView.LayoutManager} is
     *                                  not a {@link LinearLayoutManager}
     */
    @NonNull
    public FastScroller attach(@NonNull final RecyclerView recyclerView)
            throws IllegalArgumentException {

        // Note: do not test the adapter here for being a PopupTextProvider,
        // it can still be null at this time.

        final FastScroller fastScroller = new FastScrollerImpl(
                thumb, track, thumb, track,
                thumbThickness, minimumRange,
                thumbMinSize,
                expandedTouchArea);

        fastScroller.attach(recyclerView);

        @Nullable
        final OverlayProvider overlayProvider = OverlayProviderFactory
                .create(overlayType, thumb.getIntrinsicWidth(), recyclerView);

        fastScroller.setOverlayProvider(overlayProvider);

        final OnApplyWindowInsetsListener listener =
                new ScrollingViewOnApplyWindowInsetsListener(recyclerView, overlayProvider);
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, listener);

        return fastScroller;
    }
}
