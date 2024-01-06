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
import android.content.res.TypedArray;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Cover Scaling.
 * <strong>Never change the 'scale' values</strong>, they get stored in the db.
 * <p>
 * These values are used as the index into a resource array.
 *
 * @see com.hardbacknutter.nevertoomanybooks.R.array#cover_book_list_longest_side
 */
public enum CoverScale {
    Hidden(0),
    Small(1),
    Medium(2),
    Large(3),
    XL(4);

    public static final CoverScale DEFAULT = Medium;

    private final int scale;

    CoverScale(final int scale) {
        this.scale = scale;
    }

    public int getScale() {
        return scale;
    }

    @NonNull
    public static CoverScale byId(final int id) {
        if (id > XL.scale) {
            return XL;
        } else if (id < Hidden.scale) {
            return Hidden;
        } else {
            return Arrays.stream(values()).filter(v -> v.scale == id).findAny().orElse(DEFAULT);
        }
    }

    public CoverScale larger() {
        final int next = MathUtils.clamp(scale + 1, Small.scale, XL.scale);
        return values()[next];
    }

    public CoverScale smaller() {
        final int next = MathUtils.clamp(scale - 1, Small.scale, XL.scale);
        return values()[next];
    }

    /**
     * Calculate the scaled cover maximum width in pixels.
     *
     * @param context Current context
     *
     * @return max width in pixels
     */
    @Dimension
    public int getMaxWidthInPixels(@NonNull final Context context) {
        // The scale is used to retrieve the cover dimensions.
        // We use a square space for the image so both portrait/landscape images work out.
        final TypedArray coverSizes = context
                .getResources().obtainTypedArray(R.array.cover_book_list_longest_side);
        try {
            return coverSizes.getDimensionPixelSize(scale, 0);
        } finally {
            coverSizes.recycle();
        }
    }
}
