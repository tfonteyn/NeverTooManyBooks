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

/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package com.hardbacknutter.zratingbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.Objects;

class MaterialRatingDrawable
        extends LayerDrawable {

    MaterialRatingDrawable(@NonNull final Context context,
                           final boolean fillBackgroundStars) {
        super(new Drawable[]{
                createBackground(context, fillBackgroundStars),
                createSecondaryProgress(context, fillBackgroundStars),
                createProgress(context)
        });

        setId(0, android.R.id.background);
        setId(1, android.R.id.secondaryProgress);
        setId(2, android.R.id.progress);
    }

    @NonNull
    private static Drawable createProgress(@NonNull final Context context) {
        return clippedLayerDrawableWithTintAttrRes(
                context, R.drawable.mrb_star_icon_black_36dp,
                android.R.attr.colorControlActivated);
    }

    @NonNull
    private static Drawable createSecondaryProgress(@NonNull final Context context,
                                                    final boolean fillBackgroundStars) {
        if (fillBackgroundStars) {
            return clippedLayerDrawableWithTintColor(
                    context, R.drawable.mrb_star_icon_black_36dp,
                    Color.TRANSPARENT);
        }
        return clippedLayerDrawableWithTintAttrRes(
                context, R.drawable.mrb_star_border_icon_black_36dp,
                android.R.attr.colorControlActivated);
    }

    @NonNull
    private static Drawable createBackground(@NonNull final Context context,
                                             final boolean fillBackgroundStars) {
        if (fillBackgroundStars) {
            return layerDrawableWithTintAttrRes(
                    context, R.drawable.mrb_star_icon_black_36dp,
                    android.R.attr.colorControlHighlight);
        }
        return layerDrawableWithTintAttrRes(
                context, R.drawable.mrb_star_border_icon_black_36dp,
                android.R.attr.colorControlNormal);
    }

    @NonNull
    private static Drawable layerDrawableWithTintColor(@NonNull final Context context,
                                                       @DrawableRes final int tileResId,
                                                       @ColorInt final int tintColor) {
        final TileDrawable drawable = new TileDrawable(
                Objects.requireNonNull(AppCompatResources.getDrawable(context, tileResId)));
        drawable.mutate();
        drawable.setTint(tintColor);
        return drawable;
    }

    @NonNull
    private static Drawable layerDrawableWithTintAttrRes(@NonNull final Context context,
                                                         @DrawableRes final int tileResId,
                                                         @AttrRes final int tintAttrId) {
        return layerDrawableWithTintColor(context, tileResId, getTintColor(context, tintAttrId));
    }

    @SuppressLint("RtlHardcoded")
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static Drawable clippedLayerDrawableWithTintColor(@NonNull final Context context,
                                                              @DrawableRes final int tileResId,
                                                              @ColorInt final int tintColor) {
        return new ClipDrawable(
                layerDrawableWithTintColor(context, tileResId, tintColor),
                Gravity.LEFT, ClipDrawable.HORIZONTAL);
    }

    @SuppressLint("RtlHardcoded")
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static Drawable clippedLayerDrawableWithTintAttrRes(@NonNull final Context context,
                                                                @DrawableRes final int tileResId,
                                                                @AttrRes final int tintAttrId) {
        return new ClipDrawable(
                layerDrawableWithTintAttrRes(context, tileResId, tintAttrId),
                Gravity.LEFT, ClipDrawable.HORIZONTAL);
    }

    private static int getTintColor(@NonNull final Context context,
                                    @AttrRes final int tintAttrId) {
        final TypedArray a = context.obtainStyledAttributes(new int[]{tintAttrId});
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    float getTileRatio() {
        final Drawable drawable = getTileDrawableByLayerId(android.R.id.progress).getDrawable();
        return (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
    }

    void setStarCount(final int count) {
        getTileDrawableByLayerId(android.R.id.background).setTileCount(count);
        getTileDrawableByLayerId(android.R.id.secondaryProgress).setTileCount(count);
        getTileDrawableByLayerId(android.R.id.progress).setTileCount(count);
    }

    @NonNull
    private TileDrawable getTileDrawableByLayerId(@IdRes final int id) {
        final Drawable layerDrawable = findDrawableByLayerId(id);
        if (id == android.R.id.background) {
            return (TileDrawable) layerDrawable;
        } else if (id == android.R.id.progress || id == android.R.id.secondaryProgress) {
            return (TileDrawable) Objects.requireNonNull(
                    ((DrawableWrapper) layerDrawable).getDrawable());
        }
        // Should never reach here.
        throw new RuntimeException("Invalid id");
    }
}
