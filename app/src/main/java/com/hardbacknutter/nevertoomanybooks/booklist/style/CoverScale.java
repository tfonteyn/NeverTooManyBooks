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
    /**
     * A standard paperback measures 17.5cm x 10.6cm,
     * which gives us a 5/3 ratio between height and width.
     * <p>
     * i.e.: height = width / 0.6
     */
    public static final float HW_RATIO = 0.6f;

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
            final Resources res = context.getResources();
            // Calculate depending on the available screen width.
            final double screenWidthInPx = Math.floor(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                              res.getConfiguration().screenWidthDp,
                                              res.getDisplayMetrics()));
            if (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // In landscape, half.
                return (int) (screenWidthInPx / 2);
            } else {
                // In portrait, the entire screen width
                return (int) screenWidthInPx;
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
        final Resources res = context.getResources();

        if (this == MAX) {
            if (res.getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
                return 2;
            } else {
                // Configuration.ORIENTATION_PORTRAIT
                return 1;
            }
        } else if (this != Hidden) {
            // Calculate depending on the available screen width.
            final TypedArray coverSizes = res.obtainTypedArray(R.array.cover_max_width);
            try {
                final int screenWidthDp = res.getConfiguration().screenWidthDp;
                // Multiply by 0.6 as the values in the resource are optimized for list-mode,
                // where we aim to fill up 1/3 of the width with the image, and 2/3 with text.
                // The 0.6 could likely be tuned on a screen size basis... but the differences
                // will be minimal hence not bothering for now.
                final float coverWidthDp = 0.6f * coverSizes.getDimension(scale, 1);
                return (int) Math.floor(screenWidthDp / coverWidthDp);
            } finally {
                coverSizes.recycle();
            }
        }
        // Hidden.... we should never get here... flw
        return 1;
    }

}
