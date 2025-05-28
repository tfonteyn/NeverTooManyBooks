/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.math.MathUtils;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewSize;

/**
 * Cover Scaling.
 * <strong>Never change the 'scale' values</strong>, they get stored in the db.
 * <p>
 * These values are used as the index into a resource array.
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
     * <p>
     * In {@link ScreenLayout#Grid} mode this will result in:
     * <ul>
     *     <li>portrait : 1 image fills up the entire screen width.</li>
     *     <li>landscape: 2 images side by side fills up the entire screen width</li>
     * </ul>
     */
    Maximum(4);

    public static final CoverScale DEFAULT = Medium;
    /**
     * A standard paperback measures 17.5cm x 10.6cm,
     * which gives us a 5/3 ratio between height and width.
     * <p>
     * i.e.: height = width / 0.6
     */
    public static final float HW_RATIO = 0.6f;

    private static final ImageViewSize HIDDEN = new ImageViewSize(0, 0);

    private final int id;

    CoverScale(final int id) {
        this.id = id;
    }

    /**
     * Lookup by id.
     * <p>
     * Import/Export and database usage only.
     *
     * @param id to lookup
     *
     * @return type; or {@link #DEFAULT} for any invalid id.
     */
    @NonNull
    public static CoverScale byId(final int id) {
        if (id > Maximum.id) {
            return Maximum;
        } else if (id < Hidden.id) {
            return Hidden;
        } else {
            return Arrays.stream(values())
                         .filter(v -> v.id == id)
                         .findFirst()
                         .orElse(DEFAULT);
        }
    }

    @Px
    private static int getWindowWidthInPx(@NonNull final Context context) {
        return WindowMetricsCalculator
                .getOrCreate()
                .computeCurrentWindowMetrics(context)
                .getBounds()
                .width();
    }

    /**
     * Get the internal id.
     * <p>
     * Import/Export and database usage only.
     *
     * @return id
     */
    public int getId() {
        return id;
    }

    /**
     * Get a short description of this scale.
     *
     * @param context Current context
     *
     * @return the label
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getResources().getStringArray(R.array.lbl_style_cover_scale)[id];
    }

    /**
     * Increase and get the new scale.
     *
     * @return the next larger scale
     */
    @NonNull
    public CoverScale larger() {
        final int next = MathUtils.clamp(id + 1, Small.id, Maximum.id);
        return values()[next];
    }

    /**
     * Decrease and get the new scale.
     *
     * @return the next smaller scale
     */
    @NonNull
    public CoverScale smaller() {
        final int next = MathUtils.clamp(id - 1, Small.id, Maximum.id);
        return values()[next];
    }

    /**
     * Use an indexed lookup to fixed values depending on "sw" device width
     * and the given layout.
     *
     * @param res    for lookups
     * @param layout for auto-sizing
     *
     * @return width in pixels
     *
     * @throws IllegalArgumentException (debug)
     */
    @Px
    private int lookup(@NonNull final Resources res,
                       @NonNull final ScreenLayout layout) {

        final int width;
        final TypedArray coverSizes = res.obtainTypedArray(R.array.cover_max_width);
        try {
            width = coverSizes.getDimensionPixelSize(id, 0);
        } finally {
            coverSizes.recycle();
        }
        switch (layout) {
            case List:
                return width;
            case Grid:
                // The multiplier used here is NOT related to HW_RATIO!
                //
                // Multiply the cover-width by 0.6 as the values in the resource are
                // optimized for list-mode where we aim to fill up 1/3 of the width
                // with the image, and 2/3 with text.
                // This could likely be tuned on a screen size basis... but the differences
                // will be minimal hence not bothering for now.
                //
                // We experimented with values 1.0, 0.8, 0.7, 0.6, 0.5...
                // Using 0.6 gives the "nicest" spread of the number of pictures on a row
                // depending on scale setting. See end of class for examples.
                return width * 10 / 6;
            default:
                throw new IllegalArgumentException(layout.toString());
        }
    }

    /**
     * Calculate the maximum width in pixels, depending on the available screen width.
     *
     * @param context Current context
     * @param layout  mode for which to lookup the width
     *
     * @return max size in pixels
     */
    @NonNull
    public ImageViewSize getMaxSizeInPixels(@NonNull final Context context,
                                            @NonNull final ScreenLayout layout) {
        if (this == Hidden) {
            return HIDDEN;
        }

        final Resources res = context.getResources();

        final int width;
        if (this == Maximum && layout == ScreenLayout.Grid) {
            if (res.getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
                // In landscape, half.
                width = getWindowWidthInPx(context) / 2;
            } else {
                // In portrait, the entire screen width
                width = getWindowWidthInPx(context);
            }
        } else {
            width = lookup(res, layout);
        }

        return new ImageViewSize(width, (int) (width / CoverScale.HW_RATIO));
    }

    /**
     * Use the available screen width and the scale to calculate the optimal
     * span-count for use by the BoB {@link ScreenLayout#Grid} mode.
     *
     * @param context Current context
     *
     * @return span count
     */
    @IntRange(from = 1)
    public int getGridSpanCount(@NonNull final Context context) {
        final Resources res = context.getResources();

        if (this == Hidden) {
            // we should never get here / return 0... flw
            return 1;
        }

        if (this == Maximum) {
            if (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                final ScreenSize screenSize = ScreenSize.compute(context);
                if (screenSize.getWidth() == ScreenSize.Value.Expanded) {
                    return 3;
                }
                return 2;
            } else {
                // Configuration.ORIENTATION_PORTRAIT
                return 1;
            }
        }

        final float coverWidthPx = lookup(res, ScreenLayout.Grid);
        return (int) Math.floor((float) getWindowWidthInPx(context) / coverWidthPx);
    }

    /*  Grid span count, multiplication of 0.6

    Pixel 8 Pro
        Resolution (px) 1344 x 2992
        Resolution (dp)  448 x 998
        Density 480 dpi

    Portrait:
    - Small:  5
    - Medium: 3
    - Large : 2
    - Max:    1

    Landscape
    - Small: 12
    - Medium: 8
    - Large:  6
    - Max:    3

    ===========================================
    Pixel 2
        Resolution (px) 1080 x 1920
        Resolution (dp) 412 x 732
        Density 420 dpi

    Portrait:
    - Small:  5
    - Medium: 3
    - Large : 2
    - Max:    1

    Landscape
    - Small:  9
    - Medium: 6
    - Large:  4
    - Max:    2

    ===========================================
    Small Phone
        Resolution (px) 720 x 1280
        Resolution (dp) 360 x 640
        Density 320 dpi

    Portrait:
    - Small:  4
    - Medium: 3
    - Large : 2
    - Max:    1

    Landscape
    - Small:  8
    - Medium: 5
    - Large:  4
    - Max:    2

    ===========================================
    WSVGA Tablet (7")
        Resolution (px) 1024 x 600
        Resolution (dp) 1024 x 600
        Density 160 dpi

    Portrait:
    - Small:  5
    - Medium: 3
    - Large : 2
    - Max:    1

    Landscape
    - Small:  8
    - Medium: 6
    - Large:  4
    - Max:    3

    ===========================================
    Medium Tablet (10")
        Resolution (px) 2560 x 1600
        Resolution (dp) 1280 x 800
        Density 320 dpi

    Portrait:
    - Small:  5
    - Medium: 4
    - Large : 3
    - Max:    1

    Landscape: n/a; embedded view is list-only

     */
}
