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

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class TileDrawable
        extends Drawable {
    private final DummyConstantState constantState = new DummyConstantState();
    private int alpha = 0xFF;
    @NonNull
    private Drawable drawable;
    private int tileCount = -1;
    @Nullable
    private ColorFilter colorFilter;
    @Nullable
    private ColorStateList tintList;
    @Nullable
    private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
    @Nullable
    private PorterDuffColorFilter tintFilter;

    TileDrawable(@NonNull final Drawable drawable) {
        this.drawable = drawable;
    }

    @NonNull
    Drawable getDrawable() {
        return drawable;
    }

    public int getTileCount() {
        return tileCount;
    }

    void setTileCount(final int tileCount) {
        this.tileCount = tileCount;
        invalidateSelf();
    }

    @NonNull
    @Override
    public Drawable mutate() {
        drawable = drawable.mutate();
        return this;
    }

    private void onDraw(@NonNull final Canvas canvas,
                        final int width,
                        final int height) {

        drawable.setAlpha(alpha);
        final ColorFilter cf = colorFilter != null ? colorFilter : tintFilter;
        if (cf != null) {
            drawable.setColorFilter(cf);
        }

        final int tileHeight = drawable.getIntrinsicHeight();
        final float scale = (float) height / tileHeight;
        canvas.scale(scale, scale);

        final float scaledWidth = width / scale;
        if (tileCount < 0) {
            final int tileWidth = drawable.getIntrinsicWidth();
            for (int x = 0; x < scaledWidth; x += tileWidth) {
                drawable.setBounds(x, 0, x + tileWidth, tileHeight);
                drawable.draw(canvas);
            }
        } else {
            final float tileWidth = scaledWidth / tileCount;
            for (int i = 0; i < tileCount; ++i) {
                final int drawableWidth = drawable.getIntrinsicWidth();
                final float tileCenter = tileWidth * (i + 0.5f);
                final float drawableWidthHalf = (float) drawableWidth / 2;
                drawable.setBounds(Math.round(tileCenter - drawableWidthHalf), 0,
                                   Math.round(tileCenter + drawableWidthHalf), tileHeight);
                drawable.draw(canvas);
            }
        }
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(final int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public void setTint(@ColorInt final int tintColor) {
        setTintList(ColorStateList.valueOf(tintColor));
    }

    @Override
    public void setTintList(@Nullable final ColorStateList tint) {
        tintList = tint;
        if (updateTintFilter()) {
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(@Nullable final PorterDuff.Mode tintMode) {
        this.tintMode = tintMode;
        if (updateTintFilter()) {
            invalidateSelf();
        }
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    @Override
    protected boolean onStateChange(@NonNull final int[] state) {
        return updateTintFilter();
    }

    private boolean updateTintFilter() {

        if (tintList == null || tintMode == null) {
            final boolean hadTintFilter = tintFilter != null;
            tintFilter = null;
            return hadTintFilter;
        }

        final int tintColor = tintList.getColorForState(getState(), Color.TRANSPARENT);
        // They made PorterDuffColorFilter.setColor() and setMode() @hide.
        tintFilter = new PorterDuffColorFilter(tintColor, tintMode);
        return true;
    }

    @Override
    public int getOpacity() {
        // Be safe.
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {

        final Rect bounds = getBounds();
        if (bounds.width() == 0 || bounds.height() == 0) {
            return;
        }

        final int saveCount = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        onDraw(canvas, bounds.width(), bounds.height());
        canvas.restoreToCount(saveCount);
    }

    @Override
    public ConstantState getConstantState() {
        return constantState;
    }

    private final class DummyConstantState
            extends ConstantState {

        @Override
        public int getChangingConfigurations() {
            return 0;
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return TileDrawable.this;
        }
    }
}
