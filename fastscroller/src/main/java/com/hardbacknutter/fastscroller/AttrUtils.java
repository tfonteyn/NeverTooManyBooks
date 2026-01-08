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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public final class AttrUtils {

    private AttrUtils() {
    }

    /**
     * Get a colour int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single colour value in the form 0xAARRGGBB.
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    @ColorInt
    static int getColorInt(@NonNull final Context context,
                           @SuppressWarnings("SameParameterValue") @AttrRes final int attr)
            throws Resources.NotFoundException {
        final TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        try {
            if (a.hasValue(0)) {
                return a.getColor(0, 0);
            }
        } finally {
            a.recycle();
        }
        throw new Resources.NotFoundException(String.valueOf(attr));
    }

    /**
     * Get a Drawable for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A Drawable
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    @NonNull
    public static Drawable getDrawable(@NonNull final Context context,
                                       @AttrRes final int attr)
            throws Resources.NotFoundException {
        final TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        try {
            if (a.hasValue(0)) {
                final Drawable drawable = a.getDrawable(0);
                // Paranoia... should never happen when hasValue==true
                if (drawable != null) {
                    return drawable;
                }
            }
        } finally {
            a.recycle();
        }
        throw new Resources.NotFoundException(String.valueOf(attr));
    }
}
