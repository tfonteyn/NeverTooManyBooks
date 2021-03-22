/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import androidx.annotation.AnyRes;
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
    @AnyRes
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
     *
     * @throws Resources.NotFoundException if the requested Color does not exist.
     */
    @ColorInt
    public static int getColorInt(@NonNull final Context context,
                                  @AttrRes final int attr)
            throws Resources.NotFoundException {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        return context.getResources().getColor(tv.resourceId, theme);

        // Why not use MaterialColors.getColor(context, attr) :
        // If the resource is a plain color, then this is the same as the code used above.
        // However, if the resource is a reference (using a string) then
        // - MaterialColors.getColor  DOES NOT RESOLVE THIS
        // - getResources().getColor will resolve it correctly.
        //
        // example, for "R.attr.colorControlNormal" we get:
        //   TypedValue{t=0x3/d=0x9f3 "res/color/text_color_secondary.xml" a=1 r=0x1060233}
        // - MaterialColors.getColor returns the data part: 0x9f3  (tv.data)
        // - getResources().getColor resolves it and return the correct color int.
    }

    /**
     * Get a Drawable for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A Drawable
     *
     * @throws Resources.NotFoundException if the requested Drawable does not exist.
     */
    @SuppressWarnings("unused")
    @SuppressLint("UseCompatLoadingForDrawables")
    public static Drawable getDrawable(@NonNull final Context context,
                                       @AttrRes final int attr)
            throws Resources.NotFoundException {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        return context.getResources().getDrawable(tv.resourceId, theme);
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
     *
     * @throws Resources.NotFoundException if the requested text size does not exist.
     */
    @SuppressWarnings("unused")
    public static int getTextSize(@NonNull final Context context,
                                  @AttrRes final int attr)
            throws Resources.NotFoundException {
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
     * @param context Current context; <strong>DO NOT USE THE APPLICATION CONTEXT</strong>
     * @param attr    attribute id to resolve
     *
     * @return size in integer pixels
     */
    public static int getDimensionPixelSize(@NonNull final Context context,
                                            @AttrRes final int attr) {

        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        try {
            final DisplayMetrics metrics = new DisplayMetrics();
            if (Build.VERSION.SDK_INT >= 30) {
                //noinspection ConstantConditions
                context.getDisplay().getRealMetrics(metrics);
            } else {
                final WindowManager wm = (WindowManager)
                        context.getSystemService(Context.WINDOW_SERVICE);
                wm.getDefaultDisplay().getMetrics(metrics);
            }
            return (int) tv.getDimension(metrics);

        } catch (@NonNull final UnsupportedOperationException e) {
            // When running androidTest, we get:
            // java.lang.UnsupportedOperationException: Tried to obtain display from a Context
            // not associated with one. Only visual Contexts (such as Activity or one created
            // with Context#createWindowContext) or ones created with Context#createDisplayContext
            // are associated with displays. Other types of Contexts are typically related
            // to background entities and may return an arbitrary display.

            // fake a response
            return 48;
        }
    }
}
