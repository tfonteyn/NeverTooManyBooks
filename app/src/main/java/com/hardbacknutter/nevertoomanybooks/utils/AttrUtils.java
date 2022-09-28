/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.graphics.drawable.Drawable;

import androidx.annotation.AnyRes;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Why not use MaterialColors.getColor(context, attr) :
 * If the resource is a plain color, then this is the same as the code used above.
 * However, if the resource is a reference (using a string) then
 * - MaterialColors.getColor  DOES NOT RESOLVE THIS
 * - getResources().getColor will resolve it correctly.
 * <p>
 * example, for "R.attr.colorControlNormal" we get:
 * TypedValue{t=0x3/d=0x9f3 "res/color/text_color_secondary.xml" a=1 r=0x1060233}
 * - MaterialColors.getColor returns the data part: 0x9f3  (tv.data)
 * - getResources().getColor resolves it and return the correct color int.
 */
public final class AttrUtils {

    private static final String ERROR_FAILED_TO_RESOLVE_ATTRIBUTE = "Failed to resolve attribute ";

    private AttrUtils() {
    }

    /**
     * Get the resource id for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return resource ID
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    @AnyRes
    public static int getResId(@NonNull final Context context,
                               @AttrRes final int attr)
            throws Resources.NotFoundException {
        try (TypedArray a = context.obtainStyledAttributes(new int[]{attr})) {
            final int resId = a.getResourceId(0, 0);
            if (resId != 0) {
                return resId;
            }
        }
        throw new Resources.NotFoundException(ERROR_FAILED_TO_RESOLVE_ATTRIBUTE + attr);
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    @ColorInt
    public static int getColorInt(@NonNull final Context context,
                                  @AttrRes final int attr)
            throws Resources.NotFoundException {
        try (TypedArray a = context.obtainStyledAttributes(new int[]{attr})) {
            final int color = a.getColor(0, 0);
            if (color != 0) {
                return color;
            }
        }
        throw new Resources.NotFoundException(ERROR_FAILED_TO_RESOLVE_ATTRIBUTE + attr);
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
        try (TypedArray a = context.obtainStyledAttributes(new int[]{attr})) {
            final Drawable drawable = a.getDrawable(0);
            if (drawable != null) {
                return drawable;
            }
        }
        throw new Resources.NotFoundException(ERROR_FAILED_TO_RESOLVE_ATTRIBUTE + attr);
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
    public static int getDimensionPixelSize(@NonNull final Context context,
                                            @AttrRes final int attr)
            throws Resources.NotFoundException {
        try (TypedArray a = context.obtainStyledAttributes(new int[]{attr})) {
            final int dimension = a.getDimensionPixelSize(0, 0);
            if (dimension != 0) {
                return dimension;
            }
        }
        throw new Resources.NotFoundException(ERROR_FAILED_TO_RESOLVE_ATTRIBUTE + attr);
    }
}
