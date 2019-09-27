/*
 * @Copyright 2019 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.widgets.cfs;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;

/**
 * see package-info.java
 */
public class CFSRecyclerView
        extends RecyclerView {

    /** The overlay. */
    @Nullable
    private FastScrollerOverlay mOverlay;

    /**
     * Constructor used when instantiating Views programmatically.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     */
    public CFSRecyclerView(@NonNull final Context context) {
        this(context, null);
    }

    /**
     * Constructor used by the LayoutInflater.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public CFSRecyclerView(@NonNull final Context context,
                           @Nullable final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor used by the LayoutInflater if there was a 'style' attribute.
     *
     * @param context      The Context the view is running in, through which it can
     *                     access the current theme, resources, etc.
     * @param attrs        The attributes of the XML tag that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     */
    public CFSRecyclerView(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (attrs != null) {
            int defStyleRes = 0;
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CFSRecyclerView,
                                                          defStyleAttr, defStyleRes);

            boolean enableFastScroller =
                    a.getBoolean(R.styleable.CFSRecyclerView_cfsEnabled, false);

            if (enableFastScroller) {
                StateListDrawable verticalThumbDrawable =
                        (StateListDrawable) a.getDrawable(
                                R.styleable.CFSRecyclerView_cfsVerticalThumbDrawable);
                StateListDrawable horizontalThumbDrawable =
                        (StateListDrawable) a.getDrawable(
                                R.styleable.CFSRecyclerView_cfsHorizontalThumbDrawable);

                Drawable verticalTrackDrawable =
                        a.getDrawable(R.styleable.CFSRecyclerView_cfsVerticalTrackDrawable);
                Drawable horizontalTrackDrawable =
                        a.getDrawable(R.styleable.CFSRecyclerView_cfsHorizontalTrackDrawable);

                if (verticalThumbDrawable == null || verticalTrackDrawable == null
                    || horizontalThumbDrawable == null || horizontalTrackDrawable == null) {
                    throw new IllegalArgumentException(
                            "Trying to set fast scroller without all required drawables.");
                }

                Resources resources = getContext().getResources();
                new CFSFastScroller(this,
                                    verticalThumbDrawable, verticalTrackDrawable,
                                    horizontalThumbDrawable, horizontalTrackDrawable,
                                    resources.getDimensionPixelSize(R.dimen.cfs_default_thickness),
                                    resources.getDimensionPixelSize(R.dimen.cfs_minimum_range),
                                    resources.getDimensionPixelOffset(R.dimen.cfs_margin));
                // optional
                Drawable overlayDrawable =
                        a.getDrawable(R.styleable.CFSRecyclerView_cfsOverlayDrawable);
                if (overlayDrawable != null) {
                    mOverlay = new FastScrollerOverlay(getContext(), overlayDrawable);
                }
            }
            a.recycle();
        }
    }

    /**
     * Developer sanity.
     */
    @Override
    public void setLayoutManager(@Nullable final LayoutManager layout) {
        if (!(layout instanceof LinearLayoutManager)) {
            throw new IllegalArgumentException("Not a LinearLayoutManager");
        }
        super.setLayoutManager(layout);
    }

    /**
     * Proxy method for drawing the overlay.
     */
    public void drawIndexerOverlay(@NonNull final Canvas c,
                                   @NonNull final RecyclerView.State state) {
        if (mOverlay != null) {
            mOverlay.onDrawOver(c, this, state);
        }
    }
}
