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
import android.content.res.Resources;
import android.content.res.TypedArray;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;

public enum TextScale {
    VerySmall(0),
    Small(1),
    Medium(2),
    Large(3),
    XL(4);

    public static final TextScale DEFAULT = Medium;

    private final int scale;

    TextScale(final int scale) {
        this.scale = scale;
    }

    public int getScale() {
        return scale;
    }

    @NonNull
    public static TextScale byId(final int id) {
        if (id > XL.scale) {
            return XL;
        } else if (id < VerySmall.scale) {
            return VerySmall;
        } else {
            return Arrays.stream(values()).filter(v -> v.scale == id).findAny().orElse(DEFAULT);
        }
    }


    @Dimension(unit = Dimension.SP)
    public float getFontSizeInSp(@NonNull final Context context) {
        final Resources res = context.getResources();
        final TypedArray ta;
        ta = res.obtainTypedArray(R.array.bob_text_size_in_sp);
        try {
            final float size = ta.getFloat(scale, 0);
            if (BuildConfig.DEBUG /* always */) {
                if (size <= 0) {
                    throw new IllegalArgumentException("Font size");
                }
            }
            return size;
        } finally {
            ta.recycle();
        }
    }

    public float getPaddingFactor(@NonNull final Context context) {
        final Resources res = context.getResources();
        final TypedArray ta;
        ta = res.obtainTypedArray(R.array.bob_text_padding_in_percent);
        try {
            final float size = ta.getFloat(scale, 0);
            if (BuildConfig.DEBUG /* always */) {
                if (size <= 0) {
                    throw new IllegalArgumentException("Padding factor");
                }
            }
            return size;
        } finally {
            ta.recycle();
        }
    }
}
