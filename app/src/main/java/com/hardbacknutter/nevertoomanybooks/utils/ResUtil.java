/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;

public final class ResUtil {
    private ResUtil() {
    }

    public static float getFloatDimension(@NonNull final Context context,
                                          @DimenRes final int dimenRes) {
        final TypedValue outValue = new TypedValue();
        context.getResources().getValue(dimenRes, outValue, true);
        return outValue.getFloat();
    }
}
