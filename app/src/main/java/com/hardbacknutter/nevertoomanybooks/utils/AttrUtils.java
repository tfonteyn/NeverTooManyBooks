/*
 * @Copyright 2020 HardBackNutter
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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public final class AttrUtils {

    private AttrUtils() {
    }

    /**
     * Get the resource id for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return resource ID
     */
    public static int getResId(@NonNull final Context context,
                               @AttrRes final int attr) {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        return tv.resourceId;
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    @ColorInt
    public static int getColorInt(@NonNull final Context context,
                                  @AttrRes final int attr) {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        // Calling this returns the tv.data
        //int c = MaterialColors.getColor(context, attr, "");
        // but calling this returns the color state list resolution
        return context.getResources().getColor(tv.resourceId, theme);
    }

    /**
     * Get the Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *                Must be a type that has a {@code android.R.attr.textSize} value.
     *
     * @return size in integer pixels, or {@code -1} if not defined.
     */
    public static int getTextSize(@NonNull final Context context,
                                  @AttrRes final int attr) {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        final int[] textSizeAttr = new int[]{android.R.attr.textSize};
        final int indexOfAttrTextSize = 0;
        final TypedArray ta = context.getTheme().obtainStyledAttributes(tv.data, textSizeAttr);
        try {
            return ta.getDimensionPixelSize(indexOfAttrTextSize, -1);
        } finally {
            ta.recycle();
        }
    }

    /**
     * Get a dimension (absolute) int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return size in integer pixels
     */
    public static int getDimen(@NonNull final Context context,
                               @AttrRes final int attr) {

        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        final DisplayMetrics metrics = new DisplayMetrics();
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        //noinspection ConstantConditions
        wm.getDefaultDisplay().getMetrics(metrics);

        return (int) tv.getDimension(metrics);
    }
}
