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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;

import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Cover Scaling.
 * <strong>Never change the 'scale' values</strong>, they get stored in the db.
 * <p>
 * These values are used as the index into a resource array.
 * <p>
 * FIXME: our use of {@link Configuration#screenWidthDp} will break on dual-screen setups
 * (but who can afford those ...)
 * From {@link Configuration} docs:
 * <pre>
 *     if the app is spanning both screens of a dual-screen device
 *     (with the screens side by side), {@code screenWidthDp} represents the
 *     width of both screens
 * </pre>
 */
public enum CoverScale {
    /** Don't show the cover at all. */
    Hidden(0),
    Small(1),
    /**
     * Medium aka Normal; the default.
     * This size "should" be the ideal size for the BoB screen regardless of screen size.
     */
    Medium(2),
    Large(3),
    /**
     * Represented to the user (in preferences screen) as X-large.
     * In BoB grid-mode this will:
     * - portrait: 1 image fills up the entire screen width.
     * - landscape: 2 images side by side fills up the entire screen width
     */
    MAX(4);

    public static final CoverScale DEFAULT = Medium;

    private final int scale;

    CoverScale(final int scale) {
        this.scale = scale;
    }

    @NonNull
    static CoverScale byId(final int id) {
        if (id > MAX.scale) {
            return MAX;
        } else if (id < Hidden.scale) {
            return Hidden;
        } else {
            return Arrays.stream(values()).filter(v -> v.scale == id).findAny().orElse(DEFAULT);
        }
    }

    public int getScale() {
        return scale;
    }

    @NonNull
    public CoverScale larger() {
        final int next = MathUtils.clamp(scale + 1, Small.scale, MAX.scale);
        return values()[next];
    }

    @NonNull
    public CoverScale smaller() {
        final int next = MathUtils.clamp(scale - 1, Small.scale, MAX.scale);
        return values()[next];
    }

    /**
     * Calculate the maximum width in pixels.
     *
     * @param context Current context
     * @param layout  mode for which to lookup the width
     *
     * @return max width in pixels
     */
    @Dimension
    public int getMaxWidthInPixels(@NonNull final Context context,
                                   @NonNull final Style.Layout layout) {
        if (this == Hidden) {
            return 0;
        }
        if (this == MAX && layout == Style.Layout.Grid) {
            // Calculate depending on the available screen width.
            final Configuration configuration = context.getResources().getConfiguration();
            final int orientation = configuration.orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // In landscape, half.
                return getScreenWidthInPixels(context) / 2;
            } else {
                // In portrait, the entire screen width
                return getScreenWidthInPixels(context);
            }
        }

        // Use an indexed lookup to fixed values depending on "sw" device width.
        final TypedArray coverSizes = context
                .getResources().obtainTypedArray(R.array.cover_max_width);
        try {
            return coverSizes.getDimensionPixelSize(scale, 0);
        } finally {
            coverSizes.recycle();
        }
    }

    @Dimension
    private int getScreenWidthInPixels(@NonNull final Context context) {
        final Resources res = context.getResources();
        final int screenWidthDp = res.getConfiguration().screenWidthDp;

        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                                    screenWidthDp,
                                                    res.getDisplayMetrics()));
    }

    /**
     * Use the available screen width and the scale to calculate the optimal
     * span-count for use by the BoB {@link Style.Layout#Grid} mode.
     *
     * @param context Current context
     *
     * @return span count
     */
    @IntRange(from = 1)
    public int getGridSpanCount(@NonNull final Context context) {
        final int spanCount;
        final Configuration configuration = context.getResources().getConfiguration();
        if (this == MAX) {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                spanCount = 2;
            } else {
                spanCount = 1;
            }
        } else {
            // Calculate depending on the available screen width.
            final int coverMaxSizeInPixels = getMaxWidthInPixels(context, Style.Layout.Grid);
            spanCount = (int) Math.floor((double) getScreenWidthInPixels(context)
                                         / coverMaxSizeInPixels);
        }

        // Sanity check ... if the developer (that'll be me...) made a boo-boo...
        // the coverMaxSizeInPixels could be smaller than the screen-width.
        if (spanCount < 0) {
            return 1;
        }
        return spanCount;
    }
}
