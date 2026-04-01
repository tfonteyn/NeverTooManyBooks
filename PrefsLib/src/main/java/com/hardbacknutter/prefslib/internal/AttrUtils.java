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
package com.hardbacknutter.prefslib.internal;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

final class AttrUtils {

    private AttrUtils() {
    }

    /**
     * Get a dimension (absolute) int value for the given attribute.
     *
     * @param context Current context; <strong>DO NOT USE THE APPLICATION CONTEXT</strong>
     * @param attr    attribute id to resolve
     *
     * @return size in integer pixels
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    static int getDimensionPixelSize(@NonNull final Context context,
                                     @AttrRes final int attr)
            throws Resources.NotFoundException {
        final TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        try {
            if (a.hasValue(0)) {
                return a.getDimensionPixelSize(0, 0);
            }
        } finally {
            a.recycle();
        }
        throw new Resources.NotFoundException(String.valueOf(attr));
    }
}
